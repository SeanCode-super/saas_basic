package com.saasbasics.platform.modules.ai.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseTenantEntity;

@TableName("ai_prompt")
public class AiPromptEntity extends BaseTenantEntity {

    private String promptCode;
    private String promptName;
    private String category;
    private Integer activeVersion;
    private String status;

    public String getPromptCode() { return promptCode; }
    public void setPromptCode(String promptCode) { this.promptCode = promptCode; }
    public String getPromptName() { return promptName; }
    public void setPromptName(String promptName) { this.promptName = promptName; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Integer getActiveVersion() { return activeVersion; }
    public void setActiveVersion(Integer activeVersion) { this.activeVersion = activeVersion; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
