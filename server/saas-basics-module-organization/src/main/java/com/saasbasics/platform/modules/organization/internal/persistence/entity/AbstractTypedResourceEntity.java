package com.saasbasics.platform.modules.organization.internal.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;

public abstract class AbstractTypedResourceEntity extends AbstractOrganizationResourceEntity {

    @TableField("type_namespace")
    private String typeNamespace;

    @TableField("type_code")
    private String typeCode;

    @TableField("type_version")
    private String typeVersion;

    public String getTypeNamespace() {
        return typeNamespace;
    }

    public void setTypeNamespace(String typeNamespace) {
        this.typeNamespace = typeNamespace;
    }

    public String getTypeCode() {
        return typeCode;
    }

    public void setTypeCode(String typeCode) {
        this.typeCode = typeCode;
    }

    public String getTypeVersion() {
        return typeVersion;
    }

    public void setTypeVersion(String typeVersion) {
        this.typeVersion = typeVersion;
    }
}
