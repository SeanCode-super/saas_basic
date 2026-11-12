package com.saasbasics.platform.modules.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.tenant.TenantAccessContext;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.modules.file.dto.ExportDtos.ExportCreateRequest;
import com.saasbasics.platform.modules.file.dto.ExportDtos.ExportTaskDTO;
import com.saasbasics.platform.modules.file.dto.ExportDtos.ReportScheduleDTO;
import com.saasbasics.platform.modules.file.dto.ExportDtos.ReportScheduleSaveRequest;
import com.saasbasics.platform.modules.file.entity.ReportScheduleEntity;
import com.saasbasics.platform.modules.file.mapper.ReportScheduleMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * L2 Scheduled Enterprise Report Automation Service:
 * manages recurring DAILY / WEEKLY / MONTHLY export schedules and delivers outputs to Download Center.
 */
@Service
public class ReportScheduleService {

    private static final Logger log = LoggerFactory.getLogger(ReportScheduleService.class);

    private final ObjectProvider<ReportScheduleMapper> scheduleMapperProvider;
    private final EnterpriseExportService exportService;
    private final ObjectMapper objectMapper;

    public ReportScheduleService(
            ObjectProvider<ReportScheduleMapper> scheduleMapperProvider,
            EnterpriseExportService exportService,
            ObjectMapper objectMapper
    ) {
        this.scheduleMapperProvider = scheduleMapperProvider;
        this.exportService = exportService;
        this.objectMapper = objectMapper;
    }

    public List<ReportScheduleDTO> listSchedules() {
        ReportScheduleMapper mapper = scheduleMapperProvider.getIfAvailable();
        if (mapper == null) {
            return List.of();
        }
        Long tenantId = currentTenantId();
        return mapper.selectList(new LambdaQueryWrapper<ReportScheduleEntity>()
                        .eq(ReportScheduleEntity::getTenantId, tenantId)
                        .eq(ReportScheduleEntity::getDeleted, 0)
                        .orderByDesc(ReportScheduleEntity::getId))
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public ReportScheduleDTO saveSchedule(ReportScheduleSaveRequest req) {
        ReportScheduleMapper mapper = scheduleMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("SCHEDULE_UNAVAILABLE", "定时报表持久化不可用");
        }
        if (req.scheduleName() == null || req.scheduleName().isBlank() || req.sourceCode() == null || req.sourceCode().isBlank()) {
            throw new BizException("SCHEDULE_INVALID", "报表名称和数据源不能为空");
        }
        Long tenantId = currentTenantId();
        Long userId = currentUserId();
        ReportScheduleEntity entity = null;
        if (req.id() != null) {
            entity = mapper.selectOne(new LambdaQueryWrapper<ReportScheduleEntity>()
                    .eq(ReportScheduleEntity::getId, req.id())
                    .eq(ReportScheduleEntity::getTenantId, tenantId)
                    .eq(ReportScheduleEntity::getDeleted, 0));
        }
        if (entity == null) {
            entity = new ReportScheduleEntity();
            entity.setTenantId(tenantId);
            entity.setScheduleCode(req.scheduleCode() != null && !req.scheduleCode().isBlank()
                    ? req.scheduleCode().trim()
                    : "rpt_" + UUID.randomUUID().toString().substring(0, 8));
            entity.setCreatedBy(userId);
            entity.setCreatedAt(LocalDateTime.now());
            entity.setDeleted(0);
        }

        String freq = normalizeFrequency(req.frequency());
        int hour = req.runHour() != null ? Math.min(23, Math.max(0, req.runHour())) : 8;
        int minute = req.runMinute() != null ? Math.min(59, Math.max(0, req.runMinute())) : 0;

        entity.setScheduleName(req.scheduleName().trim());
        entity.setSourceCode(req.sourceCode().trim());
        entity.setExportFormat(req.exportFormat() != null ? req.exportFormat().trim().toUpperCase(Locale.ROOT) : "PDF");
        entity.setFieldsJson(toJsonSafe(req.fields() != null ? req.fields() : List.of()));
        entity.setFrequency(freq);
        entity.setRunHour(hour);
        entity.setRunMinute(minute);
        entity.setCronExpr(buildCron(freq, hour, minute));
        entity.setIncludeWatermark(!Boolean.FALSE.equals(req.includeWatermark()) ? 1 : 0);
        entity.setDesensitizeEnabled(!Boolean.FALSE.equals(req.desensitizeEnabled()) ? 1 : 0);
        entity.setRecipientUserIds(req.recipientUserIds() != null && !req.recipientUserIds().isBlank()
                ? req.recipientUserIds().trim() : String.valueOf(userId));
        entity.setStatus(req.status() != null && !req.status().isBlank() ? req.status().trim().toUpperCase(Locale.ROOT) : "ENABLED");
        entity.setRemark(req.remark());
        entity.setNextRunAt(computeNextRun(freq, hour, minute));
        entity.setUpdatedBy(userId);
        entity.setUpdatedAt(LocalDateTime.now());

        if (entity.getId() == null) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
        return toDto(entity);
    }

