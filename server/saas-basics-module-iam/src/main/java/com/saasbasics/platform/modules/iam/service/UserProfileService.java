package com.saasbasics.platform.modules.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.iam.dto.UserPasswordUpdateRequest;
import com.saasbasics.platform.modules.iam.dto.UserProfileResponse;
import com.saasbasics.platform.modules.iam.dto.UserProfileUpdateRequest;
import com.saasbasics.platform.modules.iam.entity.PasswordHistoryEntity;
import com.saasbasics.platform.modules.iam.entity.PasswordPolicyEntity;
import com.saasbasics.platform.modules.iam.entity.UserEntity;
import com.saasbasics.platform.modules.iam.mapper.PasswordHistoryMapper;
import com.saasbasics.platform.modules.iam.mapper.PasswordPolicyMapper;
import com.saasbasics.platform.modules.iam.mapper.UserMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class UserProfileService {

    private final UserMapper userMapper;
    private final PasswordSecurityService passwordSecurityService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectProvider<PasswordPolicyMapper> passwordPolicyMapperProvider;
    private final ObjectProvider<PasswordHistoryMapper> passwordHistoryMapperProvider;

    public UserProfileService(UserMapper userMapper,
                              PasswordSecurityService passwordSecurityService,
                              JdbcTemplate jdbcTemplate,
                              ObjectProvider<PasswordPolicyMapper> passwordPolicyMapperProvider,
                              ObjectProvider<PasswordHistoryMapper> passwordHistoryMapperProvider) {
        this.userMapper = userMapper;
        this.passwordSecurityService = passwordSecurityService;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordPolicyMapperProvider = passwordPolicyMapperProvider;
        this.passwordHistoryMapperProvider = passwordHistoryMapperProvider;
    }

    public UserProfileResponse getCurrentProfile() {
        AuthPrincipal principal = AuthContext.get();
        if (principal == null || principal.userId() == null) {
            throw new BizException("UNAUTHORIZED", "未登录或会话已失效");
        }

        UserEntity user = userMapper.selectById(principal.userId());
        if (user == null) {
            throw new BizException("USER_NOT_FOUND", "当前用户不存在");
        }

        // 查询用户所属角色列表
        List<String> roleNames = jdbcTemplate.query(
                "SELECT r.role_name FROM iam_role r " +
                "INNER JOIN iam_user_role ur ON r.id = ur.role_id " +
                "WHERE ur.user_id = ? AND ur.deleted = 0 AND r.deleted = 0",
                (rs, rowNum) -> rs.getString("role_name"),
                user.getId()
        );

        // 查询最近10条个人登录历史
        List<UserProfileResponse.PersonalLoginLogItem> recentLogs = new ArrayList<>();
        try {
            recentLogs = jdbcTemplate.query(
                    "SELECT id, login_ip, success, fail_reason, occurred_at FROM audit_login_log " +
                    "WHERE user_id = ? ORDER BY id DESC LIMIT 10",
                    (rs, rowNum) -> new UserProfileResponse.PersonalLoginLogItem(
                            rs.getLong("id"),
                            rs.getString("login_ip"),
                            rs.getBoolean("success"),
                            rs.getString("fail_reason"),
                            rs.getTimestamp("occurred_at") != null ? rs.getTimestamp("occurred_at").toLocalDateTime() : null
                    ),
                    user.getId()
            );
        } catch (Exception ignored) {
            // 容错处理
        }

        return new UserProfileResponse(
                user.getId(),
                user.getTenantId(),
                principal.tenantCode(),
                user.getUsername(),
                user.getNickname(),
                user.getMobile(),
                user.getEmail(),
                user.getUserType(),
                user.getStatus(),
                user.getLastLoginAt(),
                user.getLastLoginIp(),
                roleNames,
                recentLogs
        );
    }

    @Transactional
    public UserProfileResponse updateProfile(UserProfileUpdateRequest request) {
        AuthPrincipal principal = AuthContext.get();
        if (principal == null || principal.userId() == null) {
            throw new BizException("UNAUTHORIZED", "未登录或会话已失效");
        }

        UserEntity user = userMapper.selectById(principal.userId());
        if (user == null) {
            throw new BizException("USER_NOT_FOUND", "用户不存在");
        }

        user.setNickname(request.nickname());
        if (request.mobile() != null) {
            user.setMobile(request.mobile());
        }
        if (request.email() != null) {
            user.setEmail(request.email());
        }
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        return getCurrentProfile();
    }

    @Transactional
    public void updatePassword(UserPasswordUpdateRequest request) {
        AuthPrincipal principal = AuthContext.get();
        if (principal == null || principal.userId() == null) {
            throw new BizException("UNAUTHORIZED", "未登录或会话已失效");
        }

        UserEntity user = userMapper.selectById(principal.userId());
        if (user == null) {
            throw new BizException("USER_NOT_FOUND", "用户不存在");
        }

        if (!passwordSecurityService.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new BizException("INVALID_OLD_PASSWORD", "原密码校验错误，请重新输入");
        }

        if (passwordSecurityService.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BizException("SAME_PASSWORD", "新密码不能与旧密码相同");
        }

        PasswordPolicyMapper policyMapper = passwordPolicyMapperProvider != null ? passwordPolicyMapperProvider.getIfAvailable() : null;
        PasswordPolicyEntity policy = policyMapper != null ? policyMapper.selectActivePolicy(user.getTenantId()) : null;
        passwordSecurityService.validate(request.newPassword(), policy);

        PasswordHistoryMapper historyMapper = passwordHistoryMapperProvider != null ? passwordHistoryMapperProvider.getIfAvailable() : null;
        int limit = (policy != null && policy.getPasswordHistoryLimit() != null) ? policy.getPasswordHistoryLimit() : 5;
        if (historyMapper != null && limit > 0) {
            List<PasswordHistoryEntity> recent = historyMapper.selectList(
                    new LambdaQueryWrapper<PasswordHistoryEntity>()
                            .eq(PasswordHistoryEntity::getTenantId, user.getTenantId())
                            .eq(PasswordHistoryEntity::getUserId, user.getId())
                            .eq(PasswordHistoryEntity::getDeleted, 0)
                            .orderByDesc(PasswordHistoryEntity::getChangedAt)
                            .last("LIMIT " + limit)
            );
            for (PasswordHistoryEntity h : recent) {
                if (passwordSecurityService.matches(request.newPassword(), h.getPasswordHash())) {
                    throw new BizException("PASSWORD_HISTORY_CONFLICT",
                            "新密码不能与最近 " + limit + " 次使用过的历史密码相同");
                }
            }
        }

        String nextHash = passwordSecurityService.hash(request.newPassword());
        user.setPasswordHash(nextHash);
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setNeedResetPassword(false);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        if (historyMapper != null) {
            PasswordHistoryEntity rec = new PasswordHistoryEntity();
            rec.setTenantId(user.getTenantId());
            rec.setUserId(user.getId());
            rec.setPasswordHash(nextHash);
            rec.setChangedAt(LocalDateTime.now());
            rec.setChangeReason("USER_PASSWORD_UPDATE");
            historyMapper.insert(rec);
        }
    }
}
