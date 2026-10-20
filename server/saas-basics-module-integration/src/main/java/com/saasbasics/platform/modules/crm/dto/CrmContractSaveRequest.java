package com.saasbasics.platform.modules.crm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class CrmContractSaveRequest {
    private String name;
    private Long customerId;
    private Long businessId;
    private BigDecimal totalPrice;
    private LocalDate startTime;
    private LocalDate endTime;
    private String signerUserName;
    private String remark;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public Long getBusinessId() { return businessId; }
    public void setBusinessId(Long businessId) { this.businessId = businessId; }
    public BigDecimal getTotalPrice() { return totalPrice; }
    public void setTotalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; }
    public LocalDate getStartTime() { return startTime; }
    public void setStartTime(LocalDate startTime) { this.startTime = startTime; }
    public LocalDate getEndTime() { return endTime; }
    public void setEndTime(LocalDate endTime) { this.endTime = endTime; }
    public String getSignerUserName() { return signerUserName; }
    public void setSignerUserName(String signerUserName) { this.signerUserName = signerUserName; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
