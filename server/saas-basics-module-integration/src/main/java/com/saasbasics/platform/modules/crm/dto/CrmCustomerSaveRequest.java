package com.saasbasics.platform.modules.crm.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

public class CrmCustomerSaveRequest {
    private String name;
    private String industry;
    private String level;
    private String source;
    private String mobile;
    private String telephone;
    private String email;
    private String address;
    private Integer dealStatus;
    private Integer lockStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime nextContactTime;
    private String remark;

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
    public Integer getLockStatus() { return lockStatus; }
    public void setLockStatus(Integer lockStatus) { this.lockStatus = lockStatus; }
    public LocalDateTime getNextContactTime() { return nextContactTime; }
    public void setNextContactTime(LocalDateTime nextContactTime) { this.nextContactTime = nextContactTime; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
