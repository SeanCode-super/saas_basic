package com.saasbasics.platform.modules.ai.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseTenantEntity;
import java.math.BigDecimal;

@TableName("ai_billing_reconcile")
public class AiBillingReconcileEntity extends BaseTenantEntity {

    @TableField("public_id")
    private String publicId;
    @TableField("billing_period")
    private String billingPeriod;
    @TableField("run_count")
    private Integer runCount;
    @TableField("ledger_prompt_tokens")
    private Long ledgerPromptTokens;
    @TableField("ledger_completion_tokens")
    private Long ledgerCompletionTokens;
    @TableField("ledger_total_tokens")
    private Long ledgerTotalTokens;
    @TableField("ledger_cost")
    private BigDecimal ledgerCost;
    @TableField("quota_used_tokens")
    private Long quotaUsedTokens;
    @TableField("quota_estimated_cost")
    private BigDecimal quotaEstimatedCost;
    @TableField("token_discrepancy")
    private Long tokenDiscrepancy;
    @TableField("cost_discrepancy")
    private BigDecimal costDiscrepancy;
    @TableField("status")
    private String status;
    @TableField("summary_json")
    private String summaryJson;

    public String getPublicId() { return publicId; }
    public void setPublicId(String publicId) { this.publicId = publicId; }
    public String getBillingPeriod() { return billingPeriod; }
    public void setBillingPeriod(String billingPeriod) { this.billingPeriod = billingPeriod; }
    public Integer getRunCount() { return runCount; }
    public void setRunCount(Integer runCount) { this.runCount = runCount; }
    public Long getLedgerPromptTokens() { return ledgerPromptTokens; }
    public void setLedgerPromptTokens(Long ledgerPromptTokens) { this.ledgerPromptTokens = ledgerPromptTokens; }
    public Long getLedgerCompletionTokens() { return ledgerCompletionTokens; }
    public void setLedgerCompletionTokens(Long ledgerCompletionTokens) { this.ledgerCompletionTokens = ledgerCompletionTokens; }
    public Long getLedgerTotalTokens() { return ledgerTotalTokens; }
    public void setLedgerTotalTokens(Long ledgerTotalTokens) { this.ledgerTotalTokens = ledgerTotalTokens; }
    public BigDecimal getLedgerCost() { return ledgerCost; }
    public void setLedgerCost(BigDecimal ledgerCost) { this.ledgerCost = ledgerCost; }
    public Long getQuotaUsedTokens() { return quotaUsedTokens; }
    public void setQuotaUsedTokens(Long quotaUsedTokens) { this.quotaUsedTokens = quotaUsedTokens; }
    public BigDecimal getQuotaEstimatedCost() { return quotaEstimatedCost; }
    public void setQuotaEstimatedCost(BigDecimal quotaEstimatedCost) { this.quotaEstimatedCost = quotaEstimatedCost; }
    public Long getTokenDiscrepancy() { return tokenDiscrepancy; }
    public void setTokenDiscrepancy(Long tokenDiscrepancy) { this.tokenDiscrepancy = tokenDiscrepancy; }
    public BigDecimal getCostDiscrepancy() { return costDiscrepancy; }
    public void setCostDiscrepancy(BigDecimal costDiscrepancy) { this.costDiscrepancy = costDiscrepancy; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSummaryJson() { return summaryJson; }
    public void setSummaryJson(String summaryJson) { this.summaryJson = summaryJson; }
}
