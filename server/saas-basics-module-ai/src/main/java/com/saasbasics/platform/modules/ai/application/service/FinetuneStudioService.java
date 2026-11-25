package com.saasbasics.platform.modules.ai.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.security.SsrfProtectionValidator;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.FinetuneDatasetDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.FinetuneDatasetSaveRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.FinetuneJobCreateRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.FinetuneJobDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.FinetuneSampleAddRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.FinetuneSampleDTO;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiCommandHistoryEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiFinetuneDatasetEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiFinetuneJobEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiFinetuneSampleEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.AiCommandHistoryMapper;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.AiFinetuneDatasetMapper;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.AiFinetuneJobMapper;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.AiFinetuneSampleMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enterprise AI Fine-Tuning Workbench Service:
 * manages training datasets, automatic PII desensitization, NL2SQL history harvesting,
 * quality approval, Alpaca/ShareGPT JSONL export, and LoRA training + A/B evaluation jobs.
 */
@Service
public class FinetuneStudioService {

    private static final Pattern MOBILE_RE = Pattern.compile("(1[3-9]\\d)\\d{4}(\\d{4})");
    private static final Pattern EMAIL_RE = Pattern.compile("([a-zA-Z0-9._%+-]{2})[a-zA-Z0-9._%+-]*@([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})");

    private final ObjectProvider<AiFinetuneDatasetMapper> datasetMapperProvider;
    private final ObjectProvider<AiFinetuneSampleMapper> sampleMapperProvider;
    private final ObjectProvider<AiFinetuneJobMapper> jobMapperProvider;
    private final ObjectProvider<AiCommandHistoryMapper> historyMapperProvider;
    private final ObjectMapper objectMapper;
    private volatile String trainingEndpoint = "";
    private volatile String trainingApiKey = "";
    private volatile boolean allowPrivateTrainingEndpoint = false;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Value("${saas-basics.ai.training.endpoint:}")
    public void setTrainingEndpoint(String trainingEndpoint) {
        this.trainingEndpoint = trainingEndpoint == null ? "" : trainingEndpoint.trim();
    }

    @Value("${saas-basics.ai.training.api-key:}")
    public void setTrainingApiKey(String trainingApiKey) {
        this.trainingApiKey = trainingApiKey == null ? "" : trainingApiKey.trim();
    }

    @Value("${saas-basics.ai.training.allow-private-for-dev:false}")
    public void setAllowPrivateTrainingEndpoint(boolean allowPrivateTrainingEndpoint) {
        this.allowPrivateTrainingEndpoint = allowPrivateTrainingEndpoint;
    }

    public FinetuneStudioService(
            ObjectProvider<AiFinetuneDatasetMapper> datasetMapperProvider,
            ObjectProvider<AiFinetuneSampleMapper> sampleMapperProvider,
            ObjectProvider<AiFinetuneJobMapper> jobMapperProvider,
            ObjectProvider<AiCommandHistoryMapper> historyMapperProvider,
            ObjectMapper objectMapper
    ) {
        this.datasetMapperProvider = datasetMapperProvider;
        this.sampleMapperProvider = sampleMapperProvider;
        this.jobMapperProvider = jobMapperProvider;
        this.historyMapperProvider = historyMapperProvider;
        this.objectMapper = objectMapper;
    }

    public List<FinetuneDatasetDTO> listDatasets() {
        AiFinetuneDatasetMapper mapper = datasetMapperProvider.getIfAvailable();
        if (mapper == null) return List.of();
        return mapper.selectList(new LambdaQueryWrapper<AiFinetuneDatasetEntity>()
                        .eq(AiFinetuneDatasetEntity::getTenantId, currentTenantId())
                        .eq(AiFinetuneDatasetEntity::getDeleted, 0)
                        .orderByDesc(AiFinetuneDatasetEntity::getId))
                .stream()
                .map(this::toDatasetDto)
                .toList();
    }

