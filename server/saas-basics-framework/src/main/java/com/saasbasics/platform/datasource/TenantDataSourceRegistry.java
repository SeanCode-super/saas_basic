package com.saasbasics.platform.datasource;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

@Component
public class TenantDataSourceRegistry {

    private final Map<Long, DataSource> dedicatedDataSources = new ConcurrentHashMap<>();

    public void registerDataSource(Long tenantId, DataSource dataSource) {
        if (tenantId != null && dataSource != null) {
            dedicatedDataSources.put(tenantId, dataSource);
        }
    }

    public void unregisterDataSource(Long tenantId) {
        if (tenantId != null) {
            dedicatedDataSources.remove(tenantId);
        }
    }

    public boolean hasDedicatedDataSource(Long tenantId) {
        return tenantId != null && dedicatedDataSources.containsKey(tenantId);
    }

    public DataSource getDataSource(Long tenantId) {
        return dedicatedDataSources.get(tenantId);
    }

    public Map<Long, DataSource> getAllDedicatedDataSources() {
        return Map.copyOf(dedicatedDataSources);
    }
}
