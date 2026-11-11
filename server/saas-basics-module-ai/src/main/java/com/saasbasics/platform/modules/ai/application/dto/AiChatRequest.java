package com.saasbasics.platform.modules.ai.application.dto;

import java.util.List;

public class AiChatRequest {

    private String model = "deepseek-chat";
    private String promptCode;
    private List<ChatMessage> messages;
    private Boolean stream = true;
    private Double temperature = 0.7;

    public static class ChatMessage {
        private String role;
        private String content;

        public ChatMessage() {}
        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getPromptCode() { return promptCode; }
    public void setPromptCode(String promptCode) { this.promptCode = promptCode; }
    public List<ChatMessage> getMessages() { return messages; }
    public void setMessages(List<ChatMessage> messages) { this.messages = messages; }
    public Boolean getStream() { return stream; }
    public void setStream(Boolean stream) { this.stream = stream; }
    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }
}
