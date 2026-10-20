package com.saasbasics.platform.modules.integration.ai;

public class AiChatResponse {

    private String id;
    private String model;
    private String reply;
    private int promptTokens;
    private int completionTokens;
    private long costMillis;

    public AiChatResponse() {}

    public AiChatResponse(String id, String model, String reply, int promptTokens, int completionTokens, long costMillis) {
        this.id = id;
        this.model = model;
        this.reply = reply;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.costMillis = costMillis;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(int promptTokens) {
        this.promptTokens = promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(int completionTokens) {
        this.completionTokens = completionTokens;
    }

    public long getCostMillis() {
        return costMillis;
    }

    public void setCostMillis(long costMillis) {
        this.costMillis = costMillis;
    }
}
