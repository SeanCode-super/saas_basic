package com.saasbasics.platform.modules.ai.domain;

public class AiDomainException extends RuntimeException {

    private final String code;

    public AiDomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
