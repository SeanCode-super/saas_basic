package com.saasbasics.platform.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.modules.system.dto.NotifyMessageResponse;
import com.saasbasics.platform.modules.system.entity.SystemNotifyMessageEntity;
import com.saasbasics.platform.modules.system.mapper.SystemNotifyMessageMapper;
import com.saasbasics.platform.websocket.PlatformWebSocketSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class SystemNotifyService {

    private static final Logger log = LoggerFactory.getLogger(SystemNotifyService.class);

    private final SystemNotifyMessageMapper messageMapper;
    private final PlatformWebSocketSessionManager webSocketSessionManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SystemNotifyService(SystemNotifyMessageMapper messageMapper,
                               PlatformWebSocketSessionManager webSocketSessionManager) {
        this.messageMapper = messageMapper;
        this.webSocketSessionManager = webSocketSessionManager;
    }

    public List<NotifyMessageResponse> listMyMessages(Long tenantId, Long userId, String category, Boolean unreadOnly) {
        LambdaQueryWrapper<SystemNotifyMessageEntity> query = new LambdaQueryWrapper<SystemNotifyMessageEntity>()
                .eq(tenantId != null && tenantId > 0, SystemNotifyMessageEntity::getTenantId, tenantId)
                .eq(SystemNotifyMessageEntity::getUserId, userId);

        if (category != null && !category.isBlank() && !"ALL".equalsIgnoreCase(category)) {
            query.eq(SystemNotifyMessageEntity::getCategory, category.toUpperCase());
        }

        if (Boolean.TRUE.equals(unreadOnly)) {
            query.eq(SystemNotifyMessageEntity::getReadStatus, 0);
        }

        query.orderByDesc(SystemNotifyMessageEntity::getCreatedAt);

        return messageMapper.selectList(query).stream()
                .map(this::toResponse)
                .toList();
    }

    public long countUnread(Long tenantId, Long userId) {
        LambdaQueryWrapper<SystemNotifyMessageEntity> query = new LambdaQueryWrapper<SystemNotifyMessageEntity>()
                .eq(tenantId != null && tenantId > 0, SystemNotifyMessageEntity::getTenantId, tenantId)
                .eq(SystemNotifyMessageEntity::getUserId, userId)
                .eq(SystemNotifyMessageEntity::getReadStatus, 0);
        return messageMapper.selectCount(query);
    }

    @Transactional
    public void markAsRead(Long userId, Long messageId) {
        LambdaUpdateWrapper<SystemNotifyMessageEntity> update = new LambdaUpdateWrapper<SystemNotifyMessageEntity>()
                .eq(SystemNotifyMessageEntity::getId, messageId)
                .eq(SystemNotifyMessageEntity::getUserId, userId)
                .set(SystemNotifyMessageEntity::getReadStatus, 1)
                .set(SystemNotifyMessageEntity::getReadAt, LocalDateTime.now());
        messageMapper.update(null, update);
    }

    @Transactional
    public void markAllAsRead(Long tenantId, Long userId) {
        LambdaUpdateWrapper<SystemNotifyMessageEntity> update = new LambdaUpdateWrapper<SystemNotifyMessageEntity>()
                .eq(tenantId != null && tenantId > 0, SystemNotifyMessageEntity::getTenantId, tenantId)
                .eq(SystemNotifyMessageEntity::getUserId, userId)
                .eq(SystemNotifyMessageEntity::getReadStatus, 0)
                .set(SystemNotifyMessageEntity::getReadStatus, 1)
                .set(SystemNotifyMessageEntity::getReadAt, LocalDateTime.now());
        messageMapper.update(null, update);
    }

    @Transactional
    public NotifyMessageResponse createAndSend(Long tenantId, Long userId, String category,
                                               String title, String content, String bizType, String bizId) {
        SystemNotifyMessageEntity entity = new SystemNotifyMessageEntity();
        entity.setTenantId(tenantId != null ? tenantId : 1L);
        entity.setUserId(userId);
        entity.setCategory(category != null ? category.toUpperCase() : "SYSTEM");
        entity.setTitle(title);
        entity.setContent(content);
        entity.setReadStatus(0);
        entity.setBizType(bizType);
        entity.setBizId(bizId);
        entity.setCreatedAt(LocalDateTime.now());

        messageMapper.insert(entity);
        NotifyMessageResponse response = toResponse(entity);

        // Push real-time event via WebSocket
        try {
            long unread = countUnread(tenantId, userId);
            String pushJson = objectMapper.writeValueAsString(Map.of(
                    "type", "NOTIFICATION_ARRIVED",
                    "unreadCount", unread,
                    "message", response,
                    "timestamp", System.currentTimeMillis()
            ));
            webSocketSessionManager.sendToUser(userId, pushJson);
        } catch (Exception ex) {
            log.warn("Failed to push real-time WebSocket notification to userId {}: {}", userId, ex.getMessage());
        }

        return response;
    }

    private NotifyMessageResponse toResponse(SystemNotifyMessageEntity entity) {
        return new NotifyMessageResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getUserId(),
                entity.getCategory(),
                entity.getTitle(),
                entity.getContent(),
                entity.getReadStatus(),
                entity.getReadAt(),
                entity.getBizType(),
                entity.getBizId(),
                entity.getExtJson(),
                entity.getCreatedAt()
        );
    }
}
