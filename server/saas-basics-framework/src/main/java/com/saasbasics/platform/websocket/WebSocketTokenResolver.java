package com.saasbasics.platform.websocket;

/**
 * WebSocket 握手令牌解析 SPI 接口
 */
@FunctionalInterface
public interface WebSocketTokenResolver {
    WebSocketUserInfo resolveToken(String token);
}
