package com.saasbasics.platform.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 平台 WebSocket 路由配置
 */
@Configuration
@EnableWebSocket
public class PlatformWebSocketConfig implements WebSocketConfigurer {

    private final PlatformWebSocketHandler webSocketHandler;
    private final PlatformWebSocketHandshakeInterceptor handshakeInterceptor;

    public PlatformWebSocketConfig(PlatformWebSocketHandler webSocketHandler,
                                   PlatformWebSocketHandshakeInterceptor handshakeInterceptor) {
        this.webSocketHandler = webSocketHandler;
        this.handshakeInterceptor = handshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketHandler, "/api/ws")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
