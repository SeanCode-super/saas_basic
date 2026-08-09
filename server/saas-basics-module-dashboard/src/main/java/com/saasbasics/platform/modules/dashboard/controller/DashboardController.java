package com.saasbasics.platform.modules.dashboard.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.dashboard.dto.DashboardMetric;
import com.saasbasics.platform.modules.dashboard.service.DashboardService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/metrics")
    @RequirePermission("dashboard:metrics:query")
    public ApiResponse<List<DashboardMetric>> metrics() {
        return ApiResponse.success(dashboardService.listMetrics());
    }
}
