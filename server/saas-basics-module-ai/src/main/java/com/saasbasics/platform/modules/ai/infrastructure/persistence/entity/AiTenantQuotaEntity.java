package com.saasbasics.platform.modules.ai.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseTenantEntity;
import java.math.BigDecimal;

@TableName("ai_tenant_quota")
public class AiTenantQuotaEntity extends BaseTenantEntity {

    private Long monthlyTokenLimit;
    private Long usedTokensMonth;
    private Long promptTokensMonth;
    private Long completionTokensMonth;
    private BigDecimal estimatedCostMonth;
    private String billingMonth;
    private Integer rpmLimit;
    private String status;

    public Long getMonthlyTokenLimit() { return monthlyTokenLimit; }
    public void setMonthlyTokenLimit(Long monthlyTokenLimit) { this.monthlyTokenLimit = monthlyTokenLimit; }
    public Long getUsedTokensMonth() { return usedTokensMonth; }
    public void setUsedTokensMonth(Long usedTokensMonth) { this.usedTokensMonth = usedTokensMonth; }
    public Long getPromptTokensMonth() { return promptTokensMonth; }
    public void setPromptTokensMonth(Long promptTokensMonth) { this.promptTokensMonth = promptTokensMonth; }
    public Long getCompletionTokensMonth() { return completionTokensMonth; }
    public void setCompletionTokensMonth(Long completionTokensMonth) { this.completionTokensMonth = completionTokensMonth; }
    public BigDecimal getEstimatedCostMonth() { return estimatedCostMonth; }
    public void setEstimatedCostMonth(BigDecimal estimatedCostMonth) { this.estimatedCostMonth = estimatedCostMonth; }
    public String getBillingMonth() { return billingMonth; }
    public void setBillingMonth(String billingMonth) { this.billingMonth = billingMonth; }
    public Integer getRpmLimit() { return rpmLimit; }
    public void setRpmLimit(Integer rpmLimit) { this.rpmLimit = rpmLimit; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
