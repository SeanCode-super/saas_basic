package com.saasbasics.platform.modules.scheduler.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseTenantEntity;

@TableName("sched_job_alarm")
public class JobAlarmEntity extends BaseTenantEntity {

    @TableField("job_id")
    private Long jobId;

    @TableField("alarm_code")
    private String alarmCode;

    @TableField("alarm_name")
    private String alarmName;

    @TableField("channel_type")
    private String channelType;

    @TableField("trigger_rule")
    private String triggerRule;

    @TableField("receiver_json")
    private String receiverJson;

    @TableField("template_code")
    private String templateCode;

    @TableField("silence_minutes")
    private Integer silenceMinutes;

    @TableField("status")
    private String status;

    public Long getJobId() { return jobId; }
    public void setJobId(Long jobId) { this.jobId = jobId; }
    public String getAlarmCode() { return alarmCode; }
    public void setAlarmCode(String alarmCode) { this.alarmCode = alarmCode; }
    public String getAlarmName() { return alarmName; }
    public void setAlarmName(String alarmName) { this.alarmName = alarmName; }
    public String getChannelType() { return channelType; }
    public void setChannelType(String channelType) { this.channelType = channelType; }
    public String getTriggerRule() { return triggerRule; }
    public void setTriggerRule(String triggerRule) { this.triggerRule = triggerRule; }
    public String getReceiverJson() { return receiverJson; }
    public void setReceiverJson(String receiverJson) { this.receiverJson = receiverJson; }
    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }
    public Integer getSilenceMinutes() { return silenceMinutes; }
    public void setSilenceMinutes(Integer silenceMinutes) { this.silenceMinutes = silenceMinutes; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
