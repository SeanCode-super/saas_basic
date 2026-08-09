package com.saasbasics.platform.modules.iam.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseTenantEntity;

@TableName("iam_role_api")
public class RoleApiEntity extends BaseTenantEntity {

    @TableField("role_id")
    private Long roleId;

    @TableField("api_resource_id")
    private Long apiResourceId;

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public Long getApiResourceId() {
        return apiResourceId;
    }

    public void setApiResourceId(Long apiResourceId) {
        this.apiResourceId = apiResourceId;
    }
}
