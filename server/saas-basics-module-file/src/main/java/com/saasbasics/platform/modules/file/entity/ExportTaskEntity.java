package com.saasbasics.platform.modules.file.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseTenantEntity;
import java.time.LocalDateTime;

@TableName("sys_export_task")
public class ExportTaskEntity extends BaseTenantEntity {

    @TableField("task_no")
    private String taskNo;

    @TableField("task_name")
    private String taskName;

    @TableField("source_type")
    private String sourceType;

    @TableField("source_code")
    private String sourceCode;

    @TableField("export_format")
    private String exportFormat;

    @TableField("columns_json")
    private String columnsJson;

    @TableField("query_params_json")
    private String queryParamsJson;

    @TableField("watermark_text")
    private String watermarkText;

    @TableField("trace_code")
    private String traceCode;

    @TableField("desensitized")
    private Integer desensitized;

    @TableField("total_rows")
    private Long totalRows;

    @TableField("processed_rows")
    private Long processedRows;

    @TableField("progress_percent")
    private Integer progressPercent;

    @TableField("file_size")
    private Long fileSize;

    @TableField("object_key")
    private String objectKey;

    @TableField("file_sha256")
    private String fileSha256;

    @TableField("status")
    private String status;

    @TableField("approval_status")
    private String approvalStatus;

    @TableField("approved_by")
    private Long approvedBy;

    @TableField("approved_by_name")
    private String approvedByName;

    @TableField("approved_at")
    private LocalDateTime approvedAt;

    @TableField("approve_reason")
    private String approveReason;

    @TableField("error_message")
    private String errorMessage;

    @TableField("expire_at")
    private LocalDateTime expireAt;

    @TableField("operator_user_id")
    private Long operatorUserId;

    @TableField("operator_name")
    private String operatorName;

    public String getTaskNo() { return taskNo; }
    public void setTaskNo(String taskNo) { this.taskNo = taskNo; }
    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
    public String getExportFormat() { return exportFormat; }
    public void setExportFormat(String exportFormat) { this.exportFormat = exportFormat; }
    public String getColumnsJson() { return columnsJson; }
    public void setColumnsJson(String columnsJson) { this.columnsJson = columnsJson; }
    public String getQueryParamsJson() { return queryParamsJson; }
    public void setQueryParamsJson(String queryParamsJson) { this.queryParamsJson = queryParamsJson; }
    public String getWatermarkText() { return watermarkText; }
    public void setWatermarkText(String watermarkText) { this.watermarkText = watermarkText; }
    public String getTraceCode() { return traceCode; }
    public void setTraceCode(String traceCode) { this.traceCode = traceCode; }
    public Integer getDesensitized() { return desensitized; }
    public void setDesensitized(Integer desensitized) { this.desensitized = desensitized; }
    public Long getTotalRows() { return totalRows; }
    public void setTotalRows(Long totalRows) { this.totalRows = totalRows; }
    public Long getProcessedRows() { return processedRows; }
    public void setProcessedRows(Long processedRows) { this.processedRows = processedRows; }
    public Integer getProgressPercent() { return progressPercent; }
    public void setProgressPercent(Integer progressPercent) { this.progressPercent = progressPercent; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public String getFileSha256() { return fileSha256; }
    public void setFileSha256(String fileSha256) { this.fileSha256 = fileSha256; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }
    public Long getApprovedBy() { return approvedBy; }
    public void setApprovedBy(Long approvedBy) { this.approvedBy = approvedBy; }
    public String getApprovedByName() { return approvedByName; }
    public void setApprovedByName(String approvedByName) { this.approvedByName = approvedByName; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
    public String getApproveReason() { return approveReason; }
    public void setApproveReason(String approveReason) { this.approveReason = approveReason; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getExpireAt() { return expireAt; }
    public void setExpireAt(LocalDateTime expireAt) { this.expireAt = expireAt; }
    public Long getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
}
