package com.saasbasics.platform.modules.system.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.outbox.PlatformOutboxService;
import com.saasbasics.platform.outbox.PlatformOutboxService.OutboxEventRecordView;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/outbox")
public class OutboxController {

    private final PlatformOutboxService outboxService;

    public OutboxController(PlatformOutboxService outboxService) {
        this.outboxService = outboxService;
    }

    @GetMapping("/events")
    @RequirePermission("system:config:query")
    public ApiResponse<List<OutboxEventRecordView>> listEvents(@RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.success(outboxService.listRecentEvents(limit));
    }

    @PostMapping("/events/{id}/retry")
    @RequirePermission("system:config:write")
    public ApiResponse<Map<String, Object>> retryEvent(@PathVariable long id) {
        int dispatched = outboxService.retryEvent(id);
        return ApiResponse.success(Map.of("retriedId", id, "dispatchedCount", dispatched));
    }
}
