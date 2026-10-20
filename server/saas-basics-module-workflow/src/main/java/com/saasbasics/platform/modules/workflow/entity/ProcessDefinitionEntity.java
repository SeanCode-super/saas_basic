package com.saasbasics.platform.modules.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseTenantEntity;

@TableName("wf_process_definition")
public class ProcessDefinitionEntity extends BaseTenantEntity {

    @TableField("process_code")
    private String processCode;

    @TableField("process_name")
    private String processName;

    @TableField("category")
    private String category;

    @TableField("icon")
    private String icon;

    @TableField("color")
    private String color;

    @TableField("description")
    private String description;

    @TableField("form_schema_json")
    private String formSchemaJson;

    @TableField("flow_nodes_json")
    private String flowNodesJson;

    @TableField("status")
    private String status;

    @TableField("sort_no")
    private Integer sortNo;

    public String getProcessCode() {
        return processCode;
    }

    public void setProcessCode(String processCode) {
        this.processCode = processCode;
    }

    public String getProcessName() {
        return processName;
    }

    public void setProcessName(String processName) {
        this.processName = processName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getFormSchemaJson() {
        return formSchemaJson;
    }

    public void setFormSchemaJson(String formSchemaJson) {
        this.formSchemaJson = formSchemaJson;
    }

    public String getFlowNodesJson() {
        return flowNodesJson;
    }

    public void setFlowNodesJson(String flowNodesJson) {
        this.flowNodesJson = flowNodesJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }
}
