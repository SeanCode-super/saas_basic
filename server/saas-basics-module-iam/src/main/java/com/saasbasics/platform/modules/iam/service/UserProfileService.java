package com.saasbasics.platform.modules.iam.service;

import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.iam.dto.UserPasswordUpdateRequest;
import com.saasbasics.platform.modules.iam.dto.UserProfileResponse;
import com.saasbasics.platform.modules.iam.dto.UserProfileUpdateRequest;
import com.saasbasics.platform.modules.iam.entity.UserEntity;
import com.saasbasics.platform.modules.iam.mapper.UserMapper;
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

    public UserProfileService(UserMapper userMapper,
                              PasswordSecurityService passwordSecurityService,
                              JdbcTemplate jdbcTemplate) {
        this.userMapper = userMapper;
        this.passwordSecurityService = passwordSecurityService;
        this.jdbcTemplate = jdbcTemplate;
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

        user.setPasswordHash(passwordSecurityService.hash(request.newPassword()));
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setNeedResetPassword(false);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }
}
