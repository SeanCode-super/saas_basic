package com.saasbasics.platform.datasource;

import com.saasbasics.platform.common.tenant.TenantAccessContext;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

public class DynamicRoutingDataSource extends AbstractRoutingDataSource {

    public static final String DEFAULT_DATASOURCE_KEY = "DEFAULT";

    private final TenantDataSourceRegistry registry;
    private volatile Object defaultDataSource;
    private volatile boolean initialized;

    public DynamicRoutingDataSource(TenantDataSourceRegistry registry) {
        this.registry = registry;
        this.registry.addChangeListener(this::onRegistryChanged);
    }

    @Override
    public void setDefaultTargetDataSource(Object defaultTargetDataSource) {
        this.defaultDataSource = defaultTargetDataSource;
        super.setDefaultTargetDataSource(defaultTargetDataSource);
    }

    @Override
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
        this.initialized = true;
    }

    /**
     * Rebuilds the resolved lookup map from the current {@link TenantDataSourceRegistry} snapshot.
     */
    public synchronized void refreshTargetDataSources() {
        if (defaultDataSource == null) {
            return;
        }
        Map<Object, Object> targets = new LinkedHashMap<>();
        targets.put(DEFAULT_DATASOURCE_KEY, defaultDataSource);
        targets.putAll(registry.getAllDedicatedDataSources());
        super.setTargetDataSources(targets);
        super.afterPropertiesSet();
        this.initialized = true;
    }

    private void onRegistryChanged() {
        if (initialized && defaultDataSource != null) {
            refreshTargetDataSources();
        }
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
