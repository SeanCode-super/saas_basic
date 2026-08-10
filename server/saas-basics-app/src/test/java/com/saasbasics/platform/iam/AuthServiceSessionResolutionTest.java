package com.saasbasics.platform.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.id.UuidV7Generator;
import com.saasbasics.platform.config.SecurityProperties;
import com.saasbasics.platform.modules.audit.mapper.LoginLogMapper;
import com.saasbasics.platform.modules.iam.entity.AuthSessionEntity;
import com.saasbasics.platform.modules.iam.entity.UserEntity;
import com.saasbasics.platform.modules.iam.entity.UserPersonBindingEntity;
import com.saasbasics.platform.modules.iam.mapper.AuthSessionMapper;
import com.saasbasics.platform.modules.iam.mapper.LoginFailStatMapper;
import com.saasbasics.platform.modules.iam.mapper.LoginPolicyMapper;
import com.saasbasics.platform.modules.iam.mapper.PasswordHistoryMapper;
import com.saasbasics.platform.modules.iam.mapper.PasswordPolicyMapper;
import com.saasbasics.platform.modules.iam.mapper.RoleApiMapper;
import com.saasbasics.platform.modules.iam.mapper.UserMapper;
import com.saasbasics.platform.modules.iam.mapper.UserPersonBindingMapper;
import com.saasbasics.platform.modules.iam.mapper.UserRoleMapper;
import com.saasbasics.platform.modules.iam.service.AuthService;
import com.saasbasics.platform.modules.iam.service.MenuPermissionService;
import com.saasbasics.platform.modules.iam.service.PasswordSecurityService;
import com.saasbasics.platform.modules.iam.service.PortalEntryService;
import com.saasbasics.platform.modules.organization.api.OrganizationDirectory;
import com.saasbasics.platform.modules.tenant.entity.TenantEntity;
import com.saasbasics.platform.modules.tenant.mapper.TenantMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class AuthServiceSessionResolutionTest {

    private static final Long TENANT_ID = 42L;
    private static final Long USER_ID = 84L;
    private static final UuidV7Generator UUIDS = new UuidV7Generator();

    private AuthSessionMapper sessionMapper;
    private UserPersonBindingMapper bindingMapper;
    private OrganizationDirectory organizationDirectory;
    private AuthService authService;
    private AuthSessionEntity session;
    private UserPersonBindingEntity binding;

    @BeforeEach
    void setUp() {
        sessionMapper = mock(AuthSessionMapper.class);
        bindingMapper = mock(UserPersonBindingMapper.class);
        organizationDirectory = mock(OrganizationDirectory.class);

        UserMapper userMapper = mock(UserMapper.class);
        TenantMapper tenantMapper = mock(TenantMapper.class);
        UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);
        RoleApiMapper roleApiMapper = mock(RoleApiMapper.class);
        MenuPermissionService menuPermissionService = mock(MenuPermissionService.class);

        session = activeSession();
        UserEntity user = activeUser();
        binding = activeBinding(session.getSubjectBindingPublicId());
        TenantEntity tenant = activeTenant();

        when(sessionMapper.selectOnlineByTokenHash(any(), any())).thenReturn(session);
        when(sessionMapper.updateById(any(AuthSessionEntity.class))).thenReturn(1);
        when(sessionMapper.update(isNull(), org.mockito.ArgumentMatchers.<Wrapper<AuthSessionEntity>>any())).thenReturn(1);
        when(userMapper.selectById(USER_ID)).thenReturn(user);
        when(tenantMapper.selectById(TENANT_ID)).thenReturn(tenant);
        when(bindingMapper.selectEffectiveByUser(any(), any(), any())).thenReturn(binding);
        when(userRoleMapper.selectRoleIdsByUserId(any(), any(), any())).thenReturn(List.of());
        when(roleApiMapper.selectPermissionCodesByUserId(any(), any(), any())).thenReturn(List.of());
        when(menuPermissionService.resolveButtonPermissions(any())).thenReturn(List.of());

        authService = new AuthService(
                mock(SecurityProperties.class),
                mock(PasswordSecurityService.class),
                provider(userMapper),
                provider(tenantMapper),
                provider(sessionMapper),
                emptyProvider(LoginPolicyMapper.class),
                emptyProvider(PasswordPolicyMapper.class),
                emptyProvider(LoginFailStatMapper.class),
                emptyProvider(PasswordHistoryMapper.class),
                emptyProvider(LoginLogMapper.class),
                provider(userRoleMapper),
                provider(roleApiMapper),
                provider(bindingMapper),
                mock(PortalEntryService.class),
                menuPermissionService,
                organizationDirectory
        );
    }

    @Test
    void revokesSessionWhenItsSubjectBindingWasRevoked() {
        when(bindingMapper.selectEffectiveByUser(any(), any(), any())).thenReturn(null);

        assertThat(authService.resolvePrincipal("revoked-binding-token")).isNull();

        assertThat(session.getStatus()).isEqualTo("OFFLINE");
        assertThat(session.getLogoutAt()).isNotNull();
        verify(sessionMapper).updateById(session);
    }

    @Test
    void revokesSessionWhenItsSubjectBindingChanged() {
        binding.setPublicId(UUIDS.generate().toString());

        assertThat(authService.resolvePrincipal("changed-binding-token")).isNull();

        assertThat(session.getStatus()).isEqualTo("OFFLINE");
        assertThat(session.getLogoutAt()).isNotNull();
        verify(sessionMapper).updateById(session);
    }

    @Test
    void revokesSessionWhenItsBoundPersonIsNoLongerEffective() {
        when(organizationDirectory.requireEffectivePerson(
                eq(UUID.fromString(binding.getPersonPublicId())), any()))
                .thenThrow(new BizException("ORG_PERSON_NOT_EFFECTIVE", "The person is no longer effective"));

        assertThat(authService.resolvePrincipal("inactive-person-token")).isNull();

        assertThat(session.getStatus()).isEqualTo("OFFLINE");
        assertThat(session.getLogoutAt()).isNotNull();
        verify(sessionMapper).updateById(session);
    }

    @Test
    void clearsInvalidAssignmentContextWithoutRevokingSession() {
        UUID selectedAssignment = UUID.fromString(session.getSelectedAssignmentPublicId());
        when(organizationDirectory.requireEffectiveAssignmentForPerson(
                eq(UUID.fromString(binding.getPersonPublicId())), eq(selectedAssignment), any()))
                .thenThrow(new BizException(
                        "ORG_ASSIGNMENT_NOT_EFFECTIVE",
                        "The assignment is no longer effective"));

        AuthPrincipal principal = authService.resolvePrincipal("expired-assignment-token");

        assertThat(principal).isNotNull();
        assertThat(principal.personPublicId()).isEqualTo(UUID.fromString(binding.getPersonPublicId()));
        assertThat(principal.organizationContext()).isNull();
        assertThat(session.getSelectedAssignmentPublicId()).isNull();
        assertThat(session.getStatus()).isEqualTo("ONLINE");
        assertThat(session.getLogoutAt()).isNull();
        verify(sessionMapper).update(
                isNull(), org.mockito.ArgumentMatchers.<Wrapper<AuthSessionEntity>>any());
    }

    @Test
    void revokesSessionWhenPersonBecomesInvalidDuringAssignmentResolution() {
        String selectedAssignment = session.getSelectedAssignmentPublicId();
        when(organizationDirectory.requireEffectiveAssignmentForPerson(any(), any(), any()))
                .thenThrow(new BizException("ORG_PERSON_NOT_EFFECTIVE", "The person is no longer effective"));

        assertThat(authService.resolvePrincipal("person-invalid-during-assignment-token")).isNull();

        assertThat(session.getSelectedAssignmentPublicId()).isEqualTo(selectedAssignment);
        assertThat(session.getStatus()).isEqualTo("OFFLINE");
        assertThat(session.getLogoutAt()).isNotNull();
        verify(sessionMapper).updateById(session);
    }

    @Test
    void doesNotRevokeOrClearSessionForInfrastructureFailure() {
        String selectedAssignment = session.getSelectedAssignmentPublicId();
        when(organizationDirectory.requireEffectiveAssignmentForPerson(any(), any(), any()))
                .thenThrow(new BizException("DB_PROFILE_REQUIRED", "Organization storage is unavailable"));

        assertThatThrownBy(() -> authService.resolvePrincipal("storage-failure-token"))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).getCode())
                .isEqualTo("DB_PROFILE_REQUIRED");

        assertThat(session.getSelectedAssignmentPublicId()).isEqualTo(selectedAssignment);
        assertThat(session.getStatus()).isEqualTo("ONLINE");
        assertThat(session.getLogoutAt()).isNull();
        verify(sessionMapper, never()).updateById(session);
    }

    private AuthSessionEntity activeSession() {
        AuthSessionEntity entity = new AuthSessionEntity();
        entity.setId(700L);
        entity.setTenantId(TENANT_ID);
        entity.setTenantCode("tenant-42");
        entity.setUserId(USER_ID);
        entity.setPublicId(UUIDS.generate().toString());
        entity.setSubjectBindingPublicId(UUIDS.generate().toString());
        entity.setSelectedAssignmentPublicId(UUIDS.generate().toString());
        entity.setUsername("session-user");
        entity.setNickname("Session User");
        entity.setUserType("STAFF");
        entity.setStatus("ONLINE");
        entity.setExpireAt(LocalDateTime.now().plusHours(1));
        entity.setVersion(0);
        entity.setDeleted(0);
        return entity;
    }

    private UserEntity activeUser() {
        UserEntity entity = new UserEntity();
        entity.setId(USER_ID);
        entity.setTenantId(TENANT_ID);
        entity.setPublicId(UUIDS.generate().toString());
        entity.setStatus("ENABLED");
        entity.setDeleted(0);
        return entity;
    }

    private UserPersonBindingEntity activeBinding(String publicId) {
        UserPersonBindingEntity entity = new UserPersonBindingEntity();
        entity.setPublicId(publicId);
        entity.setTenantId(TENANT_ID);
        entity.setUserId(USER_ID);
        entity.setPersonPublicId(UUIDS.generate().toString());
        entity.setStatus("ACTIVE");
        entity.setDeleted(0);
        return entity;
    }

    private TenantEntity activeTenant() {
        TenantEntity entity = new TenantEntity();
        entity.setId(TENANT_ID);
        entity.setTenantCode("tenant-42");
        entity.setStatus("ENABLED");
        entity.setDeleted(0);
        return entity;
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }

    private static <T> ObjectProvider<T> emptyProvider(Class<T> type) {
        return provider(null);
    }
}
