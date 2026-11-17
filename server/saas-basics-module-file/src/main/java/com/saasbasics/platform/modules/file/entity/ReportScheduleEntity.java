package com.saasbasics.platform.modules.file.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseTenantEntity;
import java.time.LocalDateTime;

@TableName("sys_report_schedule")
public class ReportScheduleEntity extends BaseTenantEntity {

    @TableField("schedule_code")
    private String scheduleCode;

    @TableField("schedule_name")
    private String scheduleName;

    @TableField("source_code")
    private String sourceCode;

    @TableField("export_format")
    private String exportFormat;

    @TableField("fields_json")
    private String fieldsJson;

    @TableField("filters_json")
    private String filtersJson;

    @TableField("frequency")
    private String frequency;

    @TableField("cron_expr")
    private String cronExpr;

    @TableField("run_hour")
    private Integer runHour;

    @TableField("run_minute")
    private Integer runMinute;

    @TableField("include_watermark")
    private Integer includeWatermark;

    @TableField("desensitize_enabled")
    private Integer desensitizeEnabled;

    @TableField("recipient_user_ids")
    private String recipientUserIds;

    @TableField("last_run_at")
    private LocalDateTime lastRunAt;

    @TableField("next_run_at")
    private LocalDateTime nextRunAt;

    @TableField("last_task_id")
    private Long lastTaskId;

    @TableField("status")
    private String status;

    public String getScheduleCode() { return scheduleCode; }
    public void setScheduleCode(String scheduleCode) { this.scheduleCode = scheduleCode; }
    public String getScheduleName() { return scheduleName; }
    public void setScheduleName(String scheduleName) { this.scheduleName = scheduleName; }
    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
    public String getExportFormat() { return exportFormat; }
    public void setExportFormat(String exportFormat) { this.exportFormat = exportFormat; }
    public String getFieldsJson() { return fieldsJson; }
    public void setFieldsJson(String fieldsJson) { this.fieldsJson = fieldsJson; }
    public String getFiltersJson() { return filtersJson; }
    public void setFiltersJson(String filtersJson) { this.filtersJson = filtersJson; }
    public String getFrequency() { return frequency; }
    public void setFrequency(String frequency) { this.frequency = frequency; }
    public String getCronExpr() { return cronExpr; }
    public void setCronExpr(String cronExpr) { this.cronExpr = cronExpr; }
    public Integer getRunHour() { return runHour; }
    public void setRunHour(Integer runHour) { this.runHour = runHour; }
    public Integer getRunMinute() { return runMinute; }
    public void setRunMinute(Integer runMinute) { this.runMinute = runMinute; }
    public Integer getIncludeWatermark() { return includeWatermark; }
    public void setIncludeWatermark(Integer includeWatermark) { this.includeWatermark = includeWatermark; }
    public Integer getDesensitizeEnabled() { return desensitizeEnabled; }
    public void setDesensitizeEnabled(Integer desensitizeEnabled) { this.desensitizeEnabled = desensitizeEnabled; }
    public String getRecipientUserIds() { return recipientUserIds; }
    public void setRecipientUserIds(String recipientUserIds) { this.recipientUserIds = recipientUserIds; }
    public LocalDateTime getLastRunAt() { return lastRunAt; }
    public void setLastRunAt(LocalDateTime lastRunAt) { this.lastRunAt = lastRunAt; }
    public LocalDateTime getNextRunAt() { return nextRunAt; }
    public void setNextRunAt(LocalDateTime nextRunAt) { this.nextRunAt = nextRunAt; }
    public Long getLastTaskId() { return lastTaskId; }
    public void setLastTaskId(Long lastTaskId) { this.lastTaskId = lastTaskId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