    @Transactional
    public FinetuneDatasetDTO saveDataset(FinetuneDatasetSaveRequest req) {
        AiFinetuneDatasetMapper mapper = datasetMapperProvider.getIfAvailable();
        if (mapper == null) throw new IllegalStateException("微调数据集服务不可用");
        if (req.datasetName() == null || req.datasetName().isBlank()) {
            throw new IllegalArgumentException("数据集名称不能为空");
        }
        Long tenantId = currentTenantId();
        Long userId = currentUserId();
        AiFinetuneDatasetEntity entity = req.id() != null ? mapper.selectOne(new LambdaQueryWrapper<AiFinetuneDatasetEntity>()
                .eq(AiFinetuneDatasetEntity::getId, req.id())
                .eq(AiFinetuneDatasetEntity::getTenantId, tenantId)
                .eq(AiFinetuneDatasetEntity::getDeleted, 0)) : null;

        if (entity == null) {
            entity = new AiFinetuneDatasetEntity();
            entity.setTenantId(tenantId);
            entity.setDatasetCode(req.datasetCode() != null && !req.datasetCode().isBlank()
                    ? req.datasetCode().trim() : "ds_" + UUID.randomUUID().toString().substring(0, 8));
            entity.setSampleCount(0);
            entity.setApprovedCount(0);
            entity.setStatus("READY");
            entity.setCreatedBy(userId);
            entity.setCreatedAt(LocalDateTime.now());
            entity.setDeleted(0);
        }
        entity.setDatasetName(req.datasetName().trim());
        entity.setTaskType(req.taskType() != null && !req.taskType().isBlank() ? req.taskType().trim().toUpperCase(Locale.ROOT) : "NL2SQL");
        entity.setFormatType(req.formatType() != null && !req.formatType().isBlank() ? req.formatType().trim().toUpperCase(Locale.ROOT) : "ALPACA");
        entity.setRemark(req.remark());
        entity.setUpdatedBy(userId);
        entity.setUpdatedAt(LocalDateTime.now());

        if (entity.getId() == null) mapper.insert(entity);
        else mapper.updateById(entity);
        return toDatasetDto(entity);
    }

    public List<FinetuneSampleDTO> listSamples(Long datasetId) {
        AiFinetuneSampleMapper mapper = sampleMapperProvider.getIfAvailable();
        if (mapper == null) return List.of();
        LambdaQueryWrapper<AiFinetuneSampleEntity> qw = new LambdaQueryWrapper<AiFinetuneSampleEntity>()
                .eq(AiFinetuneSampleEntity::getTenantId, currentTenantId())
                .eq(AiFinetuneSampleEntity::getDeleted, 0);
        if (datasetId != null) {
            qw.eq(AiFinetuneSampleEntity::getDatasetId, datasetId);
        }
        return mapper.selectList(qw.orderByDesc(AiFinetuneSampleEntity::getId).last("LIMIT 200"))
                .stream()
                .map(this::toSampleDto)
                .toList();
    }

    @Transactional
    public FinetuneSampleDTO addSample(FinetuneSampleAddRequest req) {
        AiFinetuneSampleMapper mapper = sampleMapperProvider.getIfAvailable();
        if (mapper == null) throw new IllegalStateException("样本服务不可用");
        if (req.datasetId() == null || req.instruction() == null || req.outputText() == null) {
            throw new IllegalArgumentException("数据集、指令与期望输出不能为空");
        }
        boolean mask = !Boolean.FALSE.equals(req.desensitize());
        Long tenantId = currentTenantId();
        Long userId = currentUserId();

        AiFinetuneSampleEntity entity = new AiFinetuneSampleEntity();
        entity.setTenantId(tenantId);
        entity.setDatasetId(req.datasetId());
        entity.setInstruction(mask ? sanitizePii(req.instruction().trim()) : req.instruction().trim());
        entity.setInputText(req.inputText() != null ? (mask ? sanitizePii(req.inputText().trim()) : req.inputText().trim()) : "");
        entity.setOutputText(mask ? sanitizePii(req.outputText().trim()) : req.outputText().trim());
        entity.setSourceType(req.sourceType() != null ? req.sourceType() : "MANUAL");
        entity.setQualityScore(BigDecimal.valueOf(0.92));
        entity.setReviewStatus("APPROVED");
        entity.setDesensitized(mask ? 1 : 0);
        entity.setCreatedBy(userId);
        entity.setUpdatedBy(userId);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setDeleted(0);
        mapper.insert(entity);

        recalcDatasetCounts(tenantId, req.datasetId());
        return toSampleDto(entity);
    }

