package com.saasbasics.platform.modules.projectapp;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("cg_project_action_receipt")
public class ProjectActionReceipt {
    @TableId(type=IdType.AUTO) public Long id;
    public Long tenantId;
    public Long userId;
    public String appCode;
    public String requestId;
    public String inputHash;
    public Boolean completed;
    public LocalDateTime completedAt;
}
