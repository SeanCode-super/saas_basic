package com.saasbasics.platform.modules.iam.dto;

import cn.idev.excel.annotation.ExcelProperty;

/**
 * 用户台账 FastExcel 高性能流式导出 VO 模型
 */
public class UserExcelVo {

    @ExcelProperty("用户主键ID")
    private Long id;

    @ExcelProperty("登录账号")
    private String username;

    @ExcelProperty("用户昵称")
    private String nickname;

    @ExcelProperty("工号/编码")
    private String userCode;

    @ExcelProperty("联系手机")
    private String mobile;

    @ExcelProperty("电子邮箱")
    private String email;

    @ExcelProperty("账号状态")
    private String status;

    public UserExcelVo() {
    }

    public UserExcelVo(Long id, String username, String nickname, String userCode, String mobile, String email, String status) {
        this.id = id;
        this.username = username;
        this.nickname = nickname;
        this.userCode = userCode;
        this.mobile = mobile;
        this.email = email;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getUserCode() {
        return userCode;
    }

    public void setUserCode(String userCode) {
        this.userCode = userCode;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
