package com.saasbasics.platform.telemetry;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "saas-basics.telemetry")
public class TelemetryProperties {

    private boolean enabled = true;
    private String serviceName = "saas-basics-server";
    private boolean otlpEnabled = false;
    private String otlpEndpoint = "http://127.0.0.1:4318/v1/traces";
    private int maxRecentTraces = 100;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public boolean isOtlpEnabled() {
        return otlpEnabled;
    }

    public void setOtlpEnabled(boolean otlpEnabled) {
        this.otlpEnabled = otlpEnabled;
    }

    public String getOtlpEndpoint() {
        return otlpEndpoint;
    }

    public void setOtlpEndpoint(String otlpEndpoint) {
        this.otlpEndpoint = otlpEndpoint;
    }

    public int getMaxRecentTraces() {
        return maxRecentTraces;
    }

    public void setMaxRecentTraces(int maxRecentTraces) {
        this.maxRecentTraces = maxRecentTraces;
    }
}
