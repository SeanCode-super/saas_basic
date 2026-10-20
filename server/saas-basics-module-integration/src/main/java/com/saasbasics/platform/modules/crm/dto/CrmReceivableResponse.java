package com.saasbasics.platform.modules.crm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class CrmReceivableResponse {
    private Long id;
    private String receivableNo;
    private Long contractId;
    private String contractName;
    private Long customerId;
    private String customerName;
    private BigDecimal price;
    private LocalDate returnTime;
    private String payType;
    private String status;
    private String remark;
    private LocalDateTime createdAt;

    public CrmReceivableResponse() {}

    public CrmReceivableResponse(Long id, String receivableNo, Long contractId, String contractName,
                                 Long customerId, String customerName, BigDecimal price, LocalDate returnTime,
                                 String payType, String status, String remark, LocalDateTime createdAt) {
        this.id = id;
        this.receivableNo = receivableNo;
        this.contractId = contractId;
        this.contractName = contractName;
        this.customerId = customerId;
        this.customerName = customerName;
        this.price = price;
        this.returnTime = returnTime;
        this.payType = payType;
        this.status = status;
        this.remark = remark;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getReceivableNo() { return receivableNo; }
    public void setReceivableNo(String receivableNo) { this.receivableNo = receivableNo; }
    public Long getContractId() { return contractId; }
    public void setContractId(Long contractId) { this.contractId = contractId; }
    public String getContractName() { return contractName; }
    public void setContractName(String contractName) { this.contractName = contractName; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public LocalDate getReturnTime() { return returnTime; }
    public void setReturnTime(LocalDate returnTime) { this.returnTime = returnTime; }
    public String getPayType() { return payType; }
    public void setPayType(String payType) { this.payType = payType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
