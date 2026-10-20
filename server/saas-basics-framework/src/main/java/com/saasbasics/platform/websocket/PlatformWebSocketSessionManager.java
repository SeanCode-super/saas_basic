package com.saasbasics.platform.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 平台企业级 WebSocket 会话并发管理器。
 * 维护每个用户及租户下的多终端活跃 WebSocket 连接，支持精准点对点推送、租户广播与全平台广播。
 */
@Component
public class PlatformWebSocketSessionManager {

    private static final Logger log = LoggerFactory.getLogger(PlatformWebSocketSessionManager.class);

    public static final String ATTR_USER_ID = "WS_USER_ID";
    public static final String ATTR_TENANT_ID = "WS_TENANT_ID";
    public static final String ATTR_USERNAME = "WS_USERNAME";

    // userId -> Set<WebSocketSession>
    private final Map<Long, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    // tenantId -> Set<WebSocketSession>
    private final Map<Long, Set<WebSocketSession>> tenantSessions = new ConcurrentHashMap<>();

    // All active sessions
    private final Set<WebSocketSession> allSessions = new CopyOnWriteArraySet<>();

    public void registerSession(WebSocketSession session) {
        allSessions.add(session);
        Long userId = (Long) session.getAttributes().get(ATTR_USER_ID);
        Long tenantId = (Long) session.getAttributes().get(ATTR_TENANT_ID);

        if (userId != null) {
            userSessions.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);
        }
        if (tenantId != null) {
            tenantSessions.computeIfAbsent(tenantId, k -> new CopyOnWriteArraySet<>()).add(session);
        }
        log.info("WebSocket connection established. SessionId: {}, UserId: {}, TenantId: {}, TotalOnline: {}",
                session.getId(), userId, tenantId, allSessions.size());
    }

    public void removeSession(WebSocketSession session) {
        allSessions.remove(session);
        Long userId = (Long) session.getAttributes().get(ATTR_USER_ID);
        Long tenantId = (Long) session.getAttributes().get(ATTR_TENANT_ID);

        if (userId != null) {
            Set<WebSocketSession> set = userSessions.get(userId);
            if (set != null) {
                set.remove(session);
                if (set.isEmpty()) {
                    userSessions.remove(userId);
                }
            }
        }
        if (tenantId != null) {
            Set<WebSocketSession> set = tenantSessions.get(tenantId);
            if (set != null) {
                set.remove(session);
                if (set.isEmpty()) {
                    tenantSessions.remove(tenantId);
                }
            }
        }
        log.info("WebSocket connection closed. SessionId: {}, TotalOnline: {}", session.getId(), allSessions.size());
    }

    public void sendToUser(Long userId, String jsonMessage) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        TextMessage textMessage = new TextMessage(jsonMessage);
        for (WebSocketSession session : sessions) {
            safeSend(session, textMessage);
        }
    }

    public void sendToTenant(Long tenantId, String jsonMessage) {
        Set<WebSocketSession> sessions = tenantSessions.get(tenantId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        TextMessage textMessage = new TextMessage(jsonMessage);
        for (WebSocketSession session : sessions) {
            safeSend(session, textMessage);
        }
    }

    public void broadcast(String jsonMessage) {
        TextMessage textMessage = new TextMessage(jsonMessage);
        for (WebSocketSession session : allSessions) {
            safeSend(session, textMessage);
        }
    }

    public int getActiveCount() {
        return allSessions.size();
    }

    private void safeSend(WebSocketSession session, TextMessage message) {
        if (session != null && session.isOpen()) {
            try {
                synchronized (session) {
                    session.sendMessage(message);
                }
            } catch (IOException e) {
                log.warn("Failed to send WebSocket message to session {}: {}", session.getId(), e.getMessage());
            }
        }
    }
}
