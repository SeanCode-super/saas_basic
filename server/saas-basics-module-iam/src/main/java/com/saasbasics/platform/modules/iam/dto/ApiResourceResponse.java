package com.saasbasics.platform.modules.iam.dto;

public record ApiResourceResponse(
        Long id,
        Long tenantId,
        String resourceCode,
        String resourceName,
        String httpMethod,
        String urlPattern,
        Boolean authRequired,
        String status,
        String remark
) {
}
