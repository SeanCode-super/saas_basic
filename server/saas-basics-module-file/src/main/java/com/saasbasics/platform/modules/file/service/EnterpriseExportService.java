package com.saasbasics.platform.modules.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.modules.audit.api.AuditRecorder;
import com.saasbasics.platform.modules.file.dto.ExportDtos.ExportCreateRequest;
import com.saasbasics.platform.modules.file.dto.ExportDtos.ExportDatasetInfoDTO;
import com.saasbasics.platform.modules.file.dto.ExportDtos.ExportFieldSpec;
import com.saasbasics.platform.modules.file.dto.ExportDtos.ExportStatsDTO;
import com.saasbasics.platform.modules.file.dto.ExportDtos.ExportTaskDTO;
import com.saasbasics.platform.modules.file.dto.ExportDtos.ExportTemplateDTO;
import com.saasbasics.platform.modules.file.dto.ExportDtos.ExportTemplateSaveRequest;
import com.saasbasics.platform.modules.file.entity.ExportTaskEntity;
import com.saasbasics.platform.modules.file.entity.ExportTemplateEntity;
import com.saasbasics.platform.modules.file.mapper.ExportTaskMapper;
import com.saasbasics.platform.modules.file.mapper.ExportTemplateMapper;
import com.saasbasics.platform.modules.file.service.ExportRendererService.RenderOutput;
import com.saasbasics.platform.modules.file.storage.FileClient;
import com.saasbasics.platform.modules.file.storage.FileClientFactory;
import com.saasbasics.platform.websocket.PlatformWebSocketSessionManager;
import jakarta.annotation.PreDestroy;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enterprise Export Queue, Sync Streaming, Security Trace Watermarking, and Download Center Service.
 */
@Service
public class EnterpriseExportService {

    private static final Logger log = LoggerFactory.getLogger(EnterpriseExportService.class);
    private static final int SYNC_ROW_THRESHOLD = 5000;
    private static final int MAX_EXPORT_ROWS = 100_000;
    private static final DateTimeFormatter WATERMARK_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ExecutorService exportWorkers = Executors.newFixedThreadPool(4);

