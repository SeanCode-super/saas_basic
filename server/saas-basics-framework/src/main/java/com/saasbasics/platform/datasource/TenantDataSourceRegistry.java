package com.saasbasics.platform.datasource;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

@Component
public class TenantDataSourceRegistry {

    private final Map<Long, DataSource> dedicatedDataSources = new ConcurrentHashMap<>();
    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();

    public void registerDataSource(Long tenantId, DataSource dataSource) {
        if (tenantId != null && dataSource != null) {
            dedicatedDataSources.put(tenantId, dataSource);
            notifyListeners();
        }
    }

    public void unregisterDataSource(Long tenantId) {
        if (tenantId != null && dedicatedDataSources.remove(tenantId) != null) {
            notifyListeners();
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

    public void addChangeListener(Runnable listener) {
        if (listener != null) {
            changeListeners.add(listener);
        }
    }

    private void notifyListeners() {
        for (Runnable listener : changeListeners) {
            listener.run();
        }
    }
}
