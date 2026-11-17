package com.saasbasics.platform.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "saas-basics.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private int tenantRpm = 1200;
    private int aiTenantRpm = 60;
    private int ipRpm = 600;
    private int windowSeconds = 60;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getTenantRpm() {
        return tenantRpm;
    }

    public void setTenantRpm(int tenantRpm) {
        this.tenantRpm = tenantRpm;
    }

    public int getAiTenantRpm() {
        return aiTenantRpm;
    }

    public void setAiTenantRpm(int aiTenantRpm) {
        this.aiTenantRpm = aiTenantRpm;
    }

    public int getIpRpm() {
        return ipRpm;
    }

    public void setIpRpm(int ipRpm) {
        this.ipRpm = ipRpm;
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(int windowSeconds) {
        this.windowSeconds = windowSeconds;
    }
}