    private final ExportDatasetCatalogService datasetCatalogService;
    private final ExportRendererService rendererService;
    private final FileClientFactory fileClientFactory;
    private final ObjectProvider<ExportTaskMapper> taskMapperProvider;
    private final ObjectProvider<ExportTemplateMapper> templateMapperProvider;
    private final ObjectProvider<AuditRecorder> auditRecorderProvider;
    private final ObjectProvider<PlatformWebSocketSessionManager> wsManagerProvider;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public EnterpriseExportService(
            ExportDatasetCatalogService datasetCatalogService,
            ExportRendererService rendererService,
            FileClientFactory fileClientFactory,
            ObjectProvider<ExportTaskMapper> taskMapperProvider,
            ObjectProvider<ExportTemplateMapper> templateMapperProvider,
            ObjectProvider<AuditRecorder> auditRecorderProvider,
            ObjectProvider<PlatformWebSocketSessionManager> wsManagerProvider,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.datasetCatalogService = datasetCatalogService;
        this.rendererService = rendererService;
        this.fileClientFactory = fileClientFactory;
        this.taskMapperProvider = taskMapperProvider;
        this.templateMapperProvider = templateMapperProvider;
        this.auditRecorderProvider = auditRecorderProvider;
        this.wsManagerProvider = wsManagerProvider;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @PreDestroy
    public void shutdown() {
        exportWorkers.shutdown();
    }

    public List<ExportDatasetInfoDTO> listDatasets() {
        return datasetCatalogService.listDatasets(currentTenantId());
    }

    public record SyncExportPayload(
            byte[] bytes,
            String fileName,
            String contentType,
            String traceCode,
            String sha256,
            long rowCount
    ) {}

    public SyncExportPayload exportSync(ExportCreateRequest request) {
        AuthPrincipal principal = AuthContext.get();
        Long tenantId = currentTenantId();
        Long userId = currentUserId();
        String operatorName = currentUsername(principal);
        String format = normalizeFormat(request.exportFormat());
        boolean includeWatermark = request.includeWatermark() == null || request.includeWatermark();
        boolean desensitize = request.desensitizeEnabled() == null || request.desensitizeEnabled();

        String traceCode = generateTraceCode();
        String taskNo = "TASK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        String watermarkText = buildWatermarkText(operatorName, traceCode);
        String title = resolveTaskTitle(request);

        try {
            RenderedDatasetPayload data = loadAndRender(
                    tenantId, request, title, format, desensitize, includeWatermark, watermarkText, traceCode, operatorName);
            String fullName = sanitizeFilename(title) + "_" + traceCode + "." + data.output().fileExtension();
            String objectKey = storeExportFile(tenantId, fullName, data.output().bytes(), data.output().contentType());

            persistExportTaskRecord(tenantId, userId, operatorName, taskNo, title, request, format,
                    watermarkText, traceCode, desensitize, data.rowCount(), data.output().bytes().length,
                    objectKey, data.output().sha256(), "COMPLETED", null);
            recordExportAudit(request, title, format, data.rowCount(), traceCode, true);

            return new SyncExportPayload(
                    data.output().bytes(),
                    fullName,
                    data.output().contentType(),
                    traceCode,
                    data.output().sha256(),
                    data.rowCount()
            );
        } catch (BizException biz) {
            throw biz;
        } catch (Exception ex) {
            recordExportAudit(request, title, format, 0L, traceCode, false);
            throw new BizException("EXPORT_FAILED", "数据导出失败: " + ex.getMessage());
        }
    }

    public ExportTaskDTO submitAsyncExport(ExportCreateRequest request) {
        ExportTaskMapper mapper = taskMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("EXPORT_UNAVAILABLE", "导出持久化服务不可用");
        }
        AuthPrincipal principal = AuthContext.get();
        Long tenantId = currentTenantId();
        Long userId = currentUserId();
        String operatorName = currentUsername(principal);
        String format = normalizeFormat(request.exportFormat());
        boolean includeWatermark = request.includeWatermark() == null || request.includeWatermark();
        boolean desensitize = request.desensitizeEnabled() == null || request.desensitizeEnabled();

        String traceCode = generateTraceCode();
        String taskNo = "EXP-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.ROOT);
        String watermarkText = buildWatermarkText(operatorName, traceCode);
        String title = resolveTaskTitle(request);

        ExportTaskEntity entity = new ExportTaskEntity();
        entity.setTenantId(tenantId);
        entity.setTaskNo(taskNo);
        entity.setTaskName(title);
        entity.setSourceType(normalizeSourceType(request));
        entity.setSourceCode(request.sourceCode() != null && !request.sourceCode().isBlank() ? request.sourceCode() : "ad_hoc");
        entity.setExportFormat(format);
        entity.setColumnsJson(toJsonSafe(request.selectedFields()));
        entity.setQueryParamsJson(toJsonSafe(request.filters()));
        entity.setWatermarkText(includeWatermark ? watermarkText : "");
        entity.setTraceCode(traceCode);
        entity.setDesensitized(desensitize ? 1 : 0);
        entity.setTotalRows(0L);
        entity.setProcessedRows(0L);
        entity.setProgressPercent(5);
        entity.setFileSize(0L);
        entity.setStatus("PENDING");
        entity.setExpireAt(LocalDateTime.now().plusDays(7));
        entity.setOperatorUserId(userId);
        entity.setOperatorName(operatorName);
        entity.setCreatedBy(userId);
        entity.setUpdatedBy(userId);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setDeleted(0);
        mapper.insert(entity);

        Long taskId = entity.getId();
        String tenantCode = principal != null && principal.tenantCode() != null ? principal.tenantCode() : "platform";

        exportWorkers.submit(() -> runAsyncExportJob(
                taskId, tenantId, tenantCode, userId, operatorName, request, title,
                format, desensitize, includeWatermark, watermarkText, traceCode
        ));

        return toTaskDto(entity);
    }

