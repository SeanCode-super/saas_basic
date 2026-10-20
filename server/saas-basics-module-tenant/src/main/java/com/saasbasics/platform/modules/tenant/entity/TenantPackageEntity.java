package com.saasbasics.platform.modules.tenant.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseEntity;

@TableName("plat_tenant_package")
public class TenantPackageEntity extends BaseEntity {

    @TableField("package_code")
    private String packageCode;

    @TableField("package_name")
    private String packageName;

    @TableField("package_type")
    private String packageType;

    @TableField("status")
    private String status;

    @TableField("max_user_count")
    private Integer maxUserCount;

    @TableField("max_dept_count")
    private Integer maxDeptCount;

    @TableField("max_storage_mb")
    private Integer maxStorageMb;

    @TableField("max_api_per_minute")
    private Integer maxApiPerMinute;

    @TableField("feature_flags_json")
    private String featureFlagsJson;

    @TableField("sort_no")
    private Integer sortNo;

    public String getPackageCode() {
        return packageCode;
    }

    public void setPackageCode(String packageCode) {
        this.packageCode = packageCode;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getPackageType() {
        return packageType;
    }

    public void setPackageType(String packageType) {
        this.packageType = packageType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getMaxUserCount() {
        return maxUserCount;
    }

    public void setMaxUserCount(Integer maxUserCount) {
        this.maxUserCount = maxUserCount;
    }

    public Integer getMaxDeptCount() {
        return maxDeptCount;
    }

    public void setMaxDeptCount(Integer maxDeptCount) {
        this.maxDeptCount = maxDeptCount;
    }

    public Integer getMaxStorageMb() {
        return maxStorageMb;
    }

    public void setMaxStorageMb(Integer maxStorageMb) {
        this.maxStorageMb = maxStorageMb;
    }

    public Integer getMaxApiPerMinute() {
        return maxApiPerMinute;
    }

    public void setMaxApiPerMinute(Integer maxApiPerMinute) {
        this.maxApiPerMinute = maxApiPerMinute;
    }

    public String getFeatureFlagsJson() {
        return featureFlagsJson;
    }

    public void setFeatureFlagsJson(String featureFlagsJson) {
        this.featureFlagsJson = featureFlagsJson;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }
}
