package com.saasbasics.platform.ratelimit;

public enum LimitType {
    /**
     * 默认策略：全局限流
     */
    DEFAULT,

    /**
     * 根据 IP 限流
     */
    IP,

    /**
     * 根据当前登录用户限流
     */
    USER
}
