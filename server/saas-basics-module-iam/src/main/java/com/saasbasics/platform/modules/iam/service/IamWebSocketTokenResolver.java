package com.saasbasics.platform.modules.iam.service;

import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.websocket.WebSocketTokenResolver;
import com.saasbasics.platform.websocket.WebSocketUserInfo;
import org.springframework.stereotype.Component;

/**
 * IAM 模块对 WebSocket 握手令牌解析的实现。
 */
@Component
public class IamWebSocketTokenResolver implements WebSocketTokenResolver {

    private final AuthService authService;

    public IamWebSocketTokenResolver(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public WebSocketUserInfo resolveToken(String token) {
        AuthPrincipal principal = authService.resolvePrincipal(token);
        if (principal == null) {
            return null;
        }
        return new WebSocketUserInfo(principal.userId(), principal.tenantId(), principal.username());
    }
}
