package com.saasbasics.platform.modules.iam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.iam.dto.ScimDtos;
import com.saasbasics.platform.modules.iam.dto.ScimDtos.ScimUserResource;
import com.saasbasics.platform.modules.iam.dto.SelfServiceAccountDtos.PasswordResetConfirmRequest;
import com.saasbasics.platform.modules.iam.dto.SelfServiceAccountDtos.PasswordResetSendCodeRequest;
import com.saasbasics.platform.modules.iam.dto.SelfServiceAccountDtos.PasswordResetSendCodeResponse;
import com.saasbasics.platform.modules.iam.dto.SelfServiceAccountDtos.TenantInviteAcceptRequest;
import com.saasbasics.platform.modules.iam.dto.SelfServiceAccountDtos.TenantInviteCreateRequest;
import com.saasbasics.platform.modules.iam.dto.SelfServiceAccountDtos.TenantInviteResponse;
import com.saasbasics.platform.modules.iam.entity.TenantInviteEntity;
import com.saasbasics.platform.modules.iam.entity.UserEntity;
import com.saasbasics.platform.modules.iam.mapper.TenantInviteMapper;
import com.saasbasics.platform.modules.iam.mapper.UserMapper;
import com.saasbasics.platform.modules.tenant.entity.TenantEntity;
import com.saasbasics.platform.modules.tenant.mapper.TenantMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ScimAndSelfServiceTest {

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> providerOf(T instance) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(instance);
        return provider;
    }

    @AfterEach
    void cleanup() {
        AuthContext.clear();
    }

    @Test
    void shouldVerifyPkceS256ChallengeAndRejectWrongVerifier() throws Exception {
        String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        byte[] sha = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(sha);
        String meta = "OIDC|https://app.example.com/callback|pkce:" + challenge;

        AuthService.verifyPkceIfBound(meta, verifier);

        assertThatThrownBy(() -> AuthService.verifyPkceIfBound(meta, "wrong_verifier"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void shouldProvisionScimUserWithinAuthenticatedTenant() {
        AuthService authService = mock(AuthService.class);
        PasswordSecurityService passwordService = new PasswordSecurityService();
        UserMapper userMapper = mock(UserMapper.class);
        AuthPrincipal principal = new AuthPrincipal(
                10L, 2L, "acme", 10L, "admin", "Admin", "TENANT_ADMIN", "ONLINE",
                null, List.of("*"), List.of(), List.of(), List.of()
        );
        when(authService.resolvePrincipal("tok_valid")).thenReturn(principal);
        when(userMapper.selectByTenantAndUsername(2L, "alice.scim")).thenReturn(null);

        AtomicReference<UserEntity> savedUser = new AtomicReference<>();
        doAnswer(inv -> {
            UserEntity u = inv.getArgument(0);
            u.setId(88L);
            savedUser.set(u);
            return 1;
        }).when(userMapper).insert(any(UserEntity.class));

        ScimProvisioningService scim = new ScimProvisioningService(authService, passwordService, providerOf(userMapper));
        ScimUserResource created = scim.createUser("Bearer tok_valid", new ScimUserResource(
                List.of(ScimDtos.USER_SCHEMA),
                null,
                null,
                "alice.scim",
                "Alice Lin",
                null,
                List.of(new ScimDtos.ScimEmail("alice@acme.example.com", "work", true)),
                true,
                null
        ));

        assertThat(created.userName()).isEqualTo("alice.scim");
        assertThat(created.active()).isTrue();
        assertThat(savedUser.get().getTenantId()).isEqualTo(2L);
        assertThat(savedUser.get().getEmail()).isEqualTo("alice@acme.example.com");
    }

    @Test
    void shouldResetPasswordWithOtpAndAcceptTenantInvite() {
        PasswordSecurityService passwordService = new PasswordSecurityService();
        TenantInviteMapper inviteMapper = mock(TenantInviteMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        TenantMapper tenantMapper = mock(TenantMapper.class);

        TenantEntity tenant = new TenantEntity();
        tenant.setId(2L);
        tenant.setTenantCode("acme");
        tenant.setTenantName("Acme Corp");
        tenant.setStatus("ENABLED");
        tenant.setDeleted(0);
        when(tenantMapper.selectOne(any())).thenReturn(tenant);
        when(tenantMapper.selectById(2L)).thenReturn(tenant);

        UserEntity alice = new UserEntity();
        alice.setId(5L);
        alice.setTenantId(2L);
        alice.setUsername("alice");
        alice.setEmail("alice@acme.example.com");
        alice.setStatus("ENABLED");
        alice.setPasswordHash(passwordService.hash("OldPass@123"));
        when(userMapper.selectByTenantAndUsername(2L, "alice")).thenReturn(alice);
        when(userMapper.selectByTenantAndUsername(2L, "bob.new")).thenReturn(null);

        AtomicReference<TenantInviteEntity> storedInvite = new AtomicReference<>();
        doAnswer(inv -> {
            TenantInviteEntity e = inv.getArgument(0);
            e.setId(77L);
            storedInvite.set(e);
            return 1;
        }).when(inviteMapper).insert(any(TenantInviteEntity.class));
        when(inviteMapper.selectOne(any())).thenAnswer(inv -> storedInvite.get());

        SelfServiceAccountService service = new SelfServiceAccountService(
                passwordService,
                providerOf(inviteMapper),
                providerOf(userMapper),
                providerOf(tenantMapper),
                providerOf(null),
                providerOf(null),
                providerOf(null),
                providerOf(null),
                null
        );

        PasswordResetSendCodeResponse otpResp = service.sendPasswordResetCode(
                new PasswordResetSendCodeRequest("alice", "acme", "alice@acme.example.com")
        );
        assertThat(otpResp.accepted()).isTrue();
        assertThat(otpResp.sandboxOtpCode()).hasSize(6);

        service.confirmPasswordReset(
                new PasswordResetConfirmRequest("alice", "acme", otpResp.sandboxOtpCode(), "NewStrong@999")
        );
        assertThat(passwordService.matches("NewStrong@999", alice.getPasswordHash())).isTrue();

        AuthContext.set(new AuthPrincipal(
                10L, 2L, "acme", 10L, "admin", "Admin", "TENANT_ADMIN", "ONLINE",
                null, List.of("*"), List.of(), List.of(), List.of()
        ));
        TenantInviteResponse inviteResp = service.createInvite(new TenantInviteCreateRequest("bob@acme.example.com", null, 1, 24));
        assertThat(inviteResp.inviteToken()).startsWith("inv_");
        AuthContext.clear();

        TenantInviteResponse accepted = service.acceptInvite(
                inviteResp.inviteToken(),
                new TenantInviteAcceptRequest("bob.new", "Bob New", "BobPass@123", null, null)
        );
        assertThat(accepted.usedCount()).isEqualTo(1);
        assertThat(accepted.status()).isEqualTo("EXHAUSTED");
    }
}