    private void runAsyncExportJob(
            Long taskId, Long tenantId, String tenantCode, Long userId, String operatorName,
            ExportCreateRequest request, String title, String format, boolean desensitize,
            boolean includeWatermark, String watermarkText, String traceCode
    ) {
        ExportTaskMapper mapper = taskMapperProvider.getIfAvailable();
        if (mapper == null) {
            return;
        }
        try (var ignored = TenantAccessContextHolder.openTenant(tenantId, tenantCode, userId)) {
            ExportTaskEntity task = mapper.selectById(taskId);
            if (task == null) {
                return;
            }
            task.setStatus("PROCESSING");
            task.setProgressPercent(25);
            task.setUpdatedAt(LocalDateTime.now());
            mapper.updateById(task);

            RenderedDatasetPayload data = loadAndRender(
                    tenantId, request, title, format, desensitize, includeWatermark, watermarkText, traceCode, operatorName);

            task.setTotalRows(data.rowCount());
            task.setProcessedRows(data.rowCount());
            task.setProgressPercent(85);
            mapper.updateById(task);

            String fullName = sanitizeFilename(title) + "_" + traceCode + "." + data.output().fileExtension();
            String objectKey = storeExportFile(tenantId, fullName, data.output().bytes(), data.output().contentType());

            task.setObjectKey(objectKey);
            task.setFileSize((long) data.output().bytes().length);
            task.setFileSha256(data.output().sha256());
            task.setProgressPercent(100);
            task.setStatus("COMPLETED");
            task.setUpdatedAt(LocalDateTime.now());
            mapper.updateById(task);

            recordExportAudit(request, title, format, data.rowCount(), traceCode, true);
            notifyUserExportCompleted(tenantId, userId, title, taskId, data.rowCount(), traceCode);
        } catch (Exception ex) {
            log.error("[ExportWorker] Async export failed taskId={}", taskId, ex);
            try (var ignored = TenantAccessContextHolder.openTenant(tenantId, tenantCode, userId)) {
                ExportTaskEntity failed = mapper.selectById(taskId);
                if (failed != null) {
                    failed.setStatus("FAILED");
                    failed.setErrorMessage(ex.getMessage() != null && ex.getMessage().length() > 900
                            ? ex.getMessage().substring(0, 900) : ex.getMessage());
                    failed.setUpdatedAt(LocalDateTime.now());
                    mapper.updateById(failed);
                }
            } catch (Exception ignoredInner) {
            }
        }
    }

    public List<ExportTaskDTO> listTasks() {
        ExportTaskMapper mapper = taskMapperProvider.getIfAvailable();
        if (mapper == null) {
            return List.of();
        }
        Long tenantId = currentTenantId();
        return mapper.selectList(new LambdaQueryWrapper<ExportTaskEntity>()
                        .eq(ExportTaskEntity::getTenantId, tenantId)
                        .eq(ExportTaskEntity::getDeleted, 0)
                        .orderByDesc(ExportTaskEntity::getId)
                        .last("LIMIT 100"))
                .stream()
                .map(this::toTaskDto)
                .toList();
    }

    public ExportStatsDTO getStats() {
        List<ExportTaskDTO> tasks = listTasks();
        long completed = tasks.stream().filter(t -> "COMPLETED".equals(t.status())).count();
        long rowsSum = tasks.stream().mapToLong(ExportTaskDTO::totalRows).sum();
        long bytesSum = tasks.stream().mapToLong(ExportTaskDTO::fileSize).sum();
        long maskedCount = tasks.stream().filter(ExportTaskDTO::desensitized).count();
        long templatesCount = listTemplates(null).size();
        return new ExportStatsDTO(tasks.size(), completed, rowsSum, bytesSum, maskedCount, templatesCount);
    }

    public record DownloadedExportFile(byte[] bytes, String fileName, String contentType, String traceCode) {}

