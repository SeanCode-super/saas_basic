package com.saasbasics.platform.modules.ai.application.dto;

public class AiChatResponse {

    private String id;
    private String model;
    private String reply;
    private Integer promptTokens;
    private Integer completionTokens;
    private Long costMillis;

    public AiChatResponse() {}

    public AiChatResponse(String id, String model, String reply, Integer promptTokens, Integer completionTokens, Long costMillis) {
        this.id = id;
        this.model = model;
        this.reply = reply;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.costMillis = costMillis;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }
    public Integer getPromptTokens() { return promptTokens; }
    public void setPromptTokens(Integer promptTokens) { this.promptTokens = promptTokens; }
    public Integer getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(Integer completionTokens) { this.completionTokens = completionTokens; }
    public Long getCostMillis() { return costMillis; }
    public void setCostMillis(Long costMillis) { this.costMillis = costMillis; }
}
