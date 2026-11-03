package com.saasbasics.platform.modules.system.devops.dto;

public class AppServiceActionResult {
    private boolean success;
    private String action; // START, STOP, RESTART, KILL, BUILD, ROLLBACK
    private String message;
    private String status;
    private Integer pid;

    public AppServiceActionResult() {
    }

    public AppServiceActionResult(boolean success, String action, String message, String status, Integer pid) {
        this.success = success;
        this.action = action;
        this.message = message;
        this.status = status;
        this.pid = pid;
    }

    public static AppServiceActionResult ok(String action, String message, String status, Integer pid) {
        return new AppServiceActionResult(true, action, message, status, pid);
    }

    public static AppServiceActionResult fail(String action, String message, String status) {
        return new AppServiceActionResult(false, action, message, status, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getPid() {
        return pid;
    }

    public void setPid(Integer pid) {
        this.pid = pid;
    }
}
