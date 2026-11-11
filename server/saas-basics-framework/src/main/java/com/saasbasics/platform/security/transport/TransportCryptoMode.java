package com.saasbasics.platform.security.transport;

/**
 * 接口传输加密运行模式。
 */
public enum TransportCryptoMode {
    /**
     * 关闭：前后端均按明文传输。
     */
    OFF,
    /**
     * 兼容协商（推荐）：受控客户端自动加密请求并解密响应；未加密的第三方/调试请求按明文放行。
     */
    OPTIONAL,
    /**
     * 强制加密：命中加密范围且未豁免的接口拒绝明文请求并返回 HTTP 428。
     */
    REQUIRED
}
