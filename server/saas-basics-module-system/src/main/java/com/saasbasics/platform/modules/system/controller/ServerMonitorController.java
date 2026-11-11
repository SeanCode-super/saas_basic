package com.saasbasics.platform.modules.system.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.system.dto.ServerMonitorResponse;
import com.saasbasics.platform.modules.system.dto.ThreadDumpItem;
import com.saasbasics.platform.modules.system.service.ServerMonitorService;
import com.saasbasics.platform.security.transport.TransportCryptoPolicyService;
import com.saasbasics.platform.telemetry.PlatformApiCatalogService;
import com.saasbasics.platform.telemetry.PlatformTelemetryService;
import com.saasbasics.platform.telemetry.TelemetrySnapshot;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/infra/server-monitor")
public class ServerMonitorController {

    private final ServerMonitorService serverMonitorService;
    private final ObjectProvider<PlatformTelemetryService> telemetryServiceProvider;
    private final ObjectProvider<PlatformApiCatalogService> apiCatalogServiceProvider;
    private final ObjectProvider<TransportCryptoPolicyService> transportPolicyServiceProvider;

    public ServerMonitorController(
            ServerMonitorService serverMonitorService,
            ObjectProvider<PlatformTelemetryService> telemetryServiceProvider) {
        this(serverMonitorService, telemetryServiceProvider, null, null);
    }

    @Autowired
    public ServerMonitorController(
            ServerMonitorService serverMonitorService,
            ObjectProvider<PlatformTelemetryService> telemetryServiceProvider,
            ObjectProvider<PlatformApiCatalogService> apiCatalogServiceProvider,
            ObjectProvider<TransportCryptoPolicyService> transportPolicyServiceProvider) {
        this.serverMonitorService = serverMonitorService;
        this.telemetryServiceProvider = telemetryServiceProvider;
        this.apiCatalogServiceProvider = apiCatalogServiceProvider;
        this.transportPolicyServiceProvider = transportPolicyServiceProvider;
    }

    @GetMapping
    @RequirePermission("system:config:query")
    public ApiResponse<ServerMonitorResponse> getServerMonitor() {
        return ApiResponse.success(serverMonitorService.collectMetrics());
    }

    @GetMapping("/telemetry")
    @RequirePermission("system:config:query")
    public ApiResponse<TelemetrySnapshot> getTelemetrySnapshot() {
        PlatformTelemetryService telemetry = telemetryServiceProvider.getIfAvailable();
        if (telemetry == null) {
            return ApiResponse.success(null);
        }
        return ApiResponse.success(telemetry.getSnapshot());
    }

    @GetMapping("/api-stats")
    @RequirePermission("system:config:query")
    public ApiResponse<PlatformApiCatalogService.ApiCatalogSnapshot> getApiStats() {
        PlatformApiCatalogService catalog = apiCatalogServiceProvider != null
                ? apiCatalogServiceProvider.getIfAvailable()
                : null;
        if (catalog == null) {
            return ApiResponse.success(null);
        }
        TransportCryptoPolicyService policyService = transportPolicyServiceProvider != null
                ? transportPolicyServiceProvider.getIfAvailable()
                : null;
        return ApiResponse.success(catalog.buildSnapshot(
                policyService != null ? policyService::evaluateDecisionForPattern : null
        ));
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