    public DownloadedExportFile downloadTaskFile(Long id) {
        ExportTaskMapper mapper = taskMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("EXPORT_UNAVAILABLE", "下载服务不可用");
        }
        Long tenantId = currentTenantId();
        ExportTaskEntity task = mapper.selectOne(new LambdaQueryWrapper<ExportTaskEntity>()
                .eq(ExportTaskEntity::getId, id)
                .eq(ExportTaskEntity::getTenantId, tenantId)
                .eq(ExportTaskEntity::getDeleted, 0));
        if (task == null || !"COMPLETED".equals(task.getStatus()) || task.getObjectKey() == null) {
            throw new BizException("EXPORT_FILE_NOT_READY", "导出文件不存在或尚未生成完成");
        }
        FileClient client = fileClientFactory.getDefaultClient();
        byte[] bytes = client.download(task.getObjectKey());
        String ext = switch (task.getExportFormat().toUpperCase(Locale.ROOT)) {
            case "CSV" -> "csv";
            case "HTML_REPORT", "REPORT", "PDF" -> "html";
            default -> "xlsx";
        };
        String contentType = switch (ext) {
            case "csv" -> "text/csv;charset=UTF-8";
            case "html" -> "text/html;charset=UTF-8";
            default -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        };
        String fileName = sanitizeFilename(task.getTaskName()) + "_" + task.getTraceCode() + "." + ext;

