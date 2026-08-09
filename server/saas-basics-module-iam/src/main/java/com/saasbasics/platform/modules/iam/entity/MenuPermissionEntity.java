package com.saasbasics.platform.modules.iam.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseTenantEntity;

@TableName("iam_menu_permission")
public class MenuPermissionEntity extends BaseTenantEntity {

    @TableField("menu_id")
    private Long menuId;

    @TableField("menu_code")
    private String menuCode;

    @TableField("subject_type")
    private String subjectType;

    @TableField("subject_value")
    private String subjectValue;

    @TableField("button_codes_json")
    private String buttonCodesJson;

    @TableField("status")
    private String status;

    public Long getMenuId() {
        return menuId;
    }

    public void setMenuId(Long menuId) {
        this.menuId = menuId;
    }

    public String getMenuCode() {
        return menuCode;
    }

    public void setMenuCode(String menuCode) {
        this.menuCode = menuCode;
    }

    public String getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(String subjectType) {
        this.subjectType = subjectType;
    }

    public String getSubjectValue() {
        return subjectValue;
    }

    public void setSubjectValue(String subjectValue) {
        this.subjectValue = subjectValue;
    }

    public String getButtonCodesJson() {
        return buttonCodesJson;
    }

    public void setButtonCodesJson(String buttonCodesJson) {
        this.buttonCodesJson = buttonCodesJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
