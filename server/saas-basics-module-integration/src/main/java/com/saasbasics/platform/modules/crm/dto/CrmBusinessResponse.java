package com.saasbasics.platform.modules.crm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class CrmBusinessResponse {
    private Long id;
    private String businessCode;
    private String name;
    private Long customerId;
    private String customerName;
    private BigDecimal totalPrice;
    private LocalDate dealDate;
    private String stage;
    private Integer winRate;
    private String lossReason;
    private Long ownerUserId;
    private String ownerUserName;
    private String remark;
    private LocalDateTime createdAt;

    public CrmBusinessResponse() {}

    public CrmBusinessResponse(Long id, String businessCode, String name, Long customerId, String customerName,
                               BigDecimal totalPrice, LocalDate dealDate, String stage, Integer winRate,
                               String lossReason, Long ownerUserId, String ownerUserName, String remark,
                               LocalDateTime createdAt) {
        this.id = id;
        this.businessCode = businessCode;
        this.name = name;
        this.customerId = customerId;
        this.customerName = customerName;
        this.totalPrice = totalPrice;
        this.dealDate = dealDate;
        this.stage = stage;
        this.winRate = winRate;
        this.lossReason = lossReason;
        this.ownerUserId = ownerUserId;
        this.ownerUserName = ownerUserName;
        this.remark = remark;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getBusinessCode() { return businessCode; }
    public void setBusinessCode(String businessCode) { this.businessCode = businessCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
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
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getOwnerUserName() { return ownerUserName; }
    public void setOwnerUserName(String ownerUserName) { this.ownerUserName = ownerUserName; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
