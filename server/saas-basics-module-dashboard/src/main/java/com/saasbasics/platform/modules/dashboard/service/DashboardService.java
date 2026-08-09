package com.saasbasics.platform.modules.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.modules.dashboard.dto.DashboardMetric;
import com.saasbasics.platform.modules.integration.entity.DatasourceEntity;
import com.saasbasics.platform.modules.integration.mapper.DatasourceMapper;
import com.saasbasics.platform.modules.tenant.entity.TenantEntity;
import com.saasbasics.platform.modules.tenant.mapper.TenantMapper;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private final ObjectProvider<TenantMapper> tenantMapperProvider;
    private final ObjectProvider<DatasourceMapper> datasourceMapperProvider;

    public DashboardService(ObjectProvider<TenantMapper> tenantMapperProvider,
                            ObjectProvider<DatasourceMapper> datasourceMapperProvider) {
        this.tenantMapperProvider = tenantMapperProvider;
        this.datasourceMapperProvider = datasourceMapperProvider;
    }

    public List<DashboardMetric> listMetrics() {
        TenantMapper tenantMapper = tenantMapperProvider.getIfAvailable();
        DatasourceMapper datasourceMapper = datasourceMapperProvider.getIfAvailable();
        if (tenantMapper != null && datasourceMapper != null) {
            long tenantCount = tenantMapper.selectCount(new LambdaQueryWrapper<TenantEntity>().eq(TenantEntity::getDeleted, 0));
            long datasourceCount = datasourceMapper.selectCount(new LambdaQueryWrapper<DatasourceEntity>().eq(DatasourceEntity::getDeleted, 0));
            return List.of(
                    new DashboardMetric("activeTenants", "Active Tenants", String.valueOf(tenantCount), "live"),
                    new DashboardMetric("datasources", "Connected Datasources", String.valueOf(datasourceCount), "live"),
                    new DashboardMetric("jobsToday", "Jobs Today", "9,284", "99.92%"),
                    new DashboardMetric("generatedModules", "Generated Modules", "63", "+4")
            );
        }
        return List.of(
                new DashboardMetric("activeTenants", "Active Tenants", "126", "+12%"),
                new DashboardMetric("datasources", "Connected Datasources", "48", "+7"),
                new DashboardMetric("jobsToday", "Jobs Today", "9,284", "99.92%"),
                new DashboardMetric("generatedModules", "Generated Modules", "63", "+4")
        );
    }
}
