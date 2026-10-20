package com.saasbasics.platform.modules.crm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class CrmBusinessSaveRequest {
    private String name;
    private Long customerId;
    private BigDecimal totalPrice;
    private LocalDate dealDate;
    private String stage;
    private Integer winRate;
    private String lossReason;
    private String remark;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public BigDecimal getTotalPrice() { return totalPrice; }
    public void setTotalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; }
    public LocalDate getDealDate() { return dealDate; }
    public void setDealDate(LocalDate dealDate) { this.dealDate = dealDate; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public Integer getWinRate() { return winRate; }
    public void setWinRate(Integer winRate) { this.winRate = winRate; }
    public String getLossReason() { return lossReason; }
    public void setLossReason(String lossReason) { this.lossReason = lossReason; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
