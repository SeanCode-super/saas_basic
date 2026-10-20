package com.saasbasics.platform.modules.system.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.system.dto.ServerMonitorResponse;
import com.saasbasics.platform.modules.system.dto.ThreadDumpItem;
import com.saasbasics.platform.modules.system.service.ServerMonitorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/infra/server-monitor")
public class ServerMonitorController {

    private final ServerMonitorService serverMonitorService;

    public ServerMonitorController(ServerMonitorService serverMonitorService) {
        this.serverMonitorService = serverMonitorService;
    }

    @GetMapping
    @RequirePermission("system:config:query")
    public ApiResponse<ServerMonitorResponse> getServerMonitor() {
        return ApiResponse.success(serverMonitorService.collectMetrics());
    }

    @GetMapping("/thread-dump")
    @RequirePermission("system:config:query")
    public ApiResponse<List<ThreadDumpItem>> getThreadDump() {
        return ApiResponse.success(serverMonitorService.dumpThreads());
    }

    @PostMapping("/alarm-test")
    @RequirePermission("system:config:query")
    public ApiResponse<java.util.Map<String, Object>> triggerAlarmTest(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String webhookUrl,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String channel) {
        return ApiResponse.success(serverMonitorService.triggerAlarmTest(webhookUrl, channel));
    }
}

