package com.saasbasics.platform.modules.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProcessStartRequest(
        @NotNull(message = "流程定义ID不能为空")
        Long definitionId,

        @NotBlank(message = "审批单标题不能为空")
        String title,

        @NotBlank(message = "表单业务数据不能为空")
        String formDataJson
) {
}
