package com.saasbasics.platform.common.security;

/**
 * Runtime enforcement contract for blocking blacklisted IPs, usernames, or tenants
 * during authentication and authenticated API dispatch.
 */
public interface SecurityBlacklistChecker {

    void assertAllowed(Long tenantId, String username, String clientIp);
}