    @Transactional
    public Map<String, Object> harvestFromNl2SqlHistory(Long datasetId, int limit) {
        AiCommandHistoryMapper histMapper = historyMapperProvider.getIfAvailable();
        AiFinetuneSampleMapper sampleMapper = sampleMapperProvider.getIfAvailable();
        if (histMapper == null || sampleMapper == null || datasetId == null) {
            return Map.of("imported", 0);
        }
        Long tenantId = currentTenantId();
        Long userId = currentUserId();
        List<AiCommandHistoryEntity> rows = histMapper.selectList(new LambdaQueryWrapper<AiCommandHistoryEntity>()
                .eq(AiCommandHistoryEntity::getTenantId, tenantId)
                .eq(AiCommandHistoryEntity::getDeleted, 0)
                .eq(AiCommandHistoryEntity::getIntentCode, "QUERY_DATA")
                .isNotNull(AiCommandHistoryEntity::getGeneratedSql)
                .orderByDesc(AiCommandHistoryEntity::getId)
                .last("LIMIT " + Math.min(Math.max(1, limit), 30)));

        int imported = 0;
        for (AiCommandHistoryEntity h : rows) {
            if (h.getGeneratedSql() == null || h.getGeneratedSql().isBlank()) continue;
            long exists = sampleMapper.selectCount(new LambdaQueryWrapper<AiFinetuneSampleEntity>()
                    .eq(AiFinetuneSampleEntity::getTenantId, tenantId)
                    .eq(AiFinetuneSampleEntity::getDatasetId, datasetId)
                    .eq(AiFinetuneSampleEntity::getInputText, sanitizePii(h.getRawCommand()))
                    .eq(AiFinetuneSampleEntity::getDeleted, 0));
            if (exists > 0) continue;

            AiFinetuneSampleEntity sample = new AiFinetuneSampleEntity();
            sample.setTenantId(tenantId);
            sample.setDatasetId(datasetId);
            sample.setInstruction("将企业数据分析提问转换为多租户安全 SELECT SQL（自动包含 tenant_id 与 deleted = 0 条件）");
            sample.setInputText(sanitizePii(h.getRawCommand()));
            sample.setOutputText(sanitizePii(h.getGeneratedSql()));
            sample.setSourceType("NL2SQL_HISTORY");
            sample.setQualityScore(BigDecimal.valueOf(0.90));
            sample.setReviewStatus("PENDING");
            sample.setDesensitized(1);
            sample.setCreatedBy(userId);
            sample.setUpdatedBy(userId);
            sample.setCreatedAt(LocalDateTime.now());
            sample.setUpdatedAt(LocalDateTime.now());
            sample.setDeleted(0);
            sampleMapper.insert(sample);
            imported++;
        }
        recalcDatasetCounts(tenantId, datasetId);
        return Map.of("imported", imported);
    }

    @Transactional
    public FinetuneSampleDTO reviewSample(Long sampleId, String status) {
        AiFinetuneSampleMapper mapper = sampleMapperProvider.getIfAvailable();
        if (mapper == null) throw new IllegalStateException("样本服务不可用");
        Long tenantId = currentTenantId();
        AiFinetuneSampleEntity sample = mapper.selectOne(new LambdaQueryWrapper<AiFinetuneSampleEntity>()
                .eq(AiFinetuneSampleEntity::getId, sampleId)
                .eq(AiFinetuneSampleEntity::getTenantId, tenantId)
                .eq(AiFinetuneSampleEntity::getDeleted, 0));
        if (sample == null) throw new IllegalArgumentException("训练样本不存在");
        String norm = "REJECTED".equalsIgnoreCase(status) ? "REJECTED" : "APPROVED";
        sample.setReviewStatus(norm);
        sample.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(sample);
        recalcDatasetCounts(tenantId, sample.getDatasetId());
        return toSampleDto(sample);
    }

