package com.saasbasics.platform.modules.workflow.dto;

import jakarta.validation.constraints.NotBlank;

public record ProcessDefinitionSaveRequest(
        @NotBlank(message = "流程编码不能为空")
        String processCode,

        @NotBlank(message = "流程名称不能为空")
        String processName,

        String category,
        String icon,
        String color,
        String description,
        String formSchemaJson,
        String flowNodesJson,
        String status,
        Integer sortNo
) {
}
