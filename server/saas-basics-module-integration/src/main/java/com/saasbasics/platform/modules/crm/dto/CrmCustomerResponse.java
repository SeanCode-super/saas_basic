package com.saasbasics.platform.modules.crm.dto;

import java.time.LocalDateTime;

public class CrmCustomerResponse {
    private Long id;
    private String customerCode;
    private String name;
    private String industry;
    private String level;
    private String source;
    private String mobile;
    private String telephone;
    private String email;
    private String address;
    private Integer dealStatus;
    private Integer isPool;
    private Integer lockStatus;
    private Long ownerUserId;
    private String ownerUserName;
    private LocalDateTime lastContactTime;
    private LocalDateTime nextContactTime;
    private String remark;
    private LocalDateTime createdAt;

    public CrmCustomerResponse() {}

    public CrmCustomerResponse(Long id, String customerCode, String name, String industry, String level,
                               String source, String mobile, String telephone, String email, String address,
                               Integer dealStatus, Integer isPool, Integer lockStatus, Long ownerUserId,
                               String ownerUserName, LocalDateTime lastContactTime, LocalDateTime nextContactTime,
                               String remark, LocalDateTime createdAt) {
        this.id = id;
        this.customerCode = customerCode;
        this.name = name;
        this.industry = industry;
        this.level = level;
        this.source = source;
        this.mobile = mobile;
        this.telephone = telephone;
        this.email = email;
        this.address = address;
        this.dealStatus = dealStatus;
        this.isPool = isPool;
        this.lockStatus = lockStatus;
        this.ownerUserId = ownerUserId;
        this.ownerUserName = ownerUserName;
        this.lastContactTime = lastContactTime;
        this.nextContactTime = nextContactTime;
        this.remark = remark;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCustomerCode() { return customerCode; }
    public void setCustomerCode(String customerCode) { this.customerCode = customerCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public String getTelephone() { return telephone; }
    public void setTelephone(String telephone) { this.telephone = telephone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public Integer getDealStatus() { return dealStatus; }
    public void setDealStatus(Integer dealStatus) { this.dealStatus = dealStatus; }
    public Integer getIsPool() { return isPool; }
    public void setIsPool(Integer isPool) { this.isPool = isPool; }
    public Integer getLockStatus() { return lockStatus; }
    public void setLockStatus(Integer lockStatus) { this.lockStatus = lockStatus; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getOwnerUserName() { return ownerUserName; }
    public void setOwnerUserName(String ownerUserName) { this.ownerUserName = ownerUserName; }
    public LocalDateTime getLastContactTime() { return lastContactTime; }
    public void setLastContactTime(LocalDateTime lastContactTime) { this.lastContactTime = lastContactTime; }
    public LocalDateTime getNextContactTime() { return nextContactTime; }
    public void setNextContactTime(LocalDateTime nextContactTime) { this.nextContactTime = nextContactTime; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
