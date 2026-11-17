package com.saasbasics.platform.modules.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.audit.dto.AuditOverviewResponse;
import com.saasbasics.platform.modules.audit.dto.LoginLogResponse;
import com.saasbasics.platform.modules.audit.dto.OperationLogDiffResponse;
import com.saasbasics.platform.modules.audit.dto.OperationLogDiffResponse.FieldDiffItem;
import com.saasbasics.platform.modules.audit.dto.OperationLogResponse;
import com.saasbasics.platform.modules.audit.entity.LoginLogEntity;
import com.saasbasics.platform.modules.audit.entity.OperationLogEntity;
import com.saasbasics.platform.modules.audit.entity.RiskRuleEntity;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import com.saasbasics.platform.modules.audit.mapper.LoginLogMapper;
import com.saasbasics.platform.modules.audit.mapper.OperationLogMapper;
import com.saasbasics.platform.modules.audit.mapper.RiskRuleMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final ObjectProvider<LoginLogMapper> loginLogMapperProvider;
    private final ObjectProvider<OperationLogMapper> operationLogMapperProvider;
    private final ObjectProvider<RiskRuleMapper> riskRuleMapperProvider;

    public AuditService(ObjectProvider<LoginLogMapper> loginLogMapperProvider,
                        ObjectProvider<OperationLogMapper> operationLogMapperProvider,
                        ObjectProvider<RiskRuleMapper> riskRuleMapperProvider) {
        this.loginLogMapperProvider = loginLogMapperProvider;
        this.operationLogMapperProvider = operationLogMapperProvider;
        this.riskRuleMapperProvider = riskRuleMapperProvider;
    }

    public AuditOverviewResponse overview() {
        LoginLogMapper loginLogMapper = required(loginLogMapperProvider, "登录审计需要数据库连接");
        OperationLogMapper operationLogMapper = required(operationLogMapperProvider, "操作审计需要数据库连接");
        RiskRuleMapper riskRuleMapper = required(riskRuleMapperProvider, "风险规则需要数据库连接");

        long loginCount = loginLogMapper.selectCount(new LambdaQueryWrapper<LoginLogEntity>());
        long operationCount = operationLogMapper.selectCount(new LambdaQueryWrapper<OperationLogEntity>());
        long riskRuleCount = riskRuleMapper.selectCount(new LambdaQueryWrapper<RiskRuleEntity>().eq(RiskRuleEntity::getDeleted, 0));
        return new AuditOverviewResponse(loginCount, operationCount, 0L, riskRuleCount);
    }

    public List<LoginLogResponse> listLoginLogs(String username, Boolean success) {
        LoginLogMapper mapper = required(loginLogMapperProvider, "登录审计需要数据库连接");
        LambdaQueryWrapper<LoginLogEntity> wrapper = new LambdaQueryWrapper<LoginLogEntity>()
                .orderByDesc(LoginLogEntity::getOccurredAt)
                .last("LIMIT 100");
        if (username != null && !username.isBlank()) {
            wrapper.like(LoginLogEntity::getUsername, username.trim());
        }
        if (success != null) {
            wrapper.eq(LoginLogEntity::getSuccess, success);
        }
        return mapper.selectList(wrapper)
                .stream()
                .map(item -> new LoginLogResponse(
                        item.getId(),
                        item.getTenantId(),
                        item.getUserId(),
                        item.getUsername(),
                        item.getLoginType(),
                        item.getLoginIp(),
                        item.getSuccess(),
                        item.getFailReason(),
                        item.getOccurredAt()
                ))
                .toList();
    }

    public List<OperationLogResponse> listOperationLogs(String bizModule, String operationType) {
        OperationLogMapper mapper = required(operationLogMapperProvider, "操作审计需要数据库连接");
        LambdaQueryWrapper<OperationLogEntity> wrapper = new LambdaQueryWrapper<OperationLogEntity>()
                .orderByDesc(OperationLogEntity::getOccurredAt)
                .last("LIMIT 100");
        if (bizModule != null && !bizModule.isBlank()) {
            wrapper.eq(OperationLogEntity::getBizModule, bizModule.trim());
        }
        if (operationType != null && !operationType.isBlank()) {
            wrapper.eq(OperationLogEntity::getOperationType, operationType.trim());
        }
        return mapper.selectList(wrapper)
                .stream()
                .map(item -> new OperationLogResponse(
                        item.getId(),
                        item.getTenantId(),
                        item.getOperatorUserId(),
                        item.getOperatorName(),
                        item.getBizModule(),
                        item.getBizType(),
                        item.getBizId(),
                        item.getOperationType(),
                        item.getSuccess(),
                        item.getOccurredAt()
                ))
                .toList();
    }

    public OperationLogDiffResponse getOperationLogDiff(Long id) {
        OperationLogMapper mapper = required(operationLogMapperProvider, "操作审计需要数据库连接");
        OperationLogEntity entity = mapper.selectById(id);
        if (entity == null) {
            throw new BizException("AUDIT_LOG_NOT_FOUND", "操作审计日志不存在: " + id);
        }
        String beforeRaw = (entity.getBeforeJson() != null && !entity.getBeforeJson().isBlank())
                ? entity.getBeforeJson()
                : entity.getRequestBody();
        String afterRaw = (entity.getAfterJson() != null && !entity.getAfterJson().isBlank())
                ? entity.getAfterJson()
                : entity.getResponseBody();

        List<FieldDiffItem> diffs = computeJsonDiff(beforeRaw, afterRaw);
        return new OperationLogDiffResponse(
                entity.getId(),
                entity.getBizModule(),
                entity.getBizType(),
                entity.getBizId(),
                entity.getOperationType(),
                entity.getOperatorName(),
                entity.getOccurredAt(),
                beforeRaw,
                afterRaw,
                diffs
        );
    }

    public static List<FieldDiffItem> computeJsonDiff(String beforeJson, String afterJson) {
        ObjectMapper om = new ObjectMapper();
        Map<String, String> beforeMap = flattenToMap(om, beforeJson);
        Map<String, String> afterMap = flattenToMap(om, afterJson);
        TreeSet<String> allKeys = new TreeSet<>();
        allKeys.addAll(beforeMap.keySet());
        allKeys.addAll(afterMap.keySet());

        List<FieldDiffItem> items = new ArrayList<>();
        for (String key : allKeys) {
            boolean inBefore = beforeMap.containsKey(key);
            boolean inAfter = afterMap.containsKey(key);
            String beforeVal = beforeMap.get(key);
            String afterVal = afterMap.get(key);
            if (!inBefore && inAfter) {
                items.add(new FieldDiffItem(key, null, afterVal, "ADDED"));
            } else if (inBefore && !inAfter) {
                items.add(new FieldDiffItem(key, beforeVal, null, "REMOVED"));
            } else if (!Objects.equals(beforeVal, afterVal)) {
                items.add(new FieldDiffItem(key, beforeVal, afterVal, "MODIFIED"));
            }
        }
        return items;
    }

    private static Map<String, String> flattenToMap(ObjectMapper om, String json) {
        Map<String, String> result = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return result;
        }
        try {
            JsonNode root = om.readTree(json);
            flattenNode("", root, result);
        } catch (Exception ex) {
            result.put("$raw", json);
        }
        return result;
    }

    private static void flattenNode(String prefix, JsonNode node, Map<String, String> out) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            if (!prefix.isEmpty()) {
                out.put(prefix, "null");
            }
            return;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
                flattenNode(key, entry.getValue(), out);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                flattenNode(prefix + "[" + i + "]", node.get(i), out);
            }
        } else {
            out.put(prefix.isEmpty() ? "value" : prefix, node.asText());
        }
    }

    private <T> T required(ObjectProvider<T> provider, String message) {
        T bean = provider.getIfAvailable();
        if (bean == null) {
            throw new IllegalStateException(message);
        }
        return bean;
    }
}
