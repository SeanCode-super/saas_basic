package com.saasbasics.platform.modules.system.devops.dto;

public class AppBuildTriggerRequest {
    private String customBuildCmd;
    private String remark;

    public String getCustomBuildCmd() {
        return customBuildCmd;
    }

    public void setCustomBuildCmd(String customBuildCmd) {
        this.customBuildCmd = customBuildCmd;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
