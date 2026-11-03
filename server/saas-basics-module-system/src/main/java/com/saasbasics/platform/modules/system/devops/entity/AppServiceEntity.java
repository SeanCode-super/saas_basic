package com.saasbasics.platform.modules.system.devops.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("sys_app_service")
public class AppServiceEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("service_code")
    private String serviceCode;

    @TableField("service_name")
    private String serviceName;

    @TableField("service_type")
    private String serviceType; // JAR, NODE, DOCKER, SCRIPT

    @TableField("service_group")
    private String serviceGroup; // PLATFORM, BUSINESS, INFRA

    @TableField("driver_type")
    private String driverType; // HOST_PROCESS, DOCKER

    @TableField("work_dir")
    private String workDir;

    @TableField("artifact_path")
    private String artifactPath;

    @TableField("start_cmd")
    private String startCmd;

    @TableField("stop_cmd")
    private String stopCmd;

    @TableField("port")
    private Integer port;

    @TableField("health_url")
    private String healthUrl;

    @TableField("log_path")
    private String logPath;

    @TableField("build_cmd")
    private String buildCmd;

    @TableField("auto_restart")
    private Integer autoRestart;

    @TableField("status")
    private String status; // STOPPED, RUNNING, COMPILING, STARTING, ERROR

    @TableField("current_pid")
    private Integer currentPid;

    @TableField("uptime_seconds")
    private Long uptimeSeconds;

    @TableField("last_started_at")
    private LocalDateTime lastStartedAt;

    @TableField("last_stopped_at")
    private LocalDateTime lastStoppedAt;

    @TableField("last_exit_code")
    private Integer lastExitCode;

    @TableField("last_error_msg")
    private String lastErrorMsg;

    @TableField("remark")
    private String remark;

    @TableField("sort_no")
    private Integer sortNo;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

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
}
