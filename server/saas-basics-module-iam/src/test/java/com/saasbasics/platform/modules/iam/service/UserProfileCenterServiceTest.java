package com.saasbasics.platform.modules.iam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.file.PlatformFileStorageGateway;
import com.saasbasics.platform.modules.iam.dto.OnlineSessionResponse;
import com.saasbasics.platform.modules.iam.dto.UserPreferencesData;
import com.saasbasics.platform.modules.iam.dto.UserProfileUpdateRequest;
import com.saasbasics.platform.modules.iam.entity.AuthSessionEntity;
import com.saasbasics.platform.modules.iam.entity.UserEntity;
import com.saasbasics.platform.modules.iam.mapper.AuthSessionMapper;
import com.saasbasics.platform.modules.iam.mapper.UserMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

class UserProfileCenterServiceTest {

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> providerOf(T instance) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(instance);
        return provider;
    }

    @BeforeEach
    void setupPrincipal() {
        AuthContext.set(new AuthPrincipal(
                100L, 1L, "platform", 5L, "alice", "Alice", "TENANT_USER", "ONLINE",
                LocalDateTime.now().plusHours(4), List.of("iam:user:query"), List.of(),
                List.of(2L), List.of(), UUID.randomUUID(), UUID.randomUUID(), null, null, null
        ));
    }

    @AfterEach
    void clearPrincipal() {
        AuthContext.clear();
    }

    @Test
    void requiresPasswordConfirmationWhenReplacingBoundMobileOrEmail() {
        UserMapper userMapper = mock(UserMapper.class);
        PasswordSecurityService pwdService = mock(PasswordSecurityService.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UserEntity user = new UserEntity();
        user.setId(5L);
        user.setTenantId(1L);
        user.setUsername("alice");
        user.setNickname("Alice");
        user.setMobile("13900001111");
        user.setEmail("alice@example.com");
        user.setPasswordHash("hashed");
        when(userMapper.selectById(5L)).thenReturn(user);

        UserProfileService service = new UserProfileService(
                userMapper, pwdService, jdbcTemplate, providerOf(null), providerOf(null),
                providerOf(null), providerOf(null), providerOf(null), new ObjectMapper()
        );

        assertThatThrownBy(() -> service.updateProfile(new UserProfileUpdateRequest(
                "Alice New", "13900009999", "alice@example.com", "FEMALE",
                "Bio", "Room 1", "Ext 2", "zh-CN", "Asia/Shanghai", null
        )))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo("CONFIRM_PASSWORD_REQUIRED"));

        when(pwdService.matches("secret123", "hashed")).thenReturn(true);
        service.updateProfile(new UserProfileUpdateRequest(
                "Alice New", "13900009999", "alice@example.com", "FEMALE",
                "Bio", "Room 1", "Ext 2", "en-US", "UTC", "secret123"
        ));
        assertThat(user.getMobile()).isEqualTo("13900009999");
        assertThat(user.getGender()).isEqualTo("FEMALE");
        assertThat(user.getBio()).isEqualTo("Bio");
        assertThat(user.getWorkLocation()).isEqualTo("Room 1");
        verify(userMapper).updateById(user);
    }

    @Test
    void selfServiceSessionsEnforceCurrentSessionAndOtherRevocation() {
        UserMapper userMapper = mock(UserMapper.class);
        AuthSessionMapper sessionMapper = mock(AuthSessionMapper.class);
        AuthSessionEntity current = new AuthSessionEntity();
        current.setId(100L);
        current.setUserId(5L);
        current.setSessionNo("S-CUR");
        current.setStatus("ONLINE");

        AuthSessionEntity other = new AuthSessionEntity();
        other.setId(101L);
        other.setUserId(5L);
        other.setSessionNo("S-OTHER");
        other.setStatus("ONLINE");

        when(sessionMapper.selectList(any())).thenReturn(List.of(current, other));
        when(sessionMapper.selectOne(any())).thenReturn(current).thenReturn(other).thenReturn(null);

        UserProfileService service = new UserProfileService(
                userMapper, mock(PasswordSecurityService.class), mock(JdbcTemplate.class),
                providerOf(null), providerOf(null), providerOf(sessionMapper),
                providerOf(null), providerOf(null), new ObjectMapper()
        );

        List<OnlineSessionResponse> sessions = service.listMySessions();
        assertThat(sessions).hasSize(2);
        assertThat(sessions.get(0).isCurrent()).isTrue();
        assertThat(sessions.get(1).isCurrent()).isFalse();

        assertThatThrownBy(() -> service.revokeMySession("S-CUR"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo("CANNOT_REVOKE_CURRENT_SESSION"));

        service.revokeMySession("S-OTHER");
        assertThat(other.getStatus()).isEqualTo("OFFLINE");

        assertThatThrownBy(() -> service.revokeMySession("S-STRANGER"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo("SESSION_NOT_FOUND"));

        other.setStatus("ONLINE");
        int revoked = service.revokeOtherMySessions();
        assertThat(revoked).isEqualTo(1);
        verify(sessionMapper, times(2)).updateById(other);
    }

    @Test
    void uploadAvatarPersistsAvatarMetadataAndSyncsPreferences() throws Exception {
        UserMapper userMapper = mock(UserMapper.class);
        UserEntity user = new UserEntity();
        user.setId(5L);
        user.setTenantId(1L);
        user.setUsername("alice");
        user.setNickname("Alice");
        when(userMapper.selectById(5L)).thenReturn(user);

        PlatformFileStorageGateway gateway = mock(PlatformFileStorageGateway.class);
        when(gateway.uploadBytes(eq(1L), eq("USER_AVATAR"), eq("avatar.png"), eq("image/png"), any()))
                .thenReturn(new PlatformFileStorageGateway.StoredUploadResult(
                        77L, 1L, "local_default", "LOCAL", "DIRECT_SERVER",
                        "avatars/users", "avatars/users/a.png",
                        "/api/file/raw-download/avatars/users/a.png", "avatar.png", 10L, "image/png"
                ));

        UserProfileService service = new UserProfileService(
                userMapper, mock(PasswordSecurityService.class), mock(JdbcTemplate.class),
                providerOf(null), providerOf(null), providerOf(null),
                providerOf(null), providerOf(gateway), new ObjectMapper()
        );

        service.uploadAvatar(new MockMultipartFile("file", "avatar.png", "image/png", new byte[] {1, 2, 3}));
        assertThat(user.getAvatarFileId()).isEqualTo(77L);
        assertThat(user.getAvatarUrl()).isEqualTo("/api/file/raw-download/avatars/users/a.png");

        UserPreferencesData prefs = service.updatePreferences(new UserPreferencesData(
                "en-US", "UTC", "side", "dark", true, true, true, false, true, true, "qwen-max"
        ));
        assertThat(prefs.layoutMode()).isEqualTo("side");
        assertThat(prefs.railTheme()).isEqualTo("dark");
        assertThat(user.getLocale()).isEqualTo("en-US");
    }
}