    @Transactional
    public void deleteSchedule(Long id) {
        ReportScheduleMapper mapper = scheduleMapperProvider.getIfAvailable();
        if (mapper == null) return;
        ReportScheduleEntity entity = mapper.selectOne(new LambdaQueryWrapper<ReportScheduleEntity>()
                .eq(ReportScheduleEntity::getId, id)
                .eq(ReportScheduleEntity::getTenantId, currentTenantId())
                .eq(ReportScheduleEntity::getDeleted, 0));
        if (entity != null) {
            entity.setDeleted(1);
            entity.setDeletedAt(LocalDateTime.now());
            mapper.updateById(entity);
        }
    }

    @Transactional
    public ExportTaskDTO triggerNow(Long id) {
        ReportScheduleMapper mapper = scheduleMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("SCHEDULE_UNAVAILABLE", "定时报表服务不可用");
        }
        Long tenantId = currentTenantId();
        ReportScheduleEntity schedule = mapper.selectOne(new LambdaQueryWrapper<ReportScheduleEntity>()
                .eq(ReportScheduleEntity::getId, id)
                .eq(ReportScheduleEntity::getTenantId, tenantId)
                .eq(ReportScheduleEntity::getDeleted, 0));
        if (schedule == null) {
            throw new BizException("SCHEDULE_NOT_FOUND", "定时报表计划不存在");
        }
        return executeScheduleInternal(mapper, schedule);
    }

    @Scheduled(fixedDelay = 60_000L, initialDelay = 45_000L)
    public void pollDueSchedules() {
        ReportScheduleMapper mapper = scheduleMapperProvider.getIfAvailable();
        if (mapper == null) {
            return;
        }
        try (var ignored = TenantAccessContextHolder.openSystemBypass(
                TenantAccessContext.BypassOperation.SYSTEM_TASK_OR_ANNOTATION_IGNORE,
                "scheduled-report-poll")) {
            List<ReportScheduleEntity> due = mapper.selectList(new LambdaQueryWrapper<ReportScheduleEntity>()
                    .eq(ReportScheduleEntity::getStatus, "ENABLED")
                    .eq(ReportScheduleEntity::getDeleted, 0)
                    .le(ReportScheduleEntity::getNextRunAt, LocalDateTime.now())
                    .last("LIMIT 10"));
            for (ReportScheduleEntity sched : due) {
                try (var tenantScope = TenantAccessContextHolder.openTenant(
                        sched.getTenantId(), "platform", sched.getCreatedBy() != null ? sched.getCreatedBy() : 1L)) {
                    executeScheduleInternal(mapper, sched);
                } catch (Exception ex) {
                    log.warn("[ReportSchedule] Scheduled execution failed id={}: {}", sched.getId(), ex.getMessage());
                }
            }
        } catch (Exception ignored) {
        }
    }

    private ExportTaskDTO executeScheduleInternal(ReportScheduleMapper mapper, ReportScheduleEntity sched) {
        List<String> fields = List.of();
        if (sched.getFieldsJson() != null && !sched.getFieldsJson().isBlank()) {
            try {
                fields = objectMapper.readValue(sched.getFieldsJson(), new TypeReference<>() {});
            } catch (Exception ignored) {
            }
        }
        List<Long> recipientIds = parseRecipientIds(sched.getRecipientUserIds());
        Long ownerId = recipientIds.isEmpty() ? 1L : recipientIds.get(0);

        ExportCreateRequest req = new ExportCreateRequest(
                "[定时报表] " + sched.getScheduleName(),
                "DATASET",
                sched.getSourceCode(),
                sched.getExportFormat(),
                fields,
                Map.of(),
                null, null, null,
                List.of("本报表由天枢定时报表引擎（" + sched.getFrequency() + "）自动生成并已加注防伪追溯水印。"),
                sched.getIncludeWatermark() == null || sched.getIncludeWatermark() == 1,
                sched.getDesensitizeEnabled() == null || sched.getDesensitizeEnabled() == 1,
                true,
                false
        );

        ExportTaskDTO task = exportService.submitScheduledDatasetExport(
                sched.getTenantId(), "platform", ownerId, "scheduler", req, recipientIds);

        sched.setLastRunAt(LocalDateTime.now());
        sched.setNextRunAt(computeNextRun(sched.getFrequency(),
                sched.getRunHour() != null ? sched.getRunHour() : 8,
                sched.getRunMinute() != null ? sched.getRunMinute() : 0));
        sched.setLastTaskId(task.id());
        sched.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(sched);
        return task;
    }

    private ReportScheduleDTO toDto(ReportScheduleEntity e) {
        List<String> fields = List.of();
        if (e.getFieldsJson() != null && !e.getFieldsJson().isBlank()) {
            try {
                fields = objectMapper.readValue(e.getFieldsJson(), new TypeReference<>() {});
            } catch (Exception ignored) {
            }
        }
        return new ReportScheduleDTO(
                e.getId(),
                e.getScheduleCode(),
                e.getScheduleName(),
                e.getSourceCode(),
                e.getExportFormat(),
                fields,
                e.getFrequency(),
                e.getCronExpr(),
                e.getRunHour() != null ? e.getRunHour() : 8,
                e.getRunMinute() != null ? e.getRunMinute() : 0,
                e.getIncludeWatermark() != null && e.getIncludeWatermark() == 1,
                e.getDesensitizeEnabled() != null && e.getDesensitizeEnabled() == 1,
                e.getRecipientUserIds(),
                e.getLastRunAt(),
                e.getNextRunAt(),
                e.getLastTaskId(),
                e.getStatus(),
                e.getRemark()
        );
    }

    private static List<Long> parseRecipientIds(String raw) {
        List<Long> ids = new ArrayList<>();
        if (raw != null) {
            for (String p : raw.split(",")) {
                try {
                    long v = Long.parseLong(p.trim());
                    if (v > 0) ids.add(v);
                } catch (Exception ignored) {
                }
            }
        }
        return ids.isEmpty() ? List.of(1L) : ids;
    }

    private static String normalizeFrequency(String freq) {
        if (freq == null) return "WEEKLY";
        String up = freq.trim().toUpperCase(Locale.ROOT);
        if (up.equals("DAILY") || up.equals("MONTHLY") || up.equals("WEEKLY")) {
            return up;
        }
        return "WEEKLY";
    }

    private static String buildCron(String freq, int hour, int minute) {
        return switch (freq) {
            case "DAILY" -> "0 " + minute + " " + hour + " * * ?";
            case "MONTHLY" -> "0 " + minute + " " + hour + " 1 * ?";
            default -> "0 " + minute + " " + hour + " ? * MON";
        };
    }

    private static LocalDateTime computeNextRun(String freq, int hour, int minute) {
        LocalDateTime now = LocalDateTime.now();
        return switch (freq) {
            case "DAILY" -> now.plusDays(1).withHour(hour).withMinute(minute).withSecond(0);
            case "MONTHLY" -> now.plusMonths(1).withDayOfMonth(1).withHour(hour).withMinute(minute).withSecond(0);
            default -> now.plusDays(7).withHour(hour).withMinute(minute).withSecond(0);
        };
    }

    private String toJsonSafe(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
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
}
