package com.saasbasics.platform.modules.integration.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.modules.integration.ai.dto.AiModelConfigDTO;
import com.saasbasics.platform.modules.integration.ai.dto.AiModelConfigSaveRequest;
import com.saasbasics.platform.modules.integration.ai.dto.AiProviderDTO;
import com.saasbasics.platform.modules.integration.ai.dto.AiProviderSaveRequest;
import com.saasbasics.platform.modules.integration.ai.dto.AiTestConnectionRequest;
import com.saasbasics.platform.modules.integration.ai.dto.AiTestConnectionResult;
import com.saasbasics.platform.modules.integration.ai.entity.AiModelConfigEntity;
import com.saasbasics.platform.modules.integration.ai.entity.AiProviderEntity;
import com.saasbasics.platform.modules.integration.ai.mapper.AiModelConfigMapper;
import com.saasbasics.platform.modules.integration.ai.mapper.AiProviderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AiModelManageService {

    private static final Logger log = LoggerFactory.getLogger(AiModelManageService.class);

    private final AiProviderMapper providerMapper;
    private final AiModelConfigMapper modelConfigMapper;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AiModelManageService(AiProviderMapper providerMapper,
                                AiModelConfigMapper modelConfigMapper,
                                ObjectMapper objectMapper) {
        this.providerMapper = providerMapper;
        this.modelConfigMapper = modelConfigMapper;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .build();
    }

    public List<AiProviderDTO> listProviders() {
        List<AiProviderEntity> entities = providerMapper.selectList(
                new LambdaQueryWrapper<AiProviderEntity>()
                        .orderByAsc(AiProviderEntity::getSortNo)
                        .orderByAsc(AiProviderEntity::getId)
        );

        // Preload model counts
        Map<Long, Long> modelCounts = modelConfigMapper.selectList(new LambdaQueryWrapper<AiModelConfigEntity>())
                .stream()
                .collect(Collectors.groupingBy(AiModelConfigEntity::getProviderId, Collectors.counting()));

        return entities.stream().map(e -> {
            AiProviderDTO dto = new AiProviderDTO();
            BeanUtils.copyProperties(e, dto);
            dto.setApiKeyMasked(maskApiKey(e.getApiKey()));
            dto.setHasApiKey(e.getApiKey() != null && !e.getApiKey().isBlank());
            dto.setModelCount(modelCounts.getOrDefault(e.getId(), 0L).intValue());
            return dto;
        }).collect(Collectors.toList());
    }

    @Transactional
    public Long saveProvider(AiProviderSaveRequest req) {
        if (req.getProviderName() == null || req.getProviderName().isBlank()) {
            throw new IllegalArgumentException("服务商名称不能为空");
        }
        if (req.getBaseUrl() == null || req.getBaseUrl().isBlank()) {
            throw new IllegalArgumentException("Base URL 不能为空");
        }

        // If marked default, unset other defaults
        if (req.getIsDefault() != null && req.getIsDefault() == 1) {
            unsetDefaultProviders();
        }

        if (req.getId() == null) {
            AiProviderEntity entity = new AiProviderEntity();
            BeanUtils.copyProperties(req, entity);
            if (entity.getTenantId() == null) {
                entity.setTenantId(1L);
            }
            if (entity.getStatus() == null || entity.getStatus().isBlank()) {
                entity.setStatus("ENABLED");
            }
            if (entity.getIsDefault() == null) {
                entity.setIsDefault(0);
            }
            if (entity.getSortNo() == null) {
                entity.setSortNo(50);
            }
            providerMapper.insert(entity);
            return entity.getId();
        } else {
            AiProviderEntity existing = providerMapper.selectById(req.getId());
            if (existing == null) {
                throw new IllegalArgumentException("未找到待更新的服务商: " + req.getId());
            }
            existing.setProviderName(req.getProviderName());
            existing.setProviderCode(req.getProviderCode());
            existing.setProviderType(req.getProviderType() != null ? req.getProviderType() : "OPENAI_COMPATIBLE");
            existing.setBaseUrl(req.getBaseUrl().trim());

            // If user supplied new non-masked key, update it; otherwise preserve old key
            String newKey = req.getApiKey();
            if (newKey != null && !newKey.isBlank() && !newKey.contains("****")) {
                existing.setApiKey(newKey.trim());
            }

            if (req.getStatus() != null) existing.setStatus(req.getStatus());
            if (req.getIsDefault() != null) existing.setIsDefault(req.getIsDefault());
            if (req.getSortNo() != null) existing.setSortNo(req.getSortNo());
            if (req.getRemark() != null) existing.setRemark(req.getRemark());

            providerMapper.updateById(existing);
            return existing.getId();
        }
    }

    @Transactional
    public void deleteProvider(Long id) {
        AiProviderEntity existing = providerMapper.selectById(id);
        if (existing == null) return;
        providerMapper.deleteById(id);
        // Cascade soft delete attached models
        modelConfigMapper.delete(
                new LambdaQueryWrapper<AiModelConfigEntity>().eq(AiModelConfigEntity::getProviderId, id)
        );
    }

    public List<AiModelConfigDTO> listModels(Long providerId, String status) {
        LambdaQueryWrapper<AiModelConfigEntity> qw = new LambdaQueryWrapper<AiModelConfigEntity>()
                .orderByAsc(AiModelConfigEntity::getSortNo)
                .orderByAsc(AiModelConfigEntity::getId);
        if (providerId != null) {
            qw.eq(AiModelConfigEntity::getProviderId, providerId);
        }
        if (status != null && !status.isBlank()) {
            qw.eq(AiModelConfigEntity::getStatus, status);
        }

        List<AiModelConfigEntity> models = modelConfigMapper.selectList(qw);
        Map<Long, AiProviderEntity> providerMap = providerMapper.selectList(new LambdaQueryWrapper<AiProviderEntity>())
                .stream().collect(Collectors.toMap(AiProviderEntity::getId, p -> p));

        return models.stream().map(m -> {
            AiModelConfigDTO dto = new AiModelConfigDTO();
            BeanUtils.copyProperties(m, dto);
            AiProviderEntity prov = providerMap.get(m.getProviderId());
            if (prov != null) {
                dto.setProviderName(prov.getProviderName());
                dto.setProviderCode(prov.getProviderCode());
            }
            return dto;
        }).collect(Collectors.toList());
    }

    @Transactional
    public Long saveModel(AiModelConfigSaveRequest req) {
        if (req.getModelId() == null || req.getModelId().isBlank()) {
            throw new IllegalArgumentException("模型标识符 (model_id) 不能为空");
        }
        if (req.getModelName() == null || req.getModelName().isBlank()) {
            throw new IllegalArgumentException("模型展示名称不能为空");
        }
        if (req.getProviderId() == null) {
            throw new IllegalArgumentException("必须指定所属服务商 (providerId)");
        }

        if (req.getIsDefault() != null && req.getIsDefault() == 1) {
            unsetDefaultModels();
        }

        if (req.getId() == null) {
            AiModelConfigEntity entity = new AiModelConfigEntity();
            BeanUtils.copyProperties(req, entity);
            if (entity.getTenantId() == null) entity.setTenantId(1L);
            if (entity.getStatus() == null || entity.getStatus().isBlank()) entity.setStatus("ENABLED");
            if (entity.getIsDefault() == null) entity.setIsDefault(0);
            if (entity.getSortNo() == null) entity.setSortNo(50);
            modelConfigMapper.insert(entity);
            return entity.getId();
        } else {
            AiModelConfigEntity existing = modelConfigMapper.selectById(req.getId());
            if (existing == null) {
                throw new IllegalArgumentException("未找到待更新的模型: " + req.getId());
            }
            existing.setProviderId(req.getProviderId());
            existing.setModelId(req.getModelId().trim());
            existing.setModelName(req.getModelName().trim());
            if (req.getCategory() != null) existing.setCategory(req.getCategory());
            if (req.getContextWindow() != null) existing.setContextWindow(req.getContextWindow());
            if (req.getMaxTokens() != null) existing.setMaxTokens(req.getMaxTokens());
            if (req.getTemperature() != null) existing.setTemperature(req.getTemperature());
            if (req.getIsDefault() != null) existing.setIsDefault(req.getIsDefault());
            if (req.getStatus() != null) existing.setStatus(req.getStatus());
            if (req.getSortNo() != null) existing.setSortNo(req.getSortNo());

            modelConfigMapper.updateById(existing);
            return existing.getId();
        }
    }

    @Transactional
    public void deleteModel(Long id) {
        modelConfigMapper.deleteById(id);
    }

    public AiTestConnectionResult testConnection(AiTestConnectionRequest req) {
        long start = System.currentTimeMillis();
        String targetBaseUrl = req.getBaseUrl();
        String targetApiKey = req.getApiKey();
        String testModel = req.getTestModel();

        if (req.getProviderId() != null) {
            AiProviderEntity prov = providerMapper.selectById(req.getProviderId());
            if (prov != null) {
                if (targetBaseUrl == null || targetBaseUrl.isBlank()) {
                    targetBaseUrl = prov.getBaseUrl();
                }
                if (targetApiKey == null || targetApiKey.isBlank() || targetApiKey.contains("****")) {
                    targetApiKey = prov.getApiKey();
                }
            }
        }

        if (targetBaseUrl == null || targetBaseUrl.isBlank()) {
            return new AiTestConnectionResult(false, 0, "Base URL 为空，无法发起测试", null);
        }

        String normalizedUrl = targetBaseUrl.trim();
        while (normalizedUrl.endsWith("/")) {
            normalizedUrl = normalizedUrl.substring(0, normalizedUrl.length() - 1);
        }
        if (!normalizedUrl.endsWith("/v1") && !normalizedUrl.contains("/compatible-mode/v1") && !normalizedUrl.contains("deepseek.com")) {
            // Normalize standard OpenAI-compatible base URL if needed
        }

        String endpoint = normalizedUrl.endsWith("/chat/completions") ? normalizedUrl : (normalizedUrl + "/chat/completions");
        if (testModel == null || testModel.isBlank()) {
            testModel = "deepseek-chat";
        }

        try {
            Map<String, Object> bodyMap = new HashMap<>();
            bodyMap.put("model", testModel);
            bodyMap.put("messages", List.of(Map.of("role", "user", "content", "hi")));
            bodyMap.put("max_tokens", 8);
            bodyMap.put("stream", false);

            String requestJson = objectMapper.writeValueAsString(bodyMap);

            HttpRequest.Builder httpReqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson));

            if (targetApiKey != null && !targetApiKey.isBlank()) {
                httpReqBuilder.header("Authorization", "Bearer " + targetApiKey.trim());
            }

            HttpResponse<String> response = httpClient.send(httpReqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;

            int statusCode = response.statusCode();
            String responseBody = response.body();

            if (statusCode == 200) {
                String replySample = "握手成功";
                try {
                    JsonNode root = objectMapper.readTree(responseBody);
                    JsonNode contentNode = root.at("/choices/0/message/content");
                    if (!contentNode.isMissingNode() && !contentNode.asText().isBlank()) {
                        replySample = contentNode.asText().trim();
                    }
                } catch (Exception ignored) {}
                return new AiTestConnectionResult(true, latency, String.format("握手成功 (HTTP 200)，时延: %d ms", latency), replySample);
            } else if (statusCode == 401) {
                return new AiTestConnectionResult(false, latency, "认证失败 (HTTP 401)：API Key 无效或未提供", responseBody);
            } else if (statusCode == 404) {
                return new AiTestConnectionResult(false, latency, String.format("端点未找到 (HTTP 404)：请检查 Base URL 是否正确 [%s]", endpoint), responseBody);
            } else if (statusCode == 429) {
                return new AiTestConnectionResult(false, latency, "调用超限或配额不足 (HTTP 429)：账户余额或并发限制", responseBody);
            } else {
                return new AiTestConnectionResult(false, latency, String.format("服务端返回状态码: HTTP %d", statusCode), responseBody);
            }
        } catch (Exception ex) {
            long latency = System.currentTimeMillis() - start;
            log.warn("[AI Connection Test Failed]: {}", ex.getMessage());
            return new AiTestConnectionResult(false, latency, "连接失败: " + ex.getMessage(), null);
        }
    }

    private void unsetDefaultProviders() {
        List<AiProviderEntity> list = providerMapper.selectList(new LambdaQueryWrapper<AiProviderEntity>().eq(AiProviderEntity::getIsDefault, 1));
        for (AiProviderEntity p : list) {
            p.setIsDefault(0);
            providerMapper.updateById(p);
        }
    }

    private void unsetDefaultModels() {
        List<AiModelConfigEntity> list = modelConfigMapper.selectList(new LambdaQueryWrapper<AiModelConfigEntity>().eq(AiModelConfigEntity::getIsDefault, 1));
        for (AiModelConfigEntity m : list) {
            m.setIsDefault(0);
            modelConfigMapper.updateById(m);
        }
    }

    private String maskApiKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return "未配置";
        }
        String clean = rawKey.trim();
        if (clean.length() <= 8) {
            return "******";
        }
        String prefix = clean.startsWith("sk-") ? "sk-" : clean.substring(0, 3);
        String suffix = clean.substring(clean.length() - 4);
        return prefix + "****" + suffix;
    }
}
