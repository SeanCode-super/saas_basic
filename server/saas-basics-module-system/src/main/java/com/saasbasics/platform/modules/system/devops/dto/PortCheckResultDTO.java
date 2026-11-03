package com.saasbasics.platform.modules.system.devops.dto;

public class PortCheckResultDTO {

    private int port;
    private boolean available;
    private boolean occupiedBySystem;
    private boolean occupiedByRegisteredService;
    private String registeredServiceName;
    private String registeredServiceCode;
    private String message;

    public static PortCheckResultDTO available(int port) {
        PortCheckResultDTO dto = new PortCheckResultDTO();
        dto.setPort(port);
        dto.setAvailable(true);
        dto.setMessage("端口空闲可用");
        return dto;
    }

    public static PortCheckResultDTO occupiedByService(int port, String name, String code) {
        PortCheckResultDTO dto = new PortCheckResultDTO();
        dto.setPort(port);
        dto.setAvailable(false);
        dto.setOccupiedByRegisteredService(true);
        dto.setRegisteredServiceName(name);
        dto.setRegisteredServiceCode(code);
        dto.setMessage("已分配给平台纳管服务: " + name + " (" + code + ")");
        return dto;
    }

    public static PortCheckResultDTO occupiedBySystem(int port) {
        PortCheckResultDTO dto = new PortCheckResultDTO();
        dto.setPort(port);
        dto.setAvailable(false);
        dto.setOccupiedBySystem(true);
        dto.setMessage("当前已被操作系统本地其他进程监听占用");
        return dto;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public boolean isOccupiedBySystem() {
        return occupiedBySystem;
    }

    public void setOccupiedBySystem(boolean occupiedBySystem) {
        this.occupiedBySystem = occupiedBySystem;
    }

    public boolean isOccupiedByRegisteredService() {
        return occupiedByRegisteredService;
    }

    public void setOccupiedByRegisteredService(boolean occupiedByRegisteredService) {
        this.occupiedByRegisteredService = occupiedByRegisteredService;
    }

    public String getRegisteredServiceName() {
        return registeredServiceName;
    }

    public void setRegisteredServiceName(String registeredServiceName) {
        this.registeredServiceName = registeredServiceName;
    }

    public String getRegisteredServiceCode() {
        return registeredServiceCode;
    }

    public void setRegisteredServiceCode(String registeredServiceCode) {
        this.registeredServiceCode = registeredServiceCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
