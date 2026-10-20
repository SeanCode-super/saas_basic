package com.saasbasics.platform.modules.crm.dto;

import java.time.LocalDateTime;

public class CrmFollowUpRecordResponse {
    private Long id;
    private String bizType;
    private Long bizId;
    private String followType;
    private String content;
    private LocalDateTime nextContactTime;
    private String contactUserName;
    private LocalDateTime createdAt;

    public CrmFollowUpRecordResponse() {}

    public CrmFollowUpRecordResponse(Long id, String bizType, Long bizId, String followType,
                                     String content, LocalDateTime nextContactTime, String contactUserName,
                                     LocalDateTime createdAt) {
        this.id = id;
        this.bizType = bizType;
        this.bizId = bizId;
        this.followType = followType;
        this.content = content;
        this.nextContactTime = nextContactTime;
        this.contactUserName = contactUserName;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getBizType() { return bizType; }
    public void setBizType(String bizType) { this.bizType = bizType; }
    public Long getBizId() { return bizId; }
    public void setBizId(Long bizId) { this.bizId = bizId; }
    public String getFollowType() { return followType; }
    public void setFollowType(String followType) { this.followType = followType; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LocalDateTime getNextContactTime() { return nextContactTime; }
    public void setNextContactTime(LocalDateTime nextContactTime) { this.nextContactTime = nextContactTime; }
    public String getContactUserName() { return contactUserName; }
    public void setContactUserName(String contactUserName) { this.contactUserName = contactUserName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
