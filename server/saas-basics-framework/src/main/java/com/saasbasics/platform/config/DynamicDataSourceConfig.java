package com.saasbasics.platform.config;

import com.saasbasics.platform.datasource.DynamicRoutingDataSource;
import com.saasbasics.platform.datasource.TenantDataSourceProperties;
import com.saasbasics.platform.datasource.TenantDataSourceRegistry;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Exposes {@link DynamicRoutingDataSource} as the primary application {@link DataSource},
 * routing VIP tenants with registered dedicated physical pools while falling back to the shared default pool.
 */
@Configuration
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
@EnableConfigurationProperties({DataSourceProperties.class, TenantDataSourceProperties.class})
public class DynamicDataSourceConfig {

    private final List<AutoCloseable> managedPools = new ArrayList<>();

    @Bean(name = "dataSource")
    @Primary
    @ConditionalOnMissingBean(name = "dataSource")
    public DynamicRoutingDataSource dataSource(
            DataSourceProperties defaultProperties,
            ObjectProvider<TenantDataSourceRegistry> registryProvider,
            TenantDataSourceProperties tenantProperties
    ) {
        TenantDataSourceRegistry registry = registryProvider.getIfAvailable(TenantDataSourceRegistry::new);
        DataSource defaultDataSource = defaultProperties.initializeDataSourceBuilder().build();
        trackIfCloseable(defaultDataSource);

        for (Map.Entry<Long, TenantDataSourceProperties.DedicatedDataSourceSpec> entry
                : tenantProperties.getDedicated().entrySet()) {
            Long tenantId = entry.getKey();
            TenantDataSourceProperties.DedicatedDataSourceSpec spec = entry.getValue();
            if (tenantId == null || spec == null || spec.getUrl() == null || spec.getUrl().isBlank()) {
                continue;
            }
            DataSourceBuilder<?> builder = DataSourceBuilder.create().url(spec.getUrl());
            if (spec.getUsername() != null) {
                builder.username(spec.getUsername());
            }
            if (spec.getPassword() != null) {
                builder.password(spec.getPassword());
            }
            if (spec.getDriverClassName() != null && !spec.getDriverClassName().isBlank()) {
                builder.driverClassName(spec.getDriverClassName());
            } else if (defaultProperties.getDriverClassName() != null
                    && !defaultProperties.getDriverClassName().isBlank()) {
                builder.driverClassName(defaultProperties.getDriverClassName());
            }
            DataSource dedicated = builder.build();
            trackIfCloseable(dedicated);
            registry.registerDataSource(tenantId, dedicated);
        }

        DynamicRoutingDataSource routingDataSource = new DynamicRoutingDataSource(registry);
        routingDataSource.setDefaultTargetDataSource(defaultDataSource);
        routingDataSource.refreshTargetDataSources();
        return routingDataSource;
    }

    private void trackIfCloseable(DataSource ds) {
        if (ds instanceof AutoCloseable closeable) {
            managedPools.add(closeable);
        }
    }

    @PreDestroy
    public void closeManagedPools() {
        for (AutoCloseable closeable : managedPools) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // Best-effort cleanup on context shutdown
            }
        }
        managedPools.clear();
    }
}
