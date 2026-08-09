package com.saasbasics.platform.modules.audit.api;

public interface AuditRecorder {

    void record(String bizModule,
                String bizType,
                String bizId,
                String operationType,
                String requestBody,
                String responseBody,
                boolean success);
}
