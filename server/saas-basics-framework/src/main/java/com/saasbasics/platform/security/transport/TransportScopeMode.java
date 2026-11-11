package com.saasbasics.platform.security.transport;

/**
 * 接口传输加密作用范围策略。
 */
public enum TransportScopeMode {
    /**
     * 全量业务接口（除内置豁免及 excludePaths 之外的所有 /api/**）。
     */
    ALL_API,
    /**
     * 仅高敏接口（认证、IAM 权限、系统管理、AI 治理、租户管理等敏感路径）。
     */
    SENSITIVE_ONLY,
    /**
     * 自定义路径规则（完全由 includePaths 与 excludePaths 决定）。
     */
    CUSTOM
}
