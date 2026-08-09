package com.saasbasics.platform.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.tenant.TenantAccessContext;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantAccessContextTest {

    @AfterEach
    void clearContexts() {
        AuthContext.clear();
        TenantAccessContextHolder.clear();
    }

    @Test
    void nestedScopesRestoreThePreviousTenant() {
        try (TenantAccessContextHolder.Scope ignored = TenantAccessContextHolder.openTenant(11L, "alpha", 101L)) {
            assertThat(TenantAccessContextHolder.requiredTenantId()).isEqualTo(11L);

            try (TenantAccessContextHolder.Scope nested = TenantAccessContextHolder.openTenant(22L, "beta", 101L)) {
                assertThat(TenantAccessContextHolder.requiredTenantId()).isEqualTo(22L);
            }

            assertThat(TenantAccessContextHolder.requiredTenantId()).isEqualTo(11L);
        }

        assertThat(TenantAccessContextHolder.current()).isNull();
    }

    @Test
    void platformBypassRequiresPlatformIdentityPermissionAndReason() {
        AuthContext.set(principal("STAFF", List.of("tenant:write")));
        assertThatThrownBy(() -> TenantAccessContextHolder.openPlatformBypass(
                "tenant:write",
                TenantAccessContext.BypassOperation.TENANT_BOOTSTRAP,
                "test bootstrap"))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo("TENANT_BYPASS_FORBIDDEN");

        AuthContext.set(principal("PLATFORM", List.of("tenant:query")));
        assertThatThrownBy(() -> TenantAccessContextHolder.openPlatformBypass(
                "tenant:write",
                TenantAccessContext.BypassOperation.TENANT_BOOTSTRAP,
                "test bootstrap"))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo("TENANT_BYPASS_FORBIDDEN");

        AuthContext.set(principal("PLATFORM", List.of("tenant:write")));
        assertThatThrownBy(() -> TenantAccessContextHolder.openPlatformBypass(
                "tenant:write",
                TenantAccessContext.BypassOperation.TENANT_BOOTSTRAP,
                " "))
                .isInstanceOf(IllegalArgumentException.class);

        try (TenantAccessContextHolder.Scope ignored = TenantAccessContextHolder.openPlatformBypass(
                "tenant:write",
                TenantAccessContext.BypassOperation.TENANT_BOOTSTRAP,
                "test bootstrap")) {
            TenantAccessContext context = TenantAccessContextHolder.current();
            assertThat(context).isNotNull();
            assertThat(context.mode()).isEqualTo(TenantAccessContext.Mode.PLATFORM_BYPASS);
            assertThat(context.actorId()).isEqualTo(101L);
            assertThat(context.reason()).isEqualTo("test bootstrap");
        }
    }

    private AuthPrincipal principal(String userType, List<String> permissions) {
        return new AuthPrincipal(
                1L,
                1L,
                "platform",
                101L,
                "tester",
                "Tester",
                userType,
                "ONLINE",
                LocalDateTime.now().plusHours(1),
                permissions,
                List.of(),
                List.of(),
                List.of()
        );
    }
}
