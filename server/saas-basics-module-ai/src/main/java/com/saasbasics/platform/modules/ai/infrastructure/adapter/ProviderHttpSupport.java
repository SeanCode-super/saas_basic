package com.saasbasics.platform.modules.ai.infrastructure.adapter;

import com.saasbasics.platform.common.security.SsrfProtectionValidator;
import com.saasbasics.platform.modules.ai.domain.ProviderErrorCategory;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.core.env.Environment;

/**
 * Shared HTTP client, SSRF validation, and provider status classification utilities for chat adapters.
 */
public final class ProviderHttpSupport {

    private ProviderHttpSupport() {}

    public static HttpClient defaultClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public static URI validateEndpoint(String rawUrl, Environment environment) {
        boolean allowPrivate = environment == null || !environment.matchesProfiles("prod");
        return SsrfProtectionValidator.validateOutboundUri(rawUrl, allowPrivate);
    }

    public static ProviderErrorCategory classifyStatus(int status) {
        if (status == 401 || status == 403) {
            return ProviderErrorCategory.AUTH_FAILED;
        }
        if (status == 429) {
            return ProviderErrorCategory.RATE_LIMIT;
        }
        if (status == 408 || status == 504) {
            return ProviderErrorCategory.TIMEOUT;
        }
        if (status == 400 || status == 422) {
            return ProviderErrorCategory.NON_RETRYABLE;
        }
        if (status == 451) {
            return ProviderErrorCategory.CONTENT_SAFETY;
        }
        return ProviderErrorCategory.PROVIDER_ERROR;
    }
}
