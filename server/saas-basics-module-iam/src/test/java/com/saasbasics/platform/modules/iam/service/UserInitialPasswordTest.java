package com.saasbasics.platform.modules.iam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.common.tenant.TenantMembershipCapacity;
import com.saasbasics.platform.modules.iam.dto.UserResponse;
import com.saasbasics.platform.modules.iam.dto.UserSaveRequest;
import com.saasbasics.platform.modules.iam.entity.UserEntity;
import com.saasbasics.platform.modules.iam.mapper.PasswordPolicyMapper;
import com.saasbasics.platform.modules.iam.mapper.UserMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class UserInitialPasswordTest {

    private final UserMapper users = mock(UserMapper.class);
    private final StaticListableBeanFactory beans = new StaticListableBeanFactory(Map.of("users", users));
    private final UserService service = new UserService(beans.getBeanProvider(UserMapper.class),
            new PasswordSecurityService(), mock(DataPermissionRuleService.class),
            mock(TenantMembershipCapacity.class), beans.getBeanProvider(PasswordPolicyMapper.class));

    @Test
    void rejectsMissingInitialPasswordBeforeWritingAndPreservesExistingHashOnOptionalUpdate() {
        try (var scope = TenantAccessContextHolder.openTenant(9L, "sample", 1L)) {
            for (String blank : new String[]{null, "", "   "}) {
                assertThatThrownBy(() -> service.createUser(request(blank)))
                        .isInstanceOfSatisfying(BizException.class,
                                e -> assertThat(e.getCode()).isEqualTo("IAM_INITIAL_PASSWORD_REQUIRED"));
            }
            verifyNoInteractions(users);
            var existing = new UserEntity();
            existing.setId(8L);
            existing.setTenantId(9L);
            String originalHash = new PasswordSecurityService().hash("User-chosen2026!");
            existing.setPasswordHash(originalHash);
            when(users.selectById(8L)).thenReturn(existing);
            when(users.selectUserById(8L)).thenReturn(mock(UserResponse.class));

            service.updateUser(8L, request(null));

            verify(users).updateById(existing);
            assertThat(existing.getPasswordHash()).isEqualTo(originalHash);
        }
    }

    private UserSaveRequest request(String password) {
        return new UserSaveRequest(9L, "U8", "u8", "User", null, "EMPLOYEE", "ENABLED",
                null, null, password, null, null);
    }
}
