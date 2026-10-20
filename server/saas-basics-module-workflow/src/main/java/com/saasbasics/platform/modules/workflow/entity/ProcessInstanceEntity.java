package com.saasbasics.platform.modules.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseTenantEntity;
import java.time.LocalDateTime;

@TableName("wf_process_instance")
public class ProcessInstanceEntity extends BaseTenantEntity {

    @TableField("instance_no")
    private String instanceNo;

    @TableField("definition_id")
    private Long definitionId;

    @TableField("process_code")
    private String processCode;

    @TableField("process_name")
    private String processName;

    @TableField("title")
    private String title;

    @TableField("category")
    private String category;

    @TableField("initiator_id")
    private Long initiatorId;

    @TableField("initiator_name")
    private String initiatorName;

    @TableField("initiator_dept_id")
    private Long initiatorDeptId;

    @TableField("initiator_dept_name")
    private String initiatorDeptName;

    @TableField("form_data_json")
    private String formDataJson;

    @TableField("current_node_id")
    private String currentNodeId;

    @TableField("current_node_name")
    private String currentNodeName;

    @TableField("status")
    private String status;

    @TableField("start_time")
    private LocalDateTime startTime;

    @TableField("end_time")
    private LocalDateTime endTime;

    public String getInstanceNo() {
        return instanceNo;
    }

    public void setInstanceNo(String instanceNo) {
        this.instanceNo = instanceNo;
    }

    public Long getDefinitionId() {
        return definitionId;
    }

    public void setDefinitionId(Long definitionId) {
        this.definitionId = definitionId;
    }

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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Long getInitiatorId() {
        return initiatorId;
    }

    public void setInitiatorId(Long initiatorId) {
        this.initiatorId = initiatorId;
    }

    public String getInitiatorName() {
        return initiatorName;
    }

    public void setInitiatorName(String initiatorName) {
        this.initiatorName = initiatorName;
    }

    public Long getInitiatorDeptId() {
        return initiatorDeptId;
    }

    public void setInitiatorDeptId(Long initiatorDeptId) {
        this.initiatorDeptId = initiatorDeptId;
    }

    public String getInitiatorDeptName() {
        return initiatorDeptName;
    }

    public void setInitiatorDeptName(String initiatorDeptName) {
        this.initiatorDeptName = initiatorDeptName;
    }

    public String getFormDataJson() {
        return formDataJson;
    }

    public void setFormDataJson(String formDataJson) {
        this.formDataJson = formDataJson;
    }

    public String getCurrentNodeId() {
        return currentNodeId;
    }

    public void setCurrentNodeId(String currentNodeId) {
        this.currentNodeId = currentNodeId;
    }

    public String getCurrentNodeName() {
        return currentNodeName;
    }

    public void setCurrentNodeName(String currentNodeName) {
        this.currentNodeName = currentNodeName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }
}
