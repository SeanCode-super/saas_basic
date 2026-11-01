package com.saasbasics.platform.modules.scheduler.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseTenantEntity;
import java.time.LocalDateTime;

@TableName("sched_job_trigger")
public class JobTriggerEntity extends BaseTenantEntity {

    @TableField("job_id")
    private Long jobId;

    @TableField("trigger_type")
    private String triggerType;

    @TableField("cron_expr")
    private String cronExpr;

    @TableField("fixed_interval_seconds")
    private Integer fixedIntervalSeconds;

    @TableField("next_fire_at")
    private LocalDateTime nextFireAt;

    @TableField("last_fire_at")
    private LocalDateTime lastFireAt;

    @TableField("status")
    private String status;

    public Long getJobId() {
        return jobId;
    }

    public void setJobId(Long jobId) {
        this.jobId = jobId;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public String getCronExpr() {
        return cronExpr;
    }

    public void setCronExpr(String cronExpr) {
        this.cronExpr = cronExpr;
    }

    public Integer getFixedIntervalSeconds() {
        return fixedIntervalSeconds;
    }

    public void setFixedIntervalSeconds(Integer fixedIntervalSeconds) {
        this.fixedIntervalSeconds = fixedIntervalSeconds;
    }

    public LocalDateTime getNextFireAt() {
        return nextFireAt;
    }

    public void setNextFireAt(LocalDateTime nextFireAt) {
        this.nextFireAt = nextFireAt;
    }

    public LocalDateTime getLastFireAt() {
        return lastFireAt;
    }

    public void setLastFireAt(LocalDateTime lastFireAt) {
        this.lastFireAt = lastFireAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
