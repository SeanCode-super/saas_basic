package com.saasbasics.platform.modules.iam.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseTenantEntity;
import java.time.LocalDateTime;

@TableName("iam_user_person_binding")
public class UserPersonBindingEntity extends BaseTenantEntity {

    @TableField("public_id")
    private String publicId;

    @TableField("user_id")
    private Long userId;

    @TableField("person_public_id")
    private String personPublicId;

    @TableField("status")
    private String status;

    @TableField("valid_from")
    private LocalDateTime validFrom;

    @TableField("valid_to")
    private LocalDateTime validTo;

    public String getPublicId() { return publicId; }
    public void setPublicId(String publicId) { this.publicId = publicId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getPersonPublicId() { return personPublicId; }
    public void setPersonPublicId(String personPublicId) { this.personPublicId = personPublicId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDateTime validFrom) { this.validFrom = validFrom; }
    public LocalDateTime getValidTo() { return validTo; }
    public void setValidTo(LocalDateTime validTo) { this.validTo = validTo; }
}
