package com.saasbasics.platform.modules.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.file.PlatformFileStorageGateway;
import com.saasbasics.platform.modules.iam.dto.OnlineSessionResponse;
import com.saasbasics.platform.modules.iam.dto.UserPasswordUpdateRequest;
import com.saasbasics.platform.modules.iam.dto.UserPreferencesData;
import com.saasbasics.platform.modules.iam.dto.UserProfileResponse;
import com.saasbasics.platform.modules.iam.dto.UserProfileUpdateRequest;
import com.saasbasics.platform.modules.iam.entity.AuthSessionEntity;
import com.saasbasics.platform.modules.iam.entity.PasswordHistoryEntity;
import com.saasbasics.platform.modules.iam.entity.PasswordPolicyEntity;
import com.saasbasics.platform.modules.iam.entity.UserEntity;
import com.saasbasics.platform.modules.iam.mapper.AuthSessionMapper;
import com.saasbasics.platform.modules.iam.mapper.PasswordHistoryMapper;
import com.saasbasics.platform.modules.iam.mapper.PasswordPolicyMapper;
import com.saasbasics.platform.modules.iam.mapper.UserMapper;
import com.saasbasics.platform.modules.organization.api.OrganizationDirectory;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import com.saasbasics.platform.modules.iam.mapper.UserProfileMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UserProfileService {

    private final UserMapper userMapper;
    private final PasswordSecurityService passwordSecurityService;
    private final UserProfileMapper profileMapper;
    private final ObjectProvider<PasswordPolicyMapper> passwordPolicyMapperProvider;
    private final ObjectProvider<PasswordHistoryMapper> passwordHistoryMapperProvider;
    private final ObjectProvider<AuthSessionMapper> authSessionMapperProvider;
    private final ObjectProvider<OrganizationDirectory> organizationDirectoryProvider;
    private final ObjectProvider<PlatformFileStorageGateway> fileStorageGatewayProvider;
    private final ObjectMapper objectMapper;

    public UserProfileService(UserMapper userMapper,
                              PasswordSecurityService passwordSecurityService,
                              UserProfileMapper profileMapper,
                              ObjectProvider<PasswordPolicyMapper> passwordPolicyMapperProvider,
                              ObjectProvider<PasswordHistoryMapper> passwordHistoryMapperProvider) {
        this(userMapper, passwordSecurityService, profileMapper, passwordPolicyMapperProvider,
                passwordHistoryMapperProvider, null, null, null, new ObjectMapper());
    }

    @Autowired
    public UserProfileService(UserMapper userMapper,
                              PasswordSecurityService passwordSecurityService,
                              UserProfileMapper profileMapper,
                              ObjectProvider<PasswordPolicyMapper> passwordPolicyMapperProvider,
                              ObjectProvider<PasswordHistoryMapper> passwordHistoryMapperProvider,
                              ObjectProvider<AuthSessionMapper> authSessionMapperProvider,
                              ObjectProvider<OrganizationDirectory> organizationDirectoryProvider,
                              ObjectProvider<PlatformFileStorageGateway> fileStorageGatewayProvider,
                              ObjectMapper objectMapper) {
        this.userMapper = userMapper;
        this.passwordSecurityService = passwordSecurityService;
        this.profileMapper = profileMapper;
        this.passwordPolicyMapperProvider = passwordPolicyMapperProvider;
        this.passwordHistoryMapperProvider = passwordHistoryMapperProvider;
        this.authSessionMapperProvider = authSessionMapperProvider;
        this.organizationDirectoryProvider = organizationDirectoryProvider;
        this.fileStorageGatewayProvider = fileStorageGatewayProvider;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public UserProfileResponse getCurrentProfile() {
        AuthPrincipal principal = requiredPrincipal();
        UserEntity user = requiredUser(principal.userId());

        String tenantName = resolveTenantName(user.getTenantId(), principal.tenantCode());
        List<String> roleNames = profileMapper.roleNames(user.getTenantId(), user.getId());
        List<UserProfileResponse.DataScopeSummaryItem> dataScopes = profileMapper.dataScopes(user.getTenantId(), user.getId());
        List<UserProfileResponse.IdentityBindingItem> identities = profileMapper.identities(user.getTenantId(), user.getId());
        UserProfileResponse.EmployeeProfileInfo employeeInfo = resolveEmployeeInfo(user, principal);
        List<UserProfileResponse.PersonalLoginLogItem> recentLogs = profileMapper.recentLogs(user.getTenantId(), user.getId());

        PasswordPolicyMapper policyMapper = passwordPolicyMapperProvider != null
                ? passwordPolicyMapperProvider.getIfAvailable() : null;
        PasswordPolicyEntity policy = policyMapper != null ? policyMapper.selectActivePolicy(user.getTenantId()) : null;
        Integer maxAgeDays = (policy != null && policy.getPasswordExpireDays() != null && policy.getPasswordExpireDays() > 0)
                ? policy.getPasswordExpireDays() : 90;
        LocalDateTime pwdChanged = user.getPasswordChangedAt() != null ? user.getPasswordChangedAt() : user.getCreatedAt();
        Long daysRemaining = null;
        if (pwdChanged != null && maxAgeDays > 0) {
            long elapsed = Math.max(0L, Duration.between(pwdChanged, LocalDateTime.now()).toDays());
            daysRemaining = maxAgeDays - elapsed;
        }

        int score = calculateSecurityScore(user, daysRemaining);
        UserPreferencesData prefs = UserPreferencesHelper.parseSavedOrNull(objectMapper, user);
        String effectiveLocale = user.getLocale() != null && !user.getLocale().isBlank()
                ? UserPreferencesHelper.normalizeLocale(user.getLocale()) : null;
        String effectiveTimezone = user.getTimezone() != null && !user.getTimezone().isBlank()
                ? UserPreferencesHelper.normalizeTimezone(user.getTimezone()) : null;

        return new UserProfileResponse(
                user.getId(),
                user.getPublicId(),
                user.getUserCode(),
                user.getTenantId(),
                principal.tenantCode(),
                tenantName,
                user.getUsername(),
                user.getNickname(),
                user.getMobile(),
                user.getEmail(),
                user.getAvatarFileId(),
                user.getAvatarUrl(),
                user.getGender() != null ? user.getGender() : "UNKNOWN",
                user.getBio(),
                user.getWorkLocation(),
                user.getExtContact(),
                effectiveLocale,
                effectiveTimezone,
                user.getUserType(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getLastLoginAt(),
                user.getLastLoginIp(),
                user.getPasswordChangedAt(),
                Boolean.TRUE.equals(user.getNeedResetPassword()),
                maxAgeDays,
                daysRemaining,
                Boolean.TRUE.equals(user.getMfaEnabled()),
                score,
                roleNames,
                dataScopes,
                identities,
                employeeInfo,
                prefs,
                recentLogs
        );
    }

    @Transactional
    public UserProfileResponse updateProfile(UserProfileUpdateRequest request) {
        AuthPrincipal principal = requiredPrincipal();
        UserEntity user = requiredUser(principal.userId());

        String nextMobile = normalizeBlankToNull(request.mobile());
        String nextEmail = normalizeBlankToNull(request.email());
        boolean mobileChanged = hasExistingValueChanged(user.getMobile(), nextMobile);
        boolean emailChanged = hasExistingValueChanged(user.getEmail(), nextEmail);
        if ((mobileChanged || emailChanged)) {
            if (request.confirmPassword() == null || request.confirmPassword().isBlank()) {
                throw new BizException("CONFIRM_PASSWORD_REQUIRED", "修改已绑定的手机或邮箱时必须输入当前登录密码进行安全核验");
            }
            if (!passwordSecurityService.matches(request.confirmPassword(), user.getPasswordHash())) {
                throw new BizException("INVALID_CONFIRM_PASSWORD", "当前登录密码核验失败，无法变更手机或邮箱");
            }
        }

        user.setNickname(request.nickname().trim());
        user.setMobile(nextMobile);
        user.setEmail(nextEmail);
        if (request.gender() != null && !request.gender().isBlank()) {
            String genderUpper = request.gender().trim().toUpperCase(Locale.ROOT);
            user.setGender(List.of("MALE", "FEMALE", "OTHER", "UNKNOWN").contains(genderUpper) ? genderUpper : "UNKNOWN");
        }
        user.setBio(normalizeBlankToNull(request.bio()));
        user.setWorkLocation(normalizeBlankToNull(request.workLocation()));
        user.setExtContact(normalizeBlankToNull(request.extContact()));
        if (request.locale() != null && !request.locale().isBlank()) {
            user.setLocale(UserPreferencesHelper.normalizeLocale(request.locale()));
        }
        if (request.timezone() != null && !request.timezone().isBlank()) {
            user.setTimezone(UserPreferencesHelper.normalizeTimezone(request.timezone()));
        }
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        return getCurrentProfile();
    }

    @Transactional
    public UserProfileResponse uploadAvatar(MultipartFile file) throws IOException {
        AuthPrincipal principal = requiredPrincipal();
        UserEntity user = requiredUser(principal.userId());
        if (file == null || file.isEmpty()) {
            throw new BizException("FILE_EMPTY", "请选择有效的头像图片文件");
        }
        PlatformFileStorageGateway gateway = fileStorageGatewayProvider != null
                ? fileStorageGatewayProvider.getIfAvailable() : null;
        if (gateway == null) {
            throw new BizException("FILE_STORAGE_SOURCE_NOT_CONFIGURED",
                    "文件存储网关未就绪，请检查「文件中心 -> 存储策略」是否配置了可用存储源");
        }
        PlatformFileStorageGateway.StoredUploadResult stored = gateway.uploadBytes(
                user.getTenantId(),
                "USER_AVATAR",
                file.getOriginalFilename(),
                file.getContentType(),
                file.getBytes()
        );
        user.setAvatarFileId(stored.fileId());
        user.setAvatarUrl(stored.url());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return getCurrentProfile();
    }

    @Transactional
    public UserPreferencesData updatePreferences(UserPreferencesData request) {
        AuthPrincipal principal = requiredPrincipal();
        UserEntity user = requiredUser(principal.userId());
        if (request != null) {
            if (request.locale() != null && !request.locale().isBlank()) {
                user.setLocale(UserPreferencesHelper.normalizeLocale(request.locale()));
            }
            if (request.timezone() != null && !request.timezone().isBlank()) {
                user.setTimezone(UserPreferencesHelper.normalizeTimezone(request.timezone()));
            }
            String json = UserPreferencesHelper.serialize(objectMapper, request);
            if (json != null) {
                user.setPreferencesJson(json);
            }
            user.setUpdatedAt(LocalDateTime.now());
            userMapper.updateById(user);
        }
        return UserPreferencesHelper.parse(objectMapper, user);
    }

    public List<OnlineSessionResponse> listMySessions() {
        AuthPrincipal principal = requiredPrincipal();
        AuthSessionMapper sessionMapper = authSessionMapperProvider != null
                ? authSessionMapperProvider.getIfAvailable() : null;
        if (sessionMapper == null) {
            return List.of();
        }
        List<AuthSessionEntity> sessions = sessionMapper.selectList(new LambdaQueryWrapper<AuthSessionEntity>()
                .eq(AuthSessionEntity::getTenantId, principal.tenantId())
                .eq(AuthSessionEntity::getUserId, principal.userId())
                .eq(AuthSessionEntity::getStatus, "ONLINE")
                .gt(AuthSessionEntity::getExpireAt, LocalDateTime.now())
                .eq(AuthSessionEntity::getDeleted, 0)
                .orderByDesc(AuthSessionEntity::getLastAccessAt));
        Long currentSessionId = principal.sessionId();
        return sessions.stream()
                .map(s -> new OnlineSessionResponse(
                        s.getSessionNo(),
                        s.getUserId(),
                        s.getUsername(),
                        s.getNickname(),
                        s.getUserType(),
                        s.getLoginIp(),
                        s.getUserAgent(),
                        s.getCreatedAt(),
                        s.getLastAccessAt(),
                        s.getExpireAt(),
                        s.getStatus(),
                        Objects.equals(s.getId(), currentSessionId)
                ))
                .toList();
    }

    @Transactional
    public void revokeMySession(String sessionNo) {
        AuthPrincipal principal = requiredPrincipal();
        AuthSessionMapper sessionMapper = authSessionMapperProvider != null
                ? authSessionMapperProvider.getIfAvailable() : null;
        if (sessionMapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "Session management requires MySQL persistence");
        }
        AuthSessionEntity session = sessionMapper.selectOne(new LambdaQueryWrapper<AuthSessionEntity>()
                .eq(AuthSessionEntity::getSessionNo, sessionNo)
                .eq(AuthSessionEntity::getTenantId, principal.tenantId())
                .eq(AuthSessionEntity::getUserId, principal.userId())
                .eq(AuthSessionEntity::getDeleted, 0)
                .last("LIMIT 1"));
        if (session == null) {
            throw new BizException("SESSION_NOT_FOUND", "目标会话不存在或无权操作");
        }
        if (Objects.equals(session.getId(), principal.sessionId())) {
            throw new BizException("CANNOT_REVOKE_CURRENT_SESSION", "当前正在使用的会话不能在此下线，请通过右上角退出登录");
        }
        session.setStatus("OFFLINE");
        session.setLogoutAt(LocalDateTime.now());
        sessionMapper.updateById(session);
    }

    @Transactional
    public int revokeOtherMySessions() {
        AuthPrincipal principal = requiredPrincipal();
        AuthSessionMapper sessionMapper = authSessionMapperProvider != null
                ? authSessionMapperProvider.getIfAvailable() : null;
        if (sessionMapper == null) {
            return 0;
        }
        List<AuthSessionEntity> others = sessionMapper.selectList(new LambdaQueryWrapper<AuthSessionEntity>()
                .eq(AuthSessionEntity::getTenantId, principal.tenantId())
                .eq(AuthSessionEntity::getUserId, principal.userId())
                .eq(AuthSessionEntity::getStatus, "ONLINE")
                .eq(AuthSessionEntity::getDeleted, 0));
        int count = 0;
        LocalDateTime now = LocalDateTime.now();
        for (AuthSessionEntity session : others) {
            if (!Objects.equals(session.getId(), principal.sessionId())) {
                session.setStatus("OFFLINE");
                session.setLogoutAt(now);
                sessionMapper.updateById(session);
                count++;
            }
        }
        return count;
    }

    @Transactional
    public void updatePassword(UserPasswordUpdateRequest request) {
        AuthPrincipal principal = requiredPrincipal();
        UserEntity user = requiredUser(principal.userId());

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

    private AuthPrincipal requiredPrincipal() {
        AuthPrincipal principal = AuthContext.get();
        if (principal == null || principal.userId() == null) {
            throw new BizException("UNAUTHORIZED", "未登录或会话已失效");
        }
        return principal;
    }

    private UserEntity requiredUser(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("USER_NOT_FOUND", "当前用户不存在");
        }
        return user;
    }

    private String resolveTenantName(Long tenantId, String fallbackCode) {
        String name = profileMapper.tenantName(tenantId);
        return name != null ? name : fallbackCode;
    }

    private UserProfileResponse.EmployeeProfileInfo resolveEmployeeInfo(UserEntity user, AuthPrincipal principal) {
        Long employeeId = user.getEmployeeId();
        UserProfileMapper.EmployeeDetails employee = employeeId == null ? null
                : profileMapper.employee(user.getTenantId(), employeeId);

        String personPublicId = principal.personPublicId() != null ? principal.personPublicId().toString() : null;
        String activeAssignmentPublicId = principal.organizationContext() != null
                && principal.organizationContext().assignmentPublicId() != null
                ? principal.organizationContext().assignmentPublicId().toString() : null;

        List<UserProfileResponse.AssignmentItem> assignments = new ArrayList<>();
        OrganizationDirectory directory = organizationDirectoryProvider != null
                ? organizationDirectoryProvider.getIfAvailable() : null;
        if (directory != null && principal.personPublicId() != null) {
            for (var a : directory.findEffectiveAssignments(principal.personPublicId(), Instant.now())) {
                String id = a.publicId().toString();
                assignments.add(new UserProfileResponse.AssignmentItem(id, a.organizationName(), a.unitName(),
                        a.positionName(), a.primary(), id.equals(activeAssignmentPublicId)));
            }
        }

        if (employeeId == null && personPublicId == null && assignments.isEmpty()) {
            return null;
        }
        return new UserProfileResponse.EmployeeProfileInfo(
                employeeId, employee == null ? null : employee.employeeNo(), employee == null ? null : employee.employeeName(),
                employee == null ? null : employee.deptId(), employee == null ? null : employee.deptName(),
                employee == null ? null : employee.deptFullName(), employee == null ? null : employee.positionId(),
                employee == null ? null : employee.positionName(), employee == null ? null : employee.hireDate(),
                employee == null ? null : employee.employeeStatus(),
                personPublicId, activeAssignmentPublicId, assignments
        );
    }

    private static int calculateSecurityScore(UserEntity user, Long daysRemaining) {
        int score = 35;
        if (Boolean.TRUE.equals(user.getMfaEnabled())) {
            score += 25;
        }
        if (user.getMobile() != null && !user.getMobile().isBlank()) {
            score += 15;
        }
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            score += 10;
        }
        if (!Boolean.TRUE.equals(user.getNeedResetPassword()) && (daysRemaining == null || daysRemaining > 7)) {
            score += 15;
        }
        return Math.min(100, score);
    }

    private static boolean hasExistingValueChanged(String existingValue, String nextValue) {
        String existing = existingValue == null ? "" : existingValue.trim();
        String next = nextValue == null ? "" : nextValue.trim();
        return !existing.isEmpty() && !existing.equals(next);
    }

    private static String normalizeBlankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