    public byte[] exportJsonl(Long datasetId, String formatType) {
        AiFinetuneSampleMapper mapper = sampleMapperProvider.getIfAvailable();
        if (mapper == null) return new byte[0];
        Long tenantId = currentTenantId();
        List<AiFinetuneSampleEntity> samples = mapper.selectList(new LambdaQueryWrapper<AiFinetuneSampleEntity>()
                .eq(AiFinetuneSampleEntity::getTenantId, tenantId)
                .eq(AiFinetuneSampleEntity::getDatasetId, datasetId)
                .eq(AiFinetuneSampleEntity::getReviewStatus, "APPROVED")
                .eq(AiFinetuneSampleEntity::getDeleted, 0)
                .orderByAsc(AiFinetuneSampleEntity::getId));

        boolean shareGpt = "SHAREGPT".equalsIgnoreCase(formatType);
        StringBuilder sb = new StringBuilder();
        for (AiFinetuneSampleEntity s : samples) {
            try {
                if (shareGpt) {
                    String userTurn = s.getInstruction() + (s.getInputText() != null && !s.getInputText().isBlank()
                            ? "\n" + s.getInputText() : "");
                    Map<String, Object> row = Map.of("conversations", List.of(
                            Map.of("from", "human", "value", sanitizePii(userTurn)),
                            Map.of("from", "gpt", "value", sanitizePii(s.getOutputText()))
                    ));
                    sb.append(objectMapper.writeValueAsString(row)).append("\n");
                } else {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("instruction", sanitizePii(s.getInstruction()));
                    row.put("input", sanitizePii(s.getInputText() != null ? s.getInputText() : ""));
                    row.put("output", sanitizePii(s.getOutputText()));
                    sb.append(objectMapper.writeValueAsString(row)).append("\n");
                }
            } catch (Exception ignored) {
            }
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public List<FinetuneJobDTO> listJobs() {
        AiFinetuneJobMapper mapper = jobMapperProvider.getIfAvailable();
        if (mapper == null) return List.of();
        return mapper.selectList(new LambdaQueryWrapper<AiFinetuneJobEntity>()
                        .eq(AiFinetuneJobEntity::getTenantId, currentTenantId())
                        .eq(AiFinetuneJobEntity::getDeleted, 0)
                        .orderByDesc(AiFinetuneJobEntity::getId))
                .stream()
                .map(this::toJobDto)
                .toList();
    }

    @Transactional
    public FinetuneJobDTO startTrainingJob(FinetuneJobCreateRequest req) {
        if (trainingEndpoint == null || trainingEndpoint.isBlank()) {
            throw new BizException(
                    "AI_TRAINING_PROVIDER_UNAVAILABLE", "当前没有可执行训练任务的服务，请配置外部训练服务或导出已审核数据集");
        }
        URI endpointUri = SsrfProtectionValidator.validateOutboundUri(trainingEndpoint, allowPrivateTrainingEndpoint);
        AiFinetuneJobMapper jobMapper = jobMapperProvider.getIfAvailable();
        if (jobMapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "Fine-tuning job persistence requires the db profile");
        }
        if (req == null || req.datasetId() == null) {
            throw new BizException("INVALID_PARAM", "Dataset id is required");
        }

        byte[] jsonlBytes = exportJsonl(req.datasetId(), "ALPACA");
        if (jsonlBytes.length == 0) {
            throw new BizException("AI_DATASET_EMPTY", "Selected dataset has no approved samples to train on");
        }

        Long tenantId = currentTenantId();
        Long userId = currentUserId();
        String baseModel = (req.baseModel() != null && !req.baseModel().isBlank())
                ? req.baseModel().trim() : "Qwen2.5-7B-Instruct";
        int epochs = req.epochs() != null && req.epochs() > 0 ? req.epochs() : 3;
        String learningRate = (req.learningRate() != null && !req.learningRate().isBlank())
                ? req.learningRate().trim() : "2e-4";
        int loraRank = req.loraRank() != null && req.loraRank() > 0 ? req.loraRank() : 16;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", tenantId);
        payload.put("datasetId", req.datasetId());
        payload.put("baseModel", baseModel);
        payload.put("tuningMethod", "LORA");
        payload.put("epochs", epochs);
        payload.put("learningRate", learningRate);
        payload.put("loraRank", loraRank);
        payload.put("trainingJsonl", new String(jsonlBytes, StandardCharsets.UTF_8));

        Map<String, Object> remoteResp = invokeTrainerApi(endpointUri, "POST", payload);
        String remoteJobId = String.valueOf(remoteResp.getOrDefault("jobId",
                "ft-" + UUID.randomUUID().toString().substring(0, 10)));
        String status = String.valueOf(remoteResp.getOrDefault("status", "RUNNING")).toUpperCase(Locale.ROOT);

        AiFinetuneJobEntity job = new AiFinetuneJobEntity();
        job.setTenantId(tenantId);
        job.setJobCode(remoteJobId);
        job.setJobName((req.jobName() != null && !req.jobName().isBlank())
                ? req.jobName().trim() : "LoRA-" + baseModel + "-" + remoteJobId);
        job.setDatasetId(req.datasetId());
        job.setBaseModel(baseModel);
        job.setTuningMethod("LORA");
        job.setEpochs(epochs);
        job.setLearningRate(learningRate);
        job.setLoraRank(loraRank);
        job.setStatus(status);
        job.setProgressPercent(parseInt(remoteResp.get("progressPercent"), 5));
        job.setTrainLoss(BigDecimal.ZERO);
        job.setEvalAccuracy(BigDecimal.ZERO);
        job.setBaselineAccuracy(BigDecimal.ZERO);
        job.setMetricsJson(toJsonSafe(remoteResp));
        job.setCreatedBy(userId);
        job.setUpdatedBy(userId);
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        job.setDeleted(0);
        jobMapper.insert(job);
        return toJobDto(job);
    }

    @Transactional
    public FinetuneJobDTO syncTrainingJobStatus(Long jobId) {
        if (trainingEndpoint == null || trainingEndpoint.isBlank()) {
            throw new BizException("AI_TRAINING_PROVIDER_UNAVAILABLE", "未配置外部训练服务端点");
        }
        AiFinetuneJobMapper jobMapper = jobMapperProvider.getIfAvailable();
        if (jobMapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "Fine-tuning job persistence requires the db profile");
        }
        AiFinetuneJobEntity job = jobMapper.selectOne(new LambdaQueryWrapper<AiFinetuneJobEntity>()
                .eq(AiFinetuneJobEntity::getId, jobId)
                .eq(AiFinetuneJobEntity::getTenantId, currentTenantId())
                .eq(AiFinetuneJobEntity::getDeleted, 0)
                .last("LIMIT 1"));
        if (job == null) {
            throw new BizException("AI_TRAINING_JOB_NOT_FOUND", "微调训练任务不存在");
        }
        String base = trainingEndpoint.endsWith("/") ? trainingEndpoint.substring(0, trainingEndpoint.length() - 1) : trainingEndpoint;
        URI statusUri = SsrfProtectionValidator.validateOutboundUri(base + "/" + job.getJobCode(), allowPrivateTrainingEndpoint);
        Map<String, Object> remoteResp = invokeTrainerApi(statusUri, "GET", null);

        job.setStatus(String.valueOf(remoteResp.getOrDefault("status", job.getStatus())).toUpperCase(Locale.ROOT));
        job.setProgressPercent(parseInt(remoteResp.get("progressPercent"),
                job.getProgressPercent() != null ? job.getProgressPercent() : 0));
        if (remoteResp.containsKey("trainLoss")) {
            job.setTrainLoss(new BigDecimal(String.valueOf(remoteResp.get("trainLoss"))).setScale(4, RoundingMode.HALF_UP));
        }
        if (remoteResp.containsKey("evalAccuracy")) {
            job.setEvalAccuracy(new BigDecimal(String.valueOf(remoteResp.get("evalAccuracy"))).setScale(4, RoundingMode.HALF_UP));
        }
        if (remoteResp.containsKey("outputModelId")) {
            job.setOutputModelId(String.valueOf(remoteResp.get("outputModelId")));
        }
        job.setMetricsJson(toJsonSafe(remoteResp));
        job.setUpdatedAt(LocalDateTime.now());
        jobMapper.updateById(job);
        return toJobDto(job);
    }

