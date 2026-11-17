package com.saasbasics.platform.modules.ai.application.dto;

public class AiTestConnectionResult {

    private boolean success;
    private long latencyMillis;
    private String message;
    private String replySnippet;

    public AiTestConnectionResult() {}

    public AiTestConnectionResult(boolean success, long latencyMillis, String message, String replySnippet) {
        this.success = success;
        this.latencyMillis = latencyMillis;
        this.message = message;
        this.replySnippet = replySnippet;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public long getLatencyMillis() { return latencyMillis; }
    public void setLatencyMillis(long latencyMillis) { this.latencyMillis = latencyMillis; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getReplySnippet() { return replySnippet; }
    public void setReplySnippet(String replySnippet) { this.replySnippet = replySnippet; }
}
