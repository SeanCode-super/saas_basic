package com.saasbasics.platform.modules.dashboard.service;

import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.dashboard.dto.DashboardMetric;
import com.saasbasics.platform.modules.dashboard.mapper.DashboardMetricMapper;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {
    private final ObjectProvider<DashboardMetricMapper> metrics;

    public DashboardService(ObjectProvider<DashboardMetricMapper> metrics) {
        this.metrics = metrics;
    }

    public List<DashboardMetric> listMetrics() {
        var user = AuthContext.get();
        if (user == null || user.tenantId() == null) throw new BizException("AUTH_FORBIDDEN", "需要已登录的租户身份");
        var mapper = metrics.getIfAvailable();
        if (mapper == null) throw new BizException("DASHBOARD_UNAVAILABLE", "指标数据源不可用");
        return mapper.listMetrics(user.tenantId(), user.isPlatformTenant() && user.isSuperAdmin());
    }
}
