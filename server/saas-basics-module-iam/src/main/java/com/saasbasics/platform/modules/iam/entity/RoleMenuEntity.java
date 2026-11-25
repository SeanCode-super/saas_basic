package com.saasbasics.platform.modules.iam.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseEntity;

@TableName("iam_role_menu")
public class RoleMenuEntity extends BaseEntity {

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("role_id")
    private Long roleId;

    @TableField("menu_id")
    private Long menuId;

    public RoleMenuEntity() {
    }

    public RoleMenuEntity(Long tenantId, Long roleId, Long menuId) {
        this.tenantId = tenantId;
        this.roleId = roleId;
        this.menuId = menuId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public Long getMenuId() {
        return menuId;
    }

    public void setMenuId(Long menuId) {
        this.menuId = menuId;
    }

}
