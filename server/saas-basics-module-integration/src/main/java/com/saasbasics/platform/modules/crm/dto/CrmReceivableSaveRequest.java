package com.saasbasics.platform.modules.crm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class CrmReceivableSaveRequest {
    private Long contractId;
    private BigDecimal price;
    private LocalDate returnTime;
    private String payType;
    private String remark;

    public Long getContractId() { return contractId; }
    public void setContractId(Long contractId) { this.contractId = contractId; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public LocalDate getReturnTime() { return returnTime; }
    public void setReturnTime(LocalDate returnTime) { this.returnTime = returnTime; }
    public String getPayType() { return payType; }
    public void setPayType(String payType) { this.payType = payType; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
