package com.saasbasics.platform.datasource;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for tenant-dedicated physical datasources routed by {@link DynamicRoutingDataSource}.
 */
@ConfigurationProperties(prefix = "saas-basics.datasource")
public class TenantDataSourceProperties {

    private Map<Long, DedicatedDataSourceSpec> dedicated = new LinkedHashMap<>();

    public Map<Long, DedicatedDataSourceSpec> getDedicated() {
        return dedicated;
    }

    public void setDedicated(Map<Long, DedicatedDataSourceSpec> dedicated) {
        this.dedicated = dedicated == null ? new LinkedHashMap<>() : dedicated;
    }

    public static class DedicatedDataSourceSpec {
        private String url;
        private String username;
        private String password;
        private String driverClassName;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDriverClassName() {
            return driverClassName;
        }

        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
        }
    }
}
