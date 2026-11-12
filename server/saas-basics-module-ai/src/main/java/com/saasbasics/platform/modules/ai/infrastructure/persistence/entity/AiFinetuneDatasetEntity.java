package com.saasbasics.platform.modules.ai.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseTenantEntity;

@TableName("ai_finetune_dataset")
public class AiFinetuneDatasetEntity extends BaseTenantEntity {

    @TableField("dataset_code")
    private String datasetCode;

    @TableField("dataset_name")
    private String datasetName;

    @TableField("task_type")
    private String taskType;

    @TableField("sample_count")
    private Integer sampleCount;

    @TableField("approved_count")
    private Integer approvedCount;

    @TableField("format_type")
    private String formatType;

    @TableField("status")
    private String status;

    public String getDatasetCode() { return datasetCode; }
    public void setDatasetCode(String datasetCode) { this.datasetCode = datasetCode; }
    public String getDatasetName() { return datasetName; }
    public void setDatasetName(String datasetName) { this.datasetName = datasetName; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public Integer getSampleCount() { return sampleCount; }
    public void setSampleCount(Integer sampleCount) { this.sampleCount = sampleCount; }
    public Integer getApprovedCount() { return approvedCount; }
    public void setApprovedCount(Integer approvedCount) { this.approvedCount = approvedCount; }
    public String getFormatType() { return formatType; }
    public void setFormatType(String formatType) { this.formatType = formatType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
