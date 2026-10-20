package com.saasbasics.platform.modules.tenant.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseTenantEntity;

@TableName("plat_tenant_quota")
public class TenantQuotaEntity extends BaseTenantEntity {

    @TableField("quota_code")
    private String quotaCode;

    @TableField("quota_name")
    private String quotaName;

    @TableField("quota_value")
    private Long quotaValue;

    @TableField("used_value")
    private Long usedValue;

    @TableField("alarm_threshold")
    private Long alarmThreshold;

    @TableField("reset_cycle")
    private String resetCycle;

    public String getQuotaCode() {
        return quotaCode;
    }

    public void setQuotaCode(String quotaCode) {
        this.quotaCode = quotaCode;
    }

    public String getQuotaName() {
        return quotaName;
    }

    public void setQuotaName(String quotaName) {
        this.quotaName = quotaName;
    }

    public Long getQuotaValue() {
        return quotaValue;
    }

    public void setQuotaValue(Long quotaValue) {
        this.quotaValue = quotaValue;
    }

    public Long getUsedValue() {
        return usedValue;
    }

    public void setUsedValue(Long usedValue) {
        this.usedValue = usedValue;
    }

    public Long getAlarmThreshold() {
        return alarmThreshold;
    }

    public void setAlarmThreshold(Long alarmThreshold) {
        this.alarmThreshold = alarmThreshold;
    }

    public String getResetCycle() {
        return resetCycle;
    }

    public void setResetCycle(String resetCycle) {
        this.resetCycle = resetCycle;
    }
}
