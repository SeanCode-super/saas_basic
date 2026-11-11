package com.saasbasics.platform.modules.ai.domain;

public enum ProviderErrorCategory {
    TIMEOUT,
    RATE_LIMIT,
    AUTH_FAILED,
    CONTENT_SAFETY,
    NON_RETRYABLE,
    PROVIDER_ERROR
}
