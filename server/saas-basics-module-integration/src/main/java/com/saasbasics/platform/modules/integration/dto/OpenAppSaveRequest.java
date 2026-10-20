package com.saasbasics.platform.modules.integration.dto;

import jakarta.validation.constraints.NotBlank;

public record OpenAppSaveRequest(
        Long id,
        @NotBlank(message = "应用名称不能为空")
        String appName,
        String status,
        Integer rateLimit,
        String allowedPermissions,
        String remark
) {}
