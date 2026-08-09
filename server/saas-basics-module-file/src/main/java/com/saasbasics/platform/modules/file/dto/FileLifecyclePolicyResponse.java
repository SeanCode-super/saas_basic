package com.saasbasics.platform.modules.file.dto;

public record FileLifecyclePolicyResponse(
        Long id,
        Long tenantId,
        String policyCode,
        String policyName,
        String fileScope,
        Integer retentionDays,
        Integer archiveAfterDays,
        Integer deleteAfterDays,
        Boolean deduplicateEnabled,
        Integer versionRetentionCount,
        String status,
        String remark
) {
}
