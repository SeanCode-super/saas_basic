package com.saasbasics.platform.modules.system.devops.driver;

public class ServiceRuntimeInfo {
    private String status; // RUNNING, STOPPED, STARTING, ERROR
    private Integer pid;
    private Long uptimeSeconds;
    private Double cpuUsagePercent;
    private Long memoryRssBytes;
    private Integer threadCount;
    private Boolean portListening;
    private Boolean healthOk;
    private String errorMsg;

    public ServiceRuntimeInfo() {
    }

    public static ServiceRuntimeInfo stopped() {
        ServiceRuntimeInfo info = new ServiceRuntimeInfo();
        info.setStatus("STOPPED");
        info.setPortListening(false);
        info.setHealthOk(false);
        return info;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getPid() {
        return pid;
    }

    public void setPid(Integer pid) {
        this.pid = pid;
    }

    public Long getUptimeSeconds() {
        return uptimeSeconds;
    }

    public void setUptimeSeconds(Long uptimeSeconds) {
        this.uptimeSeconds = uptimeSeconds;
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

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }
}
