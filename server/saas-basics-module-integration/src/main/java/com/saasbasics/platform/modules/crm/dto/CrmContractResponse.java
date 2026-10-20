package com.saasbasics.platform.modules.crm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class CrmContractResponse {
    private Long id;
    private String contractNo;
    private String name;
    private Long customerId;
    private String customerName;
    private Long businessId;
    private String businessName;
    private BigDecimal totalPrice;
    private LocalDate startTime;
    private LocalDate endTime;
    private String signerUserName;
    private String status;
    private String remark;
    private LocalDateTime createdAt;

    public CrmContractResponse() {}

    public CrmContractResponse(Long id, String contractNo, String name, Long customerId, String customerName,
                               Long businessId, String businessName, BigDecimal totalPrice, LocalDate startTime,
                               LocalDate endTime, String signerUserName, String status, String remark,
                               LocalDateTime createdAt) {
        this.id = id;
        this.contractNo = contractNo;
        this.name = name;
        this.customerId = customerId;
        this.customerName = customerName;
        this.businessId = businessId;
        this.businessName = businessName;
        this.totalPrice = totalPrice;
        this.startTime = startTime;
        this.endTime = endTime;
        this.signerUserName = signerUserName;
        this.status = status;
        this.remark = remark;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getContractNo() { return contractNo; }
    public void setContractNo(String contractNo) { this.contractNo = contractNo; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public Long getBusinessId() { return businessId; }
    public void setBusinessId(Long businessId) { this.businessId = businessId; }
    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }
    public BigDecimal getTotalPrice() { return totalPrice; }
    public void setTotalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; }
    public LocalDate getStartTime() { return startTime; }
    public void setStartTime(LocalDate startTime) { this.startTime = startTime; }
    public LocalDate getEndTime() { return endTime; }
    public void setEndTime(LocalDate endTime) { this.endTime = endTime; }
    public String getSignerUserName() { return signerUserName; }
    public void setSignerUserName(String signerUserName) { this.signerUserName = signerUserName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
