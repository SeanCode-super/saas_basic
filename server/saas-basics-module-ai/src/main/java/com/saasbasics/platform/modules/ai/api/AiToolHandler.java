package com.saasbasics.platform.modules.ai.api;

import java.util.Map;

/**
 * 行业与平台模块向 AI 模块注册受控业务工具的标准协议接口。
 */
public interface AiToolHandler {

    String toolCode();

    String toolName();

    String description();

    default String semanticVersion() {
        return "1.0.0";
    }

    default String permissionCode() {
        return "system:config:query";
    }

    default ToolRiskLevel riskLevel() {
        return ToolRiskLevel.READ_ONLY;
    }

    default boolean idempotent() {
        return true;
    }

    default String inputSchemaJson() {
        return "{\"type\":\"object\",\"properties\":{},\"additionalProperties\":true}";
    }

    AiToolExecutionResult execute(Long tenantId, Long userId, Map<String, Object> arguments);
}
