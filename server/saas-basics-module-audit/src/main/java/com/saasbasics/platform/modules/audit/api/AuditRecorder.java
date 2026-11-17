package com.saasbasics.platform.modules.audit.api;

public interface AuditRecorder {

    void record(String bizModule,
                String bizType,
                String bizId,
                String operationType,
                String requestBody,
                String responseBody,
                boolean success);

    default void recordDiff(String bizModule,
                            String bizType,
                            String bizId,
                            String operationType,
                            String beforeJson,
                            String afterJson,
                            boolean success) {
        record(bizModule, bizType, bizId, operationType, beforeJson, afterJson, success);
    }
}