        AuditRecorder recorder = auditRecorderProvider.getIfAvailable();
        if (recorder != null) {
            try {
                recorder.record("file", "export_download", String.valueOf(id), "DOWNLOAD_EXPORT",
                        task.getTraceCode(), "fileName=" + fileName + ", bytes=" + bytes.length, true);
            } catch (Exception ignored) {
            }
        }
        return new DownloadedExportFile(bytes, fileName, contentType, task.getTraceCode());
    }

    @Transactional
    public void deleteTask(Long id) {
        ExportTaskMapper mapper = taskMapperProvider.getIfAvailable();
        if (mapper == null) {
            return;
        }
        Long tenantId = currentTenantId();
        ExportTaskEntity task = mapper.selectOne(new LambdaQueryWrapper<ExportTaskEntity>()
                .eq(ExportTaskEntity::getId, id)
                .eq(ExportTaskEntity::getTenantId, tenantId)
                .eq(ExportTaskEntity::getDeleted, 0));
        if (task == null) {
            throw new BizException("EXPORT_TASK_NOT_FOUND", "导出记录不存在");
        }
        if (task.getObjectKey() != null && !task.getObjectKey().isBlank()) {
            try {
                fileClientFactory.getDefaultClient().delete(task.getObjectKey());
            } catch (Exception ignored) {
            }
        }
        task.setDeleted(1);
        task.setDeletedAt(LocalDateTime.now());
        mapper.updateById(task);
    }

    public List<ExportTemplateDTO> listTemplates(String sourceCode) {
        ExportTemplateMapper mapper = templateMapperProvider.getIfAvailable();
        if (mapper == null) {
            return List.of();
        }
        Long tenantId = currentTenantId();
        LambdaQueryWrapper<ExportTemplateEntity> qw = new LambdaQueryWrapper<ExportTemplateEntity>()
                .eq(ExportTemplateEntity::getTenantId, tenantId)
                .eq(ExportTemplateEntity::getDeleted, 0);
        if (sourceCode != null && !sourceCode.isBlank()) {
            qw.eq(ExportTemplateEntity::getSourceCode, sourceCode.trim());
        }
        qw.orderByDesc(ExportTemplateEntity::getId);
        return mapper.selectList(qw).stream().map(this::toTemplateDto).toList();
    }

    @Transactional
    public ExportTemplateDTO saveTemplate(ExportTemplateSaveRequest req) {
        ExportTemplateMapper mapper = templateMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("TEMPLATE_UNAVAILABLE", "模板持久化不可用");
        }
        Long tenantId = currentTenantId();
        Long userId = currentUserId();
        ExportTemplateEntity entity = null;
        if (req.id() != null) {
            entity = mapper.selectOne(new LambdaQueryWrapper<ExportTemplateEntity>()
                    .eq(ExportTemplateEntity::getId, req.id())
                    .eq(ExportTemplateEntity::getTenantId, tenantId)
                    .eq(ExportTemplateEntity::getDeleted, 0));
        }
        if (entity == null) {
            entity = new ExportTemplateEntity();
            entity.setTenantId(tenantId);
            entity.setTemplateCode(req.templateCode() != null && !req.templateCode().isBlank()
                    ? req.templateCode().trim()
                    : "tpl_" + UUID.randomUUID().toString().substring(0, 8));
            entity.setCreatedBy(userId);
            entity.setCreatedAt(LocalDateTime.now());
            entity.setDeleted(0);
        }
        entity.setTemplateName(req.templateName().trim());
        entity.setSourceCode(req.sourceCode().trim());
        entity.setFieldsJson(toJsonSafe(req.fields() != null ? req.fields() : List.of()));
        entity.setDefaultFormat(normalizeFormat(req.defaultFormat()));
        entity.setIncludeWatermark(!Boolean.FALSE.equals(req.includeWatermark()) ? 1 : 0);
        entity.setDesensitizeEnabled(!Boolean.FALSE.equals(req.desensitizeEnabled()) ? 1 : 0);
        entity.setStatus("ENABLED");
        entity.setRemark(req.remark());
        entity.setUpdatedBy(userId);
        entity.setUpdatedAt(LocalDateTime.now());

        if (entity.getId() == null) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
        return toTemplateDto(entity);
    }

    @Transactional
    public void deleteTemplate(Long id) {
        ExportTemplateMapper mapper = templateMapperProvider.getIfAvailable();
        if (mapper == null) return;
        ExportTemplateEntity entity = mapper.selectOne(new LambdaQueryWrapper<ExportTemplateEntity>()
                .eq(ExportTemplateEntity::getId, id)
                .eq(ExportTemplateEntity::getTenantId, currentTenantId())
                .eq(ExportTemplateEntity::getDeleted, 0));
        if (entity != null) {
            entity.setDeleted(1);
            entity.setDeletedAt(LocalDateTime.now());
            mapper.updateById(entity);
        }
    }

    private record RenderedDatasetPayload(RenderOutput output, long rowCount) {}

    private RenderedDatasetPayload loadAndRender(
            Long tenantId,
            ExportCreateRequest request,
            String title,
            String format,
            boolean desensitize,
            boolean includeWatermark,
            String watermarkText,
            String traceCode,
            String operatorName
    ) throws Exception {
        List<ExportFieldSpec> columns;
        List<Map<String, Object>> rows;

        if ("AD_HOC".equalsIgnoreCase(request.sourceType())
                || (request.adHocRows() != null && !request.adHocRows().isEmpty())) {
            rows = request.adHocRows() != null ? request.adHocRows() : List.of();
            List<String> colNames = request.adHocColumns() != null && !request.adHocColumns().isEmpty()
                    ? request.adHocColumns()
                    : (rows.isEmpty() ? List.of() : new ArrayList<>(rows.get(0).keySet()));
            Map<String, String> labels = request.adHocColumnLabels() != null ? request.adHocColumnLabels() : Map.of();
            columns = colNames.stream()
                    .map(c -> new ExportFieldSpec(c, labels.getOrDefault(c, c), ExportDatasetCatalogService.isSensitiveField(c), "VARCHAR"))
                    .toList();
        } else {
            ExportDatasetInfoDTO dataset = datasetCatalogService.requireDataset(tenantId, request.sourceCode());
            columns = datasetCatalogService.resolveSelectedFieldSpecs(dataset, request.selectedFields());
            int cap = Boolean.TRUE.equals(request.async()) ? MAX_EXPORT_ROWS : SYNC_ROW_THRESHOLD;
            List<Map<String, Object>> collected = new ArrayList<>();
            datasetCatalogService.streamRows(tenantId, request.sourceCode(), request.selectedFields(),
                    request.filters(), cap, collected::add);
            rows = collected;
        }

        RenderOutput output = rendererService.render(
                title, format, columns, rows, request.insights(),
                desensitize, includeWatermark, watermarkText, traceCode, operatorName, tenantId
        );
        return new RenderedDatasetPayload(output, rows.size());
    }

    private void persistExportTaskRecord(
            Long tenantId, Long userId, String operatorName, String taskNo, String title,
            ExportCreateRequest request, String format, String watermarkText, String traceCode,
            boolean desensitize, long rowCount, long fileSize, String objectKey, String sha256,
            String status, String errorMsg
    ) {
        ExportTaskMapper mapper = taskMapperProvider.getIfAvailable();
        if (mapper == null) return;
        try {
            ExportTaskEntity entity = new ExportTaskEntity();
            entity.setTenantId(tenantId);
            entity.setTaskNo(taskNo);
            entity.setTaskName(title);
            entity.setSourceType(normalizeSourceType(request));
            entity.setSourceCode(request.sourceCode() != null && !request.sourceCode().isBlank() ? request.sourceCode() : "ad_hoc");
            entity.setExportFormat(format);
            entity.setColumnsJson(toJsonSafe(request.selectedFields()));
            entity.setQueryParamsJson(toJsonSafe(request.filters()));
            entity.setWatermarkText(watermarkText);
            entity.setTraceCode(traceCode);
            entity.setDesensitized(desensitize ? 1 : 0);
            entity.setTotalRows(rowCount);
            entity.setProcessedRows(rowCount);
            entity.setProgressPercent(100);
            entity.setFileSize(fileSize);
            entity.setObjectKey(objectKey);
            entity.setFileSha256(sha256);
            entity.setStatus(status);
            entity.setErrorMessage(errorMsg);
            entity.setExpireAt(LocalDateTime.now().plusDays(7));
            entity.setOperatorUserId(userId);
            entity.setOperatorName(operatorName);
            entity.setCreatedBy(userId);
            entity.setUpdatedBy(userId);
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
            entity.setDeleted(0);
            mapper.insert(entity);
        } catch (Exception ex) {
            log.warn("[Export] Task ledger insert skipped: {}", ex.getMessage());
        }
    }

    private String storeExportFile(Long tenantId, String fileName, byte[] bytes, String contentType) {
        String dateDir = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String objectKey = "exports/t" + tenantId + "/" + dateDir + "/" + fileName;
        fileClientFactory.getDefaultClient().upload(bytes, objectKey, contentType);
        return objectKey;
    }

    private void notifyUserExportCompleted(Long tenantId, Long userId, String title, Long taskId,
                                           long rowCount, String traceCode) {
        try {
            String msgTitle = "【导出完成】" + title;
            String msgContent = "您的异步导出任务已完成，共处理 " + rowCount + " 行数据，追溯编码 " + traceCode + "，可前往「下载中心」获取文件。";
            jdbcTemplate.update("""
                INSERT INTO system_notify_message
                  (tenant_id, user_id, category, title, content, read_status, biz_type, biz_id, created_at)
                VALUES (?, ?, 'SYSTEM', ?, ?, 0, 'EXPORT_TASK', ?, NOW())
            """, tenantId, userId, msgTitle, msgContent, String.valueOf(taskId));

            PlatformWebSocketSessionManager ws = wsManagerProvider.getIfAvailable();
            if (ws != null) {
                String json = objectMapper.writeValueAsString(Map.of(
                        "type", "NOTIFICATION_ARRIVED",
                        "message", Map.of("title", msgTitle, "content", msgContent, "bizType", "EXPORT_TASK", "bizId", String.valueOf(taskId)),
                        "timestamp", System.currentTimeMillis()
                ));
                ws.sendToUser(userId, json);
            }
        } catch (Exception ex) {
            log.debug("[ExportWorker] Notification skipped: {}", ex.getMessage());
        }
    }

    private void recordExportAudit(ExportCreateRequest req, String title, String format,
                                   long rowCount, String traceCode, boolean success) {
        AuditRecorder recorder = auditRecorderProvider.getIfAvailable();
        if (recorder != null) {
            try {
                recorder.record("file", "enterprise_export", traceCode, "EXPORT_DATA",
                        toJsonSafe(Map.of("title", title, "source", req.sourceCode() == null ? "ad_hoc" : req.sourceCode(), "format", format)),
                        "rows=" + rowCount + ", traceCode=" + traceCode, success);
            } catch (Exception ignored) {
            }
        }
    }

    private ExportTaskDTO toTaskDto(ExportTaskEntity e) {
        return new ExportTaskDTO(
                e.getId(),
                e.getTaskNo(),
                e.getTaskName(),
                e.getSourceType(),
                e.getSourceCode(),
                e.getExportFormat(),
                e.getTraceCode(),
                e.getWatermarkText(),
                e.getDesensitized() != null && e.getDesensitized() == 1,
                e.getTotalRows() != null ? e.getTotalRows() : 0L,
                e.getProcessedRows() != null ? e.getProcessedRows() : 0L,
                e.getProgressPercent() != null ? e.getProgressPercent() : 0,
                e.getFileSize() != null ? e.getFileSize() : 0L,
                e.getFileSha256(),
                e.getStatus(),
                e.getErrorMessage(),
                e.getExpireAt(),
                e.getOperatorName(),
                e.getCreatedAt()
        );
    }

    private ExportTemplateDTO toTemplateDto(ExportTemplateEntity e) {
        List<String> fields = List.of();
        if (e.getFieldsJson() != null && !e.getFieldsJson().isBlank()) {
            try {
                fields = objectMapper.readValue(e.getFieldsJson(), new TypeReference<>() {});
            } catch (Exception ignored) {
            }
        }
        return new ExportTemplateDTO(
                e.getId(),
                e.getTemplateCode(),
                e.getTemplateName(),
                e.getSourceCode(),
                fields,
                e.getDefaultFormat(),
                e.getIncludeWatermark() != null && e.getIncludeWatermark() == 1,
                e.getDesensitizeEnabled() != null && e.getDesensitizeEnabled() == 1,
                e.getRemark()
        );
    }

    private static String normalizeFormat(String fmt) {
        if (fmt == null || fmt.isBlank()) return "XLSX";
        String upper = fmt.trim().toUpperCase(Locale.ROOT);
        if (upper.equals("CSV") || upper.equals("HTML_REPORT") || upper.equals("REPORT") || upper.equals("PDF")) {
            return upper.equals("REPORT") || upper.equals("PDF") ? "HTML_REPORT" : upper;
        }
        return "XLSX";
    }

    private static String normalizeSourceType(ExportCreateRequest req) {
        if (req.sourceType() != null && !req.sourceType().isBlank()) {
            return req.sourceType().trim().toUpperCase(Locale.ROOT);
        }
        return (req.adHocRows() != null && !req.adHocRows().isEmpty()) ? "NL2SQL" : "DATASET";
    }

    private static String resolveTaskTitle(ExportCreateRequest req) {
        if (req.taskName() != null && !req.taskName().isBlank()) {
            return req.taskName().trim();
        }
        return req.sourceCode() != null && !req.sourceCode().isBlank() ? req.sourceCode() + "_导出" : "AI分析报告导出";
    }

    private static String generateTraceCode() {
        String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String rand = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
        return "EXP-" + datePart + "-" + rand;
    }

    private static String buildWatermarkText(String operator, String traceCode) {
        return operator + " · " + LocalDateTime.now().format(WATERMARK_TS) + " · " + traceCode;
    }

    private static String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) return "export";
        return name.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
    }

    private String toJsonSafe(Object obj) {
        if (obj == null) return "{}";
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Long currentTenantId() {
        AuthPrincipal p = AuthContext.get();
        return p != null && p.tenantId() != null ? p.tenantId() : 1L;
    }

    private Long currentUserId() {
        AuthPrincipal p = AuthContext.get();
        return p != null && p.userId() != null ? p.userId() : 1L;
    }

    private String currentUsername(AuthPrincipal p) {
        return p != null && p.username() != null ? p.username() : "system";
    }
}
