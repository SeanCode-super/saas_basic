package com.saasbasics.platform.security.transport;

/**
 * 传输加密协议与校验异常（用于在过滤器层映射准确 HTTP 状态码与可控错误码）。
 */
public class TransportCryptoException extends RuntimeException {

    private final int httpStatus;
    private final String code;

    public TransportCryptoException(int httpStatus, String code, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getCode() {
        return code;
    }
}
