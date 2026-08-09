package com.saasbasics.platform.modules.audit.service;

import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.modules.audit.api.AuditRecorder;
import com.saasbasics.platform.modules.audit.entity.OperationLogEntity;
import com.saasbasics.platform.modules.audit.mapper.OperationLogMapper;
import java.util.Map;
import java.time.LocalDateTime;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class AuditTrailService implements AuditRecorder {

    private final ObjectProvider<OperationLogMapper> operationLogMapperProvider;
    private final ObjectMapper objectMapper;

    public AuditTrailService(ObjectProvider<OperationLogMapper> operationLogMapperProvider,
                             ObjectMapper objectMapper) {
        this.operationLogMapperProvider = operationLogMapperProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    public void record(String bizModule,
                       String bizType,
                       String bizId,
                       String operationType,
                       String requestBody,
                       String responseBody,
                       boolean success) {
        OperationLogMapper mapper = operationLogMapperProvider.getIfAvailable();
        if (mapper == null) {
            return;
        }

        AuthPrincipal principal = AuthContext.get();
        OperationLogEntity entity = new OperationLogEntity();
        entity.setTenantId(principal != null && principal.tenantId() != null ? principal.tenantId() : 0L);
        entity.setOperatorUserId(principal != null && principal.userId() != null ? principal.userId() : 0L);
        entity.setOperatorName(principal != null ? principal.username() : "system");
        entity.setBizModule(bizModule);
        entity.setBizType(bizType);
        entity.setBizId(bizId);
        entity.setOperationType(operationType);
        entity.setRequestUri(null);
        entity.setHttpMethod(null);
        entity.setRequestBody(toJsonPayload(requestBody));
        entity.setResponseBody(toJsonPayload(responseBody));
        entity.setSuccess(success);
        entity.setDurationMs(0);
        entity.setOccurredAt(LocalDateTime.now());
        mapper.insert(entity);
    }

    private String toJsonPayload(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(Map.of("value", value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("操作审计序列化失败", exception);
        }
    }
}
