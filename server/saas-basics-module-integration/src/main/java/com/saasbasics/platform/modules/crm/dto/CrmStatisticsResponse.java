package com.saasbasics.platform.modules.crm.dto;

import java.math.BigDecimal;

public class CrmStatisticsResponse {
    private Long totalCustomerCount;
    private Long poolCustomerCount;
    private Long totalClueCount;
    private BigDecimal totalBusinessAmount;
    private BigDecimal contractWonAmount;
    private BigDecimal receivableReceivedAmount;
    private BigDecimal receivablePendingAmount;

    public CrmStatisticsResponse() {}

    public CrmStatisticsResponse(Long totalCustomerCount, Long poolCustomerCount, Long totalClueCount,
                                 BigDecimal totalBusinessAmount, BigDecimal contractWonAmount,
                                 BigDecimal receivableReceivedAmount, BigDecimal receivablePendingAmount) {
        this.totalCustomerCount = totalCustomerCount;
        this.poolCustomerCount = poolCustomerCount;
        this.totalClueCount = totalClueCount;
        this.totalBusinessAmount = totalBusinessAmount;
        this.contractWonAmount = contractWonAmount;
        this.receivableReceivedAmount = receivableReceivedAmount;
        this.receivablePendingAmount = receivablePendingAmount;
    }

    public Long getTotalCustomerCount() { return totalCustomerCount; }
    public void setTotalCustomerCount(Long totalCustomerCount) { this.totalCustomerCount = totalCustomerCount; }
    public Long getPoolCustomerCount() { return poolCustomerCount; }
    public void setPoolCustomerCount(Long poolCustomerCount) { this.poolCustomerCount = poolCustomerCount; }
    public Long getTotalClueCount() { return totalClueCount; }
    public void setTotalClueCount(Long totalClueCount) { this.totalClueCount = totalClueCount; }
    public BigDecimal getTotalBusinessAmount() { return totalBusinessAmount; }
    public void setTotalBusinessAmount(BigDecimal totalBusinessAmount) { this.totalBusinessAmount = totalBusinessAmount; }
    public BigDecimal getContractWonAmount() { return contractWonAmount; }
    public void setContractWonAmount(BigDecimal contractWonAmount) { this.contractWonAmount = contractWonAmount; }
    public BigDecimal getReceivableReceivedAmount() { return receivableReceivedAmount; }
    public void setReceivableReceivedAmount(BigDecimal receivableReceivedAmount) { this.receivableReceivedAmount = receivableReceivedAmount; }
    public BigDecimal getReceivablePendingAmount() { return receivablePendingAmount; }
    public void setReceivablePendingAmount(BigDecimal receivablePendingAmount) { this.receivablePendingAmount = receivablePendingAmount; }
}
