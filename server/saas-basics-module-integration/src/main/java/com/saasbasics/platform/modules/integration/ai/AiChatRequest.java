package com.saasbasics.platform.modules.integration.ai;

import java.util.List;

public class AiChatRequest {

    private String model;
    private List<ChatMessage> messages;
    private Double temperature;
    private Boolean stream;

    public static class ChatMessage {
        private String role; // system, user, assistant
        private String content;

        public ChatMessage() {}

        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    public String getModel() {
        return model != null && !model.isBlank() ? model : "deepseek-chat";
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
    }

    public Double getTemperature() {
        return temperature != null ? temperature : 0.7;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Boolean getStream() {
        return stream != null ? stream : false;
    }

    public void setStream(Boolean stream) {
        this.stream = stream;
    }
}
