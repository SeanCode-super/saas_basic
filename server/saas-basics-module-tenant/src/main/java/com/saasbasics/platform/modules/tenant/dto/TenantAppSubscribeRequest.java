package com.saasbasics.platform.modules.tenant.dto;

import jakarta.validation.constraints.NotBlank;

public record TenantAppSubscribeRequest(
        @NotBlank(message = "应用编码不能为空")
        String appCode,
        Integer trialDays,
        String remark
) {
}
