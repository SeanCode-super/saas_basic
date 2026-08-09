package com.saasbasics.platform.modules.iam.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseTenantEntity;

@TableName("iam_data_scope")
public class DataScopeEntity extends BaseTenantEntity {

    @TableField("scope_code")
    private String scopeCode;

    @TableField("scope_name")
    private String scopeName;

    @TableField("scope_type")
    private String scopeType;

    @TableField("scope_rule_json")
    private String scopeRuleJson;

    @TableField("status")
    private String status;

    public String getScopeCode() {
        return scopeCode;
    }

    public void setScopeCode(String scopeCode) {
        this.scopeCode = scopeCode;
    }

    public String getScopeName() {
        return scopeName;
    }

    public void setScopeName(String scopeName) {
        this.scopeName = scopeName;
    }

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    public String getScopeRuleJson() {
        return scopeRuleJson;
    }

    public void setScopeRuleJson(String scopeRuleJson) {
        this.scopeRuleJson = scopeRuleJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
