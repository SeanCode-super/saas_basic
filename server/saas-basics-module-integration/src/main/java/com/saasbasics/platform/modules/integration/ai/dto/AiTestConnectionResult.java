package com.saasbasics.platform.modules.integration.ai.dto;

public class AiTestConnectionResult {
    private boolean success;
    private long latencyMs;
    private String message;
    private String replySample;

    public AiTestConnectionResult() {}

    public AiTestConnectionResult(boolean success, long latencyMs, String message, String replySample) {
        this.success = success;
        this.latencyMs = latencyMs;
        this.message = message;
        this.replySample = replySample;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getReplySample() {
        return replySample;
    }

    public void setReplySample(String replySample) {
        this.replySample = replySample;
    }
}