    private Map<String, Object> invokeTrainerApi(URI uri, String method, Map<String, Object> body) {
        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json");
            if (trainingApiKey != null && !trainingApiKey.isBlank()) {
                reqBuilder.header("Authorization", "Bearer " + trainingApiKey);
            }
            if ("POST".equals(method)) {
                reqBuilder.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
            } else {
                reqBuilder.GET();
            }
            HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BizException("AI_TRAINING_REMOTE_ERROR",
                        "外部训练服务返回异常状态: " + response.statusCode());
            }
            if (response.body() == null || response.body().isBlank()) {
                return Map.of();
            }
            return objectMapper.readValue(response.body(), new TypeReference<>() {});
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException("AI_TRAINING_REMOTE_ERROR", "调用外部训练服务失败: " + ex.getMessage());
        }
    }

    private static int parseInt(Object val, int fallback) {
        if (val == null) return fallback;
        try {
            return Integer.parseInt(String.valueOf(val));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private void recalcDatasetCounts(Long tenantId, Long datasetId) {
        AiFinetuneDatasetMapper dsMapper = datasetMapperProvider.getIfAvailable();
        AiFinetuneSampleMapper sMapper = sampleMapperProvider.getIfAvailable();
        if (dsMapper == null || sMapper == null || datasetId == null) return;
        long total = sMapper.selectCount(new LambdaQueryWrapper<AiFinetuneSampleEntity>()
                .eq(AiFinetuneSampleEntity::getTenantId, tenantId)
                .eq(AiFinetuneSampleEntity::getDatasetId, datasetId)
                .eq(AiFinetuneSampleEntity::getDeleted, 0));
        long approved = sMapper.selectCount(new LambdaQueryWrapper<AiFinetuneSampleEntity>()
                .eq(AiFinetuneSampleEntity::getTenantId, tenantId)
                .eq(AiFinetuneSampleEntity::getDatasetId, datasetId)
                .eq(AiFinetuneSampleEntity::getReviewStatus, "APPROVED")
                .eq(AiFinetuneSampleEntity::getDeleted, 0));
        AiFinetuneDatasetEntity ds = dsMapper.selectById(datasetId);
        if (ds != null) {
            ds.setSampleCount((int) total);
            ds.setApprovedCount((int) approved);
            ds.setUpdatedAt(LocalDateTime.now());
            dsMapper.updateById(ds);
        }
    }

    private static String sanitizePii(String input) {
        if (input == null || input.isBlank()) return "";
        String masked = MOBILE_RE.matcher(input).replaceAll("$1****$2");
        return EMAIL_RE.matcher(masked).replaceAll("$1***@$2");
    }

    private FinetuneDatasetDTO toDatasetDto(AiFinetuneDatasetEntity e) {
        return new FinetuneDatasetDTO(
                e.getId(), e.getDatasetCode(), e.getDatasetName(), e.getTaskType(),
                e.getSampleCount() != null ? e.getSampleCount() : 0,
                e.getApprovedCount() != null ? e.getApprovedCount() : 0,
                e.getFormatType(), e.getStatus(), e.getRemark()
        );
    }

    private FinetuneSampleDTO toSampleDto(AiFinetuneSampleEntity e) {
        return new FinetuneSampleDTO(
                e.getId(), e.getDatasetId(), e.getInstruction(), e.getInputText(), e.getOutputText(),
                e.getSourceType(), e.getQualityScore() != null ? e.getQualityScore().doubleValue() : 0.9,
                e.getReviewStatus(), e.getDesensitized() != null && e.getDesensitized() == 1, e.getCreatedAt()
        );
    }

    private FinetuneJobDTO toJobDto(AiFinetuneJobEntity e) {
        Map<String, Object> metrics = Map.of();
        if (e.getMetricsJson() != null && !e.getMetricsJson().isBlank()) {
            try {
                metrics = objectMapper.readValue(e.getMetricsJson(), new TypeReference<>() {});
            } catch (Exception ignored) {
            }
        }
        return new FinetuneJobDTO(
                e.getId(), e.getJobCode(), e.getJobName(), e.getDatasetId(),
                e.getBaseModel(), e.getTuningMethod(),
                e.getEpochs() != null ? e.getEpochs() : 3,
                e.getLearningRate(),
                e.getLoraRank() != null ? e.getLoraRank() : 16,
                e.getStatus(),
                e.getProgressPercent() != null ? e.getProgressPercent() : 0,
                e.getTrainLoss() != null ? e.getTrainLoss().doubleValue() : 0.0,
                e.getEvalAccuracy() != null ? e.getEvalAccuracy().doubleValue() : 0.0,
                e.getBaselineAccuracy() != null ? e.getBaselineAccuracy().doubleValue() : 0.0,
                e.getOutputModelId(), metrics, e.getCreatedAt()
        );
    }

    private String toJsonSafe(Object obj) {
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
}
