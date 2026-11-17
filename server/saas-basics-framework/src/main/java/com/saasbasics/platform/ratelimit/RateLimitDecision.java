package com.saasbasics.platform.ratelimit;

public record RateLimitDecision(
        boolean allowed,
        int limit,
        int remaining,
        long resetSeconds
) {
}
