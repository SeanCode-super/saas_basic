package com.saasbasics.platform.modules.system.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.modules.system.dto.NotifyMessageResponse;
import com.saasbasics.platform.modules.system.dto.NotifySendRequest;
import com.saasbasics.platform.modules.system.service.SystemNotifyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notify")
public class SystemNotifyController {

    private final SystemNotifyService notifyService;

    public SystemNotifyController(SystemNotifyService notifyService) {
        this.notifyService = notifyService;
    }

    @GetMapping("/my-messages")
    public ApiResponse<List<NotifyMessageResponse>> getMyMessages(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean unreadOnly) {
        AuthPrincipal principal = AuthContext.get();
        if (principal == null) {
            return ApiResponse.success(List.of());
        }
        return ApiResponse.success(notifyService.listMyMessages(
                principal.tenantId(), principal.userId(), category, unreadOnly));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Object>> getUnreadCount() {
        AuthPrincipal principal = AuthContext.get();
        if (principal == null) {
            return ApiResponse.success(Map.of("unreadCount", 0));
        }
        long count = notifyService.countUnread(principal.tenantId(), principal.userId());
        return ApiResponse.success(Map.of("unreadCount", count));
    }

    @PutMapping("/read/{id}")
    public ApiResponse<Void> markAsRead(@PathVariable Long id) {
        AuthPrincipal principal = AuthContext.get();
        if (principal != null) {
            notifyService.markAsRead(principal.userId(), id);
        }
        return ApiResponse.success(null);
    }

    @PutMapping("/read-all")
    public ApiResponse<Void> markAllAsRead() {
        AuthPrincipal principal = AuthContext.get();
        if (principal != null) {
            notifyService.markAllAsRead(principal.tenantId(), principal.userId());
        }
        return ApiResponse.success(null);
    }

    @PostMapping("/send-test")
    public ApiResponse<NotifyMessageResponse> sendTestMessage(@RequestBody(required = false) NotifySendRequest request) {
        AuthPrincipal principal = AuthContext.get();
        Long tenantId = principal != null ? principal.tenantId() : 1L;
        Long targetUserId = (request != null && request.targetUserId() != null)
                ? request.targetUserId()
                : (principal != null ? principal.userId() : 1L);

        String category = (request != null && request.category() != null) ? request.category() : "NOTICE";
        String title = (request != null && request.title() != null) ? request.title() : "系统实时推送测试";
        String content = (request != null && request.content() != null) ? request.content() : "这是一条通过平台 WebSocket 与持久化通知中心分发的实时测试消息。";
        String bizType = (request != null && request.bizType() != null) ? request.bizType() : "SYSTEM";
        String bizId = (request != null && request.bizId() != null) ? request.bizId() : "TEST_001";

        NotifyMessageResponse sent = notifyService.createAndSend(
                tenantId,
                targetUserId,
                category,
                title,
                content,
                bizType,
                bizId
        );
        return ApiResponse.success(sent);
    }
}
