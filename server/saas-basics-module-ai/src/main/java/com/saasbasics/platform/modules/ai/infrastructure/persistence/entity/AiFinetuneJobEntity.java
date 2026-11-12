package com.saasbasics.platform.modules.ai.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseTenantEntity;
import java.math.BigDecimal;

@TableName("ai_finetune_job")
public class AiFinetuneJobEntity extends BaseTenantEntity {

    @TableField("job_code")
    private String jobCode;

    @TableField("job_name")
    private String jobName;

    @TableField("dataset_id")
    private Long datasetId;

    @TableField("base_model")
    private String baseModel;

    @TableField("tuning_method")
    private String tuningMethod;

    @TableField("epochs")
    private Integer epochs;

    @TableField("learning_rate")
    private String learningRate;

    @TableField("lora_rank")
    private Integer loraRank;

    @TableField("status")
    private String status;

    @TableField("progress_percent")
    private Integer progressPercent;

    @TableField("train_loss")
    private BigDecimal trainLoss;

    @TableField("eval_accuracy")
    private BigDecimal evalAccuracy;

    @TableField("baseline_accuracy")
    private BigDecimal baselineAccuracy;

    @TableField("output_model_id")
    private String outputModelId;

    @TableField("metrics_json")
    private String metricsJson;

    public String getJobCode() { return jobCode; }
    public void setJobCode(String jobCode) { this.jobCode = jobCode; }
    public String getJobName() { return jobName; }
    public void setJobName(String jobName) { this.jobName = jobName; }
    public Long getDatasetId() { return datasetId; }
    public void setDatasetId(Long datasetId) { this.datasetId = datasetId; }
    public String getBaseModel() { return baseModel; }
    public void setBaseModel(String baseModel) { this.baseModel = baseModel; }
    public String getTuningMethod() { return tuningMethod; }
    public void setTuningMethod(String tuningMethod) { this.tuningMethod = tuningMethod; }
    public Integer getEpochs() { return epochs; }
    public void setEpochs(Integer epochs) { this.epochs = epochs; }
    public String getLearningRate() { return learningRate; }
    public void setLearningRate(String learningRate) { this.learningRate = learningRate; }
    public Integer getLoraRank() { return loraRank; }
    public void setLoraRank(Integer loraRank) { this.loraRank = loraRank; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getProgressPercent() { return progressPercent; }
    public void setProgressPercent(Integer progressPercent) { this.progressPercent = progressPercent; }
    public BigDecimal getTrainLoss() { return trainLoss; }
    public void setTrainLoss(BigDecimal trainLoss) { this.trainLoss = trainLoss; }
    public BigDecimal getEvalAccuracy() { return evalAccuracy; }
    public void setEvalAccuracy(BigDecimal evalAccuracy) { this.evalAccuracy = evalAccuracy; }
    public BigDecimal getBaselineAccuracy() { return baselineAccuracy; }
    public void setBaselineAccuracy(BigDecimal baselineAccuracy) { this.baselineAccuracy = baselineAccuracy; }
    public String getOutputModelId() { return outputModelId; }
    public void setOutputModelId(String outputModelId) { this.outputModelId = outputModelId; }
    public String getMetricsJson() { return metricsJson; }
    public void setMetricsJson(String metricsJson) { this.metricsJson = metricsJson; }
}
