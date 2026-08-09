package com.saasbasics.platform.common.tenant;

import java.util.Objects;

public final class TenantAccessContext {

    public enum Mode {
        TENANT,
        SYSTEM_BYPASS,
        PLATFORM_BYPASS
    }

    public enum BypassOperation {
        AUTHENTICATION_TOKEN_RESOLUTION,
        PUBLIC_PORTAL_RESOLUTION,
        TENANT_BOOTSTRAP,
        PLATFORM_TENANT_ADMINISTRATION
    }

    private final Mode mode;
    private final Long tenantId;
    private final String tenantCode;
    private final Long actorId;
    private final BypassOperation bypassOperation;
    private final String reason;

    private TenantAccessContext(Mode mode,
                                Long tenantId,
                                String tenantCode,
                                Long actorId,
                                BypassOperation bypassOperation,
                                String reason) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.tenantId = tenantId;
        this.tenantCode = tenantCode;
        this.actorId = actorId;
        this.bypassOperation = bypassOperation;
        this.reason = reason;
    }

    static TenantAccessContext tenant(Long tenantId, String tenantCode, Long actorId) {
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalArgumentException("tenantId must be positive");
        }
        if (tenantCode == null || tenantCode.isBlank()) {
            throw new IllegalArgumentException("tenantCode must not be blank");
        }
        return new TenantAccessContext(Mode.TENANT, tenantId, tenantCode, actorId, null, null);
    }

    static TenantAccessContext bypass(Mode mode,
                                      Long actorId,
                                      BypassOperation operation,
                                      String reason) {
        if (mode == Mode.TENANT) {
            throw new IllegalArgumentException("A bypass context requires a bypass mode");
        }
        if (operation == null) {
            throw new IllegalArgumentException("Bypass operation is required");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Bypass reason must not be blank");
        }
        return new TenantAccessContext(mode, null, null, actorId, operation, reason.trim());
    }

    public Mode mode() {
        return mode;
    }

    public Long tenantId() {
        return tenantId;
    }

    public String tenantCode() {
        return tenantCode;
    }

    public Long actorId() {
        return actorId;
    }

    public BypassOperation bypassOperation() {
        return bypassOperation;
    }

    public String reason() {
        return reason;
    }

    public boolean bypassesTenantIsolation() {
        return mode != Mode.TENANT;
    }
}
