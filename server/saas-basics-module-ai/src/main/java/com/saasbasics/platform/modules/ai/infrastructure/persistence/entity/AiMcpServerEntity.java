package com.saasbasics.platform.modules.ai.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseTenantEntity;
import java.time.LocalDateTime;

@TableName("ai_mcp_server")
public class AiMcpServerEntity extends BaseTenantEntity {

    private String serverCode;
    private String serverName;
    private String transportType;
    private String endpointUrl;
    private String authHeaderName;
    private String encryptedAuthToken;
    private String allowedToolsJson;
    private String healthStatus;
    private LocalDateTime lastCheckedAt;
    private String status;

    public String getServerCode() { return serverCode; }
    public void setServerCode(String serverCode) { this.serverCode = serverCode; }
    public String getServerName() { return serverName; }
    public void setServerName(String serverName) { this.serverName = serverName; }
    public String getTransportType() { return transportType; }
    public void setTransportType(String transportType) { this.transportType = transportType; }
    public String getEndpointUrl() { return endpointUrl; }
    public void setEndpointUrl(String endpointUrl) { this.endpointUrl = endpointUrl; }
    public String getAuthHeaderName() { return authHeaderName; }
    public void setAuthHeaderName(String authHeaderName) { this.authHeaderName = authHeaderName; }
    public String getEncryptedAuthToken() { return encryptedAuthToken; }
    public void setEncryptedAuthToken(String encryptedAuthToken) { this.encryptedAuthToken = encryptedAuthToken; }
    public String getAllowedToolsJson() { return allowedToolsJson; }
    public void setAllowedToolsJson(String allowedToolsJson) { this.allowedToolsJson = allowedToolsJson; }
    public String getHealthStatus() { return healthStatus; }
    public void setHealthStatus(String healthStatus) { this.healthStatus = healthStatus; }
    public LocalDateTime getLastCheckedAt() { return lastCheckedAt; }
    public void setLastCheckedAt(LocalDateTime lastCheckedAt) { this.lastCheckedAt = lastCheckedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
