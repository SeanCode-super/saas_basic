package com.saasbasics.platform.modules.file.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseTenantEntity;

@TableName("sys_export_template")
public class ExportTemplateEntity extends BaseTenantEntity {

    @TableField("template_code")
    private String templateCode;

    @TableField("template_name")
    private String templateName;

    @TableField("source_code")
    private String sourceCode;

    @TableField("fields_json")
    private String fieldsJson;

    @TableField("default_format")
    private String defaultFormat;

    @TableField("include_watermark")
    private Integer includeWatermark;

    @TableField("desensitize_enabled")
    private Integer desensitizeEnabled;

    @TableField("status")
    private String status;

    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }
    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }
    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
    public String getFieldsJson() { return fieldsJson; }
    public void setFieldsJson(String fieldsJson) { this.fieldsJson = fieldsJson; }
    public String getDefaultFormat() { return defaultFormat; }
    public void setDefaultFormat(String defaultFormat) { this.defaultFormat = defaultFormat; }
    public Integer getIncludeWatermark() { return includeWatermark; }
    public void setIncludeWatermark(Integer includeWatermark) { this.includeWatermark = includeWatermark; }
    public Integer getDesensitizeEnabled() { return desensitizeEnabled; }
    public void setDesensitizeEnabled(Integer desensitizeEnabled) { this.desensitizeEnabled = desensitizeEnabled; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
