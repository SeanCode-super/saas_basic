package com.saasbasics.platform.modules.crm.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

public class CrmFollowUpRecordSaveRequest {
    private String bizType;
    private Long bizId;
    @JsonAlias({"contactType", "follow_type", "contact_type"})
    private String followType;
    private String content;

    @JsonAlias({"nextFollowTime", "next_contact_time", "next_follow_time"})
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime nextContactTime;

    public String getBizType() { return bizType; }
    public void setBizType(String bizType) { this.bizType = bizType; }
    public Long getBizId() { return bizId; }
    public void setBizId(Long bizId) { this.bizId = bizId; }
    public String getFollowType() { return followType; }
    public void setFollowType(String followType) { this.followType = followType; }
    public String getContactType() { return followType; }
    public void setContactType(String contactType) { this.followType = contactType; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LocalDateTime getNextContactTime() { return nextContactTime; }
    public void setNextContactTime(LocalDateTime nextContactTime) { this.nextContactTime = nextContactTime; }
    public LocalDateTime getNextFollowTime() { return nextContactTime; }
    public void setNextFollowTime(LocalDateTime nextFollowTime) { this.nextContactTime = nextFollowTime; }
}
