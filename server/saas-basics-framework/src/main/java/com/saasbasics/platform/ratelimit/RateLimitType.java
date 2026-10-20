package com.saasbasics.platform.ratelimit;

public enum RateLimitType {
    /**
     * 租户级限流（隔离不同租户的 QPS 配额）
     */
    TENANT,

    /**
     * 用户级限流（单用户频次限制）
     */
    USER,

    /**
     * 客户端 IP 级限流
     */
    IP,

    /**
     * 全局 API 限流
     */
    GLOBAL
}
