package com.saasbasics.platform.modules.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("sys_api_endpoint_stat")
public class ApiEndpointStatEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String endpointKey;
    private String httpMethod;
    private String pathPattern;
    private String moduleCode;
    private Long totalCalls;
    private Long encryptedCalls;
    private Long plaintextCalls;
    private Long rejectedCalls;
    private Long errorCalls;
    private Long totalDurationMs;
    private Double p95LatencyMs;
    private Double p99LatencyMs;
    private Long lastCalledAtMs;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEndpointKey() { return endpointKey; }
    public void setEndpointKey(String endpointKey) { this.endpointKey = endpointKey; }
    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }
    public String getPathPattern() { return pathPattern; }
    public void setPathPattern(String pathPattern) { this.pathPattern = pathPattern; }
    public String getModuleCode() { return moduleCode; }
    public void setModuleCode(String moduleCode) { this.moduleCode = moduleCode; }
    public Long getTotalCalls() { return totalCalls; }
    public void setTotalCalls(Long totalCalls) { this.totalCalls = totalCalls; }
    public Long getEncryptedCalls() { return encryptedCalls; }
    public void setEncryptedCalls(Long encryptedCalls) { this.encryptedCalls = encryptedCalls; }
    public Long getPlaintextCalls() { return plaintextCalls; }
    public void setPlaintextCalls(Long plaintextCalls) { this.plaintextCalls = plaintextCalls; }
    public Long getRejectedCalls() { return rejectedCalls; }
    public void setRejectedCalls(Long rejectedCalls) { this.rejectedCalls = rejectedCalls; }
    public Long getErrorCalls() { return errorCalls; }
    public void setErrorCalls(Long errorCalls) { this.errorCalls = errorCalls; }
    public Long getTotalDurationMs() { return totalDurationMs; }
    public void setTotalDurationMs(Long totalDurationMs) { this.totalDurationMs = totalDurationMs; }
    public Double getP95LatencyMs() { return p95LatencyMs; }
    public void setP95LatencyMs(Double p95LatencyMs) { this.p95LatencyMs = p95LatencyMs; }
    public Double getP99LatencyMs() { return p99LatencyMs; }
    public void setP99LatencyMs(Double p99LatencyMs) { this.p99LatencyMs = p99LatencyMs; }
    public Long getLastCalledAtMs() { return lastCalledAtMs; }
    public void setLastCalledAtMs(Long lastCalledAtMs) { this.lastCalledAtMs = lastCalledAtMs; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
