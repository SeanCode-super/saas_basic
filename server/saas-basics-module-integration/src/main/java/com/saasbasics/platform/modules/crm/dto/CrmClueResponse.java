package com.saasbasics.platform.modules.crm.dto;

import java.time.LocalDateTime;

public class CrmClueResponse {
    private Long id;
    private String name;
    private String contactName;
    private String mobile;
    private String telephone;
    private String email;
    private String address;
    private String source;
    private String status;
    private Long ownerUserId;
    private String ownerUserName;
    private LocalDateTime nextContactTime;
    private Long convertedCustomerId;
    private String remark;
    private LocalDateTime createdAt;

    public CrmClueResponse() {}

    public CrmClueResponse(Long id, String name, String contactName, String mobile, String telephone,
                           String email, String address, String source, String status, Long ownerUserId,
                           String ownerUserName, LocalDateTime nextContactTime, Long convertedCustomerId,
                           String remark, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.contactName = contactName;
        this.mobile = mobile;
        this.telephone = telephone;
        this.email = email;
        this.address = address;
        this.source = source;
        this.status = status;
        this.ownerUserId = ownerUserId;
        this.ownerUserName = ownerUserName;
        this.nextContactTime = nextContactTime;
        this.convertedCustomerId = convertedCustomerId;
        this.remark = remark;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public String getTelephone() { return telephone; }
    public void setTelephone(String telephone) { this.telephone = telephone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getOwnerUserName() { return ownerUserName; }
    public void setOwnerUserName(String ownerUserName) { this.ownerUserName = ownerUserName; }
    public LocalDateTime getNextContactTime() { return nextContactTime; }
    public void setNextContactTime(LocalDateTime nextContactTime) { this.nextContactTime = nextContactTime; }
    public Long getConvertedCustomerId() { return convertedCustomerId; }
    public void setConvertedCustomerId(Long convertedCustomerId) { this.convertedCustomerId = convertedCustomerId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
