package com.saasbasics.platform.modules.system.devops.dto;

import java.time.LocalDateTime;

public class AppServiceDTO {
    private Long id;
    private String serviceCode;
    private String serviceName;
    private String serviceType;
    private String serviceGroup;
    private String driverType;
    private String workDir;
    private String artifactPath;
    private String startCmd;
    private String stopCmd;
    private Integer port;
    private String healthUrl;
    private String logPath;
    private String buildCmd;
    private Integer autoRestart;
    private String status;
    private Integer currentPid;
    private Long uptimeSeconds;
    private LocalDateTime lastStartedAt;
    private LocalDateTime lastStoppedAt;
    private Integer lastExitCode;
    private String lastErrorMsg;
    private String remark;
    private Integer sortNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Live Metrics collected by OSHI
    private Double cpuUsagePercent;
    private Long memoryRssBytes;
    private Integer threadCount;
    private Boolean portListening;
    private Boolean healthOk;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getServiceCode() {
        return serviceCode;
    }

    public void setServiceCode(String serviceCode) {
        this.serviceCode = serviceCode;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public String getServiceGroup() {
        return serviceGroup;
    }

    public void setServiceGroup(String serviceGroup) {
        this.serviceGroup = serviceGroup;
    }

    public String getDriverType() {
        return driverType;
    }

    public void setDriverType(String driverType) {
        this.driverType = driverType;
    }

    public String getWorkDir() {
        return workDir;
    }

    public void setWorkDir(String workDir) {
        this.workDir = workDir;
    }

    public String getArtifactPath() {
        return artifactPath;
    }

    public void setArtifactPath(String artifactPath) {
        this.artifactPath = artifactPath;
    }

    public String getStartCmd() {
        return startCmd;
    }

    public void setStartCmd(String startCmd) {
        this.startCmd = startCmd;
    }

    public String getStopCmd() {
        return stopCmd;
    }

    public void setStopCmd(String stopCmd) {
        this.stopCmd = stopCmd;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getHealthUrl() {
        return healthUrl;
    }

    public void setHealthUrl(String healthUrl) {
        this.healthUrl = healthUrl;
    }

    public String getLogPath() {
        return logPath;
    }

    public void setLogPath(String logPath) {
        this.logPath = logPath;
    }

    public String getBuildCmd() {
        return buildCmd;
    }

    public void setBuildCmd(String buildCmd) {
        this.buildCmd = buildCmd;
    }

    public Integer getAutoRestart() {
        return autoRestart;
    }

    public void setAutoRestart(Integer autoRestart) {
        this.autoRestart = autoRestart;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getCurrentPid() {
        return currentPid;
    }

    public void setCurrentPid(Integer currentPid) {
        this.currentPid = currentPid;
    }

    public Long getUptimeSeconds() {
        return uptimeSeconds;
    }

    public void setUptimeSeconds(Long uptimeSeconds) {
        this.uptimeSeconds = uptimeSeconds;
    }

    public LocalDateTime getLastStartedAt() {
        return lastStartedAt;
    }

    public void setLastStartedAt(LocalDateTime lastStartedAt) {
        this.lastStartedAt = lastStartedAt;
    }

    public LocalDateTime getLastStoppedAt() {
        return lastStoppedAt;
    }

    public void setLastStoppedAt(LocalDateTime lastStoppedAt) {
        this.lastStoppedAt = lastStoppedAt;
    }

    public Integer getLastExitCode() {
        return lastExitCode;
    }

    public void setLastExitCode(Integer lastExitCode) {
        this.lastExitCode = lastExitCode;
    }

    public String getLastErrorMsg() {
        return lastErrorMsg;
    }

    public void setLastErrorMsg(String lastErrorMsg) {
        this.lastErrorMsg = lastErrorMsg;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Double getCpuUsagePercent() {
        return cpuUsagePercent;
    }

    public void setCpuUsagePercent(Double cpuUsagePercent) {
        this.cpuUsagePercent = cpuUsagePercent;
    }

    public Long getMemoryRssBytes() {
        return memoryRssBytes;
    }

    public void setMemoryRssBytes(Long memoryRssBytes) {
        this.memoryRssBytes = memoryRssBytes;
    }

    public Integer getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(Integer threadCount) {
        this.threadCount = threadCount;
    }

    public Boolean getPortListening() {
        return portListening;
    }

    public void setPortListening(Boolean portListening) {
        this.portListening = portListening;
    }

    public Boolean getHealthOk() {
        return healthOk;
    }

    public void setHealthOk(Boolean healthOk) {
        this.healthOk = healthOk;
    }
}
