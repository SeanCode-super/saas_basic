package com.saasbasics.platform.datasource;

import com.saasbasics.platform.common.tenant.TenantAccessContext;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

public class DynamicRoutingDataSource extends AbstractRoutingDataSource {

    public static final String DEFAULT_DATASOURCE_KEY = "DEFAULT";

    private final TenantDataSourceRegistry registry;

    public DynamicRoutingDataSource(TenantDataSourceRegistry registry) {
        this.registry = registry;
    }

    @Override
    protected Object determineCurrentLookupKey() {
        TenantAccessContext context = TenantAccessContextHolder.current();
        if (context == null || context.bypassesTenantIsolation()) {
            return DEFAULT_DATASOURCE_KEY;
        }

        Long tenantId = context.tenantId();
        if (tenantId != null && registry.hasDedicatedDataSource(tenantId)) {
            return tenantId;
        }

        return DEFAULT_DATASOURCE_KEY;
    }
}
