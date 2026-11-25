package com.saasbasics.platform.modules.ai.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseTenantEntity;
import java.math.BigDecimal;

@TableName("ai_rag_eval_run")
public class AiRagEvalRunEntity extends BaseTenantEntity {

    @TableField("public_id")
    private String publicId;
    @TableField("kb_id")
    private Long kbId;
    @TableField("eval_name")
    private String evalName;
    @TableField("top_k")
    private Integer topK;
    @TableField("sample_count")
    private Integer sampleCount;
    @TableField("recall_at_k")
    private BigDecimal recallAtK;
    @TableField("mrr")
    private BigDecimal mrr;
    @TableField("ndcg_at_k")
    private BigDecimal ndcgAtK;
    @TableField("hit_rate")
    private BigDecimal hitRate;
    @TableField("avg_latency_ms")
    private Long avgLatencyMs;
    @TableField("details_json")
    private String detailsJson;
    @TableField("status")
    private String status;

    public String getPublicId() { return publicId; }
    public void setPublicId(String publicId) { this.publicId = publicId; }
    public Long getKbId() { return kbId; }
    public void setKbId(Long kbId) { this.kbId = kbId; }
    public String getEvalName() { return evalName; }
    public void setEvalName(String evalName) { this.evalName = evalName; }
    public Integer getTopK() { return topK; }
    public void setTopK(Integer topK) { this.topK = topK; }
    public Integer getSampleCount() { return sampleCount; }
    public void setSampleCount(Integer sampleCount) { this.sampleCount = sampleCount; }
    public BigDecimal getRecallAtK() { return recallAtK; }
    public void setRecallAtK(BigDecimal recallAtK) { this.recallAtK = recallAtK; }
    public BigDecimal getMrr() { return mrr; }
    public void setMrr(BigDecimal mrr) { this.mrr = mrr; }
    public BigDecimal getNdcgAtK() { return ndcgAtK; }
    public void setNdcgAtK(BigDecimal ndcgAtK) { this.ndcgAtK = ndcgAtK; }
    public BigDecimal getHitRate() { return hitRate; }
    public void setHitRate(BigDecimal hitRate) { this.hitRate = hitRate; }
    public Long getAvgLatencyMs() { return avgLatencyMs; }
    public void setAvgLatencyMs(Long avgLatencyMs) { this.avgLatencyMs = avgLatencyMs; }
    public String getDetailsJson() { return detailsJson; }
    public void setDetailsJson(String detailsJson) { this.detailsJson = detailsJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
