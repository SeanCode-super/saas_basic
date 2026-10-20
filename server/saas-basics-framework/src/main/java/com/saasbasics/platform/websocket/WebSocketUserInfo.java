package com.saasbasics.platform.websocket;

/**
 * WebSocket 连接主体凭据信息
 */
public record WebSocketUserInfo(
        Long userId,
        Long tenantId,
        String username
) {
}
