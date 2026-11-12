package com.saasbasics.platform.modules.ai.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseTenantEntity;
import java.math.BigDecimal;

@TableName("ai_finetune_sample")
public class AiFinetuneSampleEntity extends BaseTenantEntity {

    @TableField("dataset_id")
    private Long datasetId;

    @TableField("instruction")
    private String instruction;

    @TableField("input_text")
    private String inputText;

    @TableField("output_text")
    private String outputText;

    @TableField("source_type")
    private String sourceType;

    @TableField("quality_score")
    private BigDecimal qualityScore;

    @TableField("review_status")
    private String reviewStatus;

    @TableField("desensitized")
    private Integer desensitized;

    public Long getDatasetId() { return datasetId; }
    public void setDatasetId(Long datasetId) { this.datasetId = datasetId; }
    public String getInstruction() { return instruction; }
    public void setInstruction(String instruction) { this.instruction = instruction; }
    public String getInputText() { return inputText; }
    public void setInputText(String inputText) { this.inputText = inputText; }
    public String getOutputText() { return outputText; }
    public void setOutputText(String outputText) { this.outputText = outputText; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public BigDecimal getQualityScore() { return qualityScore; }
    public void setQualityScore(BigDecimal qualityScore) { this.qualityScore = qualityScore; }
    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }
    public Integer getDesensitized() { return desensitized; }
    public void setDesensitized(Integer desensitized) { this.desensitized = desensitized; }
}
