package com.saasbasics.platform.modules.integration.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseTenantEntity;

import java.math.BigDecimal;

@TableName("pay_refund")
public class PayRefundEntity extends BaseTenantEntity {

    @TableField("refund_no")
    private String refundNo;

    @TableField("pay_trade_no")
    private String payTradeNo;

    @TableField("refund_amount")
    private BigDecimal refundAmount;

    @TableField("reason")
    private String reason;

    @TableField("status")
    private String status;

    public String getRefundNo() {
        return refundNo;
    }

    public void setRefundNo(String refundNo) {
        this.refundNo = refundNo;
    }

    public String getPayTradeNo() {
        return payTradeNo;
    }

    public void setPayTradeNo(String payTradeNo) {
        this.payTradeNo = payTradeNo;
    }

    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    public void setRefundAmount(BigDecimal refundAmount) {
        this.refundAmount = refundAmount;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
