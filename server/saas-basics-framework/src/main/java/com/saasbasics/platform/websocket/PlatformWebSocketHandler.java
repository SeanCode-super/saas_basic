package com.saasbasics.platform.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

/**
 * 平台企业级 WebSocket 消息处理器。
 * 处理心跳检测 (PING / PONG) 与握手建立/断开生命周期。
 */
@Component
public class PlatformWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(PlatformWebSocketHandler.class);

    private final PlatformWebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    public PlatformWebSocketHandler(PlatformWebSocketSessionManager sessionManager, ObjectMapper objectMapper) {
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessionManager.registerSession(session);
        try {
            // Send connection acknowledge
            String welcomeJson = objectMapper.writeValueAsString(Map.of(
                    "type", "CONNECTED",
                    "sessionId", session.getId(),
                    "timestamp", System.currentTimeMillis()
            ));
            session.sendMessage(new TextMessage(welcomeJson));
        } catch (Exception e) {
            log.warn("Failed to send welcome message: {}", e.getMessage());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        // Heartbeat ping-pong
        if ("PING".equalsIgnoreCase(payload) || payload.contains("\"type\":\"PING\"")) {
            session.sendMessage(new TextMessage("{\"type\":\"PONG\",\"timestamp\":" + System.currentTimeMillis() + "}"));
            return;
        }

        log.debug("Received WebSocket message from session {}: {}", session.getId(), payload);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionManager.removeSession(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("WebSocket transport error on session {}: {}", session.getId(), exception.getMessage());
        sessionManager.removeSession(session);
    }
}
