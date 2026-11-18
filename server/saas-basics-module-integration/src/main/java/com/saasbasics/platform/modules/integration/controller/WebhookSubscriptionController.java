package com.saasbasics.platform.modules.integration.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.integration.dto.WebhookDtos.WebhookDeliveryResponse;
import com.saasbasics.platform.modules.integration.dto.WebhookDtos.WebhookSubscriptionResponse;
import com.saasbasics.platform.modules.integration.dto.WebhookDtos.WebhookSubscriptionSaveRequest;
import com.saasbasics.platform.modules.integration.service.WebhookSubscriptionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 租户级 CloudEvents 1.0 Webhook 订阅管理与投递补偿控制器。
 */
@RestController
@RequestMapping("/api/integration/webhooks")
@RequirePermission("integration:datasource:query")
public class WebhookSubscriptionController {

    private final WebhookSubscriptionService webhookSubscriptionService;

    public WebhookSubscriptionController(WebhookSubscriptionService webhookSubscriptionService) {
        this.webhookSubscriptionService = webhookSubscriptionService;
    }

    @GetMapping("/subscriptions")
    public ApiResponse<List<WebhookSubscriptionResponse>> listSubscriptions() {
        return ApiResponse.success(webhookSubscriptionService.listSubscriptions());
    }

    @PostMapping("/subscriptions")
    @RequirePermission("integration:datasource:write")
    public ApiResponse<WebhookSubscriptionResponse> createSubscription(
            @Valid @RequestBody WebhookSubscriptionSaveRequest request
    ) {
        return ApiResponse.success(webhookSubscriptionService.createSubscription(request));
    }

    @DeleteMapping("/subscriptions/{id}")
    @RequirePermission("integration:datasource:write")
    public ApiResponse<Void> deleteSubscription(@PathVariable Long id) {
        webhookSubscriptionService.deleteSubscription(id);
        return ApiResponse.success(null);
    }

    @GetMapping("/deliveries")
    public ApiResponse<List<WebhookDeliveryResponse>> listDeliveries() {
        return ApiResponse.success(webhookSubscriptionService.listDeliveries());
    }

    @PostMapping("/test/{id}")
    @RequirePermission("integration:datasource:write")
    public ApiResponse<WebhookDeliveryResponse> sendTestEvent(
            @PathVariable Long id,
            @RequestParam(required = false) String eventType
    ) {
        return ApiResponse.success(webhookSubscriptionService.sendTestEvent(id, eventType));
    }

    @PostMapping("/deliveries/{logId}/replay")
    @RequirePermission("integration:datasource:write")
    public ApiResponse<WebhookDeliveryResponse> replayDelivery(@PathVariable Long logId) {
        return ApiResponse.success(webhookSubscriptionService.replayDelivery(logId));
    }
}
