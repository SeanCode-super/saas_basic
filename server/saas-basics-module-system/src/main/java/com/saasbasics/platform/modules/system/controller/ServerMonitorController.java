package com.saasbasics.platform.modules.system.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.system.dto.AlertGovernanceDtos.AlertEvaluationSummary;
import com.saasbasics.platform.modules.system.dto.AlertGovernanceDtos.AlertEventResponse;
import com.saasbasics.platform.modules.system.dto.AlertGovernanceDtos.AlertRuleResponse;
import com.saasbasics.platform.modules.system.dto.AlertGovernanceDtos.AlertRuleSaveRequest;
import com.saasbasics.platform.modules.system.dto.ServerMonitorResponse;
import com.saasbasics.platform.modules.system.dto.ThreadDumpItem;
import com.saasbasics.platform.modules.system.service.AlertGovernanceService;
import com.saasbasics.platform.modules.system.service.ServerMonitorService;
import com.saasbasics.platform.security.transport.TransportCryptoPolicyService;
import com.saasbasics.platform.telemetry.PlatformApiCatalogService;
import com.saasbasics.platform.telemetry.PlatformTelemetryService;
import com.saasbasics.platform.telemetry.TelemetrySnapshot;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/infra/server-monitor")
public class ServerMonitorController {

    private final ServerMonitorService serverMonitorService;
    private final ObjectProvider<PlatformTelemetryService> telemetryServiceProvider;
    private final ObjectProvider<PlatformApiCatalogService> apiCatalogServiceProvider;
    private final ObjectProvider<TransportCryptoPolicyService> transportPolicyServiceProvider;
    private final ObjectProvider<AlertGovernanceService> alertGovernanceServiceProvider;

    public ServerMonitorController(
            ServerMonitorService serverMonitorService,
            ObjectProvider<PlatformTelemetryService> telemetryServiceProvider) {
        this(serverMonitorService, telemetryServiceProvider, null, null, null);
    }

    @Autowired
    public ServerMonitorController(
            ServerMonitorService serverMonitorService,
            ObjectProvider<PlatformTelemetryService> telemetryServiceProvider,
            ObjectProvider<PlatformApiCatalogService> apiCatalogServiceProvider,
            ObjectProvider<TransportCryptoPolicyService> transportPolicyServiceProvider,
            ObjectProvider<AlertGovernanceService> alertGovernanceServiceProvider) {
        this.serverMonitorService = serverMonitorService;
        this.telemetryServiceProvider = telemetryServiceProvider;
        this.apiCatalogServiceProvider = apiCatalogServiceProvider;
        this.transportPolicyServiceProvider = transportPolicyServiceProvider;
        this.alertGovernanceServiceProvider = alertGovernanceServiceProvider;
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
            @RequestParam(required = false) String webhookUrl,
            @RequestParam(required = false) String channel) {
        return ApiResponse.success(serverMonitorService.triggerAlarmTest(webhookUrl, channel));
    }

    @GetMapping("/alerts/rules")
    @RequirePermission("system:config:query")
    public ApiResponse<List<AlertRuleResponse>> listAlertRules() {
        AlertGovernanceService svc = alertGovernanceServiceProvider != null ? alertGovernanceServiceProvider.getIfAvailable() : null;
        return ApiResponse.success(svc != null ? svc.listRules() : List.of());
    }

    @PostMapping("/alerts/rules")
    @RequirePermission("system:config:write")
    public ApiResponse<AlertRuleResponse> createAlertRule(@Valid @RequestBody AlertRuleSaveRequest request) {
        return ApiResponse.success(alertGovernanceServiceProvider.getObject().createRule(request));
    }

    @PutMapping("/alerts/rules/{id}")
    @RequirePermission("system:config:write")
    public ApiResponse<AlertRuleResponse> updateAlertRule(@PathVariable Long id,
                                                          @Valid @RequestBody AlertRuleSaveRequest request) {
        return ApiResponse.success(alertGovernanceServiceProvider.getObject().updateRule(id, request));
    }

    @DeleteMapping("/alerts/rules/{id}")
    @RequirePermission("system:config:write")
    public ApiResponse<Void> deleteAlertRule(@PathVariable Long id) {
        alertGovernanceServiceProvider.getObject().deleteRule(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/alerts/evaluate")
    @RequirePermission("system:config:write")
    public ApiResponse<AlertEvaluationSummary> evaluateAlerts(
            @RequestParam(defaultValue = "false") boolean ignoreCooldown) {
        return ApiResponse.success(alertGovernanceServiceProvider.getObject().evaluateAllRules(ignoreCooldown));
    }

    @GetMapping("/alerts/events")
    @RequirePermission("system:config:query")
    public ApiResponse<List<AlertEventResponse>> listAlertEvents(@RequestParam(defaultValue = "50") int limit) {
        AlertGovernanceService svc = alertGovernanceServiceProvider != null ? alertGovernanceServiceProvider.getIfAvailable() : null;
        return ApiResponse.success(svc != null ? svc.listEvents(limit) : List.of());
    }

    @PostMapping("/alerts/events/{id}/ack")
    @RequirePermission("system:config:write")
    public ApiResponse<AlertEventResponse> acknowledgeAlertEvent(@PathVariable Long id) {
        return ApiResponse.success(alertGovernanceServiceProvider.getObject().acknowledgeEvent(id));
    }
}

