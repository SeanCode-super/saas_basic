package com.saasbasics.platform.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;

/**
 * WebSocket 握手鉴权拦截器。
 * 从请求 QueryParam、Header 等提取访问令牌，验证用户会话并绑定至 WebSocketSession 上下文。
 */
@Component
public class PlatformWebSocketHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(PlatformWebSocketHandshakeInterceptor.class);

    private final ObjectProvider<WebSocketTokenResolver> tokenResolverProvider;

    public PlatformWebSocketHandshakeInterceptor(ObjectProvider<WebSocketTokenResolver> tokenResolverProvider) {
        this.tokenResolverProvider = tokenResolverProvider;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String token = extractToken(request);
        if (token == null || token.isBlank()) {
            log.warn("WebSocket handshake rejected: missing token");
            return false;
        }

        WebSocketTokenResolver resolver = tokenResolverProvider.getIfAvailable();
        if (resolver != null) {
            WebSocketUserInfo userInfo = resolver.resolveToken(token);
            if (userInfo == null) {
                log.warn("WebSocket handshake rejected: invalid token");
                return false;
            }
            attributes.put(PlatformWebSocketSessionManager.ATTR_USER_ID, userInfo.userId());
            attributes.put(PlatformWebSocketSessionManager.ATTR_TENANT_ID, userInfo.tenantId());
            attributes.put(PlatformWebSocketSessionManager.ATTR_USERNAME, userInfo.username());
        }

        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // no-op
    }

    private String extractToken(ServerHttpRequest request) {
        // 1. From URI Query parameter: ?token=...
        URI uri = request.getURI();
        String query = uri.getQuery();
        if (query != null && !query.isBlank()) {
            for (String param : query.split("&")) {
                String[] pair = param.split("=");
                if (pair.length == 2 && ("token".equalsIgnoreCase(pair[0]) || "accessToken".equalsIgnoreCase(pair[0]))) {
                    return pair[1];
                }
            }
        }

        // 2. From Authorization Header
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        // 3. From Sec-WebSocket-Protocol
        String protocol = request.getHeaders().getFirst("Sec-WebSocket-Protocol");
        if (protocol != null && !protocol.isBlank()) {
            return protocol.trim();
        }

        return null;
    }
}
