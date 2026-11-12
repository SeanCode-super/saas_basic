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
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UserProfileService {

    private final UserMapper userMapper;
    private final PasswordSecurityService passwordSecurityService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectProvider<PasswordPolicyMapper> passwordPolicyMapperProvider;
    private final ObjectProvider<PasswordHistoryMapper> passwordHistoryMapperProvider;
    private final ObjectProvider<AuthSessionMapper> authSessionMapperProvider;
    private final ObjectProvider<OrganizationDirectory> organizationDirectoryProvider;
    private final ObjectProvider<PlatformFileStorageGateway> fileStorageGatewayProvider;
    private final ObjectMapper objectMapper;

    public UserProfileService(UserMapper userMapper,
                              PasswordSecurityService passwordSecurityService,
                              JdbcTemplate jdbcTemplate,
                              ObjectProvider<PasswordPolicyMapper> passwordPolicyMapperProvider,
                              ObjectProvider<PasswordHistoryMapper> passwordHistoryMapperProvider) {
        this(userMapper, passwordSecurityService, jdbcTemplate, passwordPolicyMapperProvider,
                passwordHistoryMapperProvider, null, null, null, new ObjectMapper());
    }

    @Autowired
    public UserProfileService(UserMapper userMapper,
                              PasswordSecurityService passwordSecurityService,
                              JdbcTemplate jdbcTemplate,
                              ObjectProvider<PasswordPolicyMapper> passwordPolicyMapperProvider,
                              ObjectProvider<PasswordHistoryMapper> passwordHistoryMapperProvider,
                              ObjectProvider<AuthSessionMapper> authSessionMapperProvider,
                              ObjectProvider<OrganizationDirectory> organizationDirectoryProvider,
                              ObjectProvider<PlatformFileStorageGateway> fileStorageGatewayProvider,
                              ObjectMapper objectMapper) {
        this.userMapper = userMapper;
        this.passwordSecurityService = passwordSecurityService;
        this.jdbcTemplate = jdbcTemplate;
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
        List<String> roleNames = queryRoleNames(user.getId());
        List<UserProfileResponse.DataScopeSummaryItem> dataScopes = queryDataScopes(user.getId(), user.getTenantId());
        List<UserProfileResponse.IdentityBindingItem> identities = queryIdentities(user.getId(), user.getTenantId());
        UserProfileResponse.EmployeeProfileInfo employeeInfo = resolveEmployeeInfo(user, principal);
        List<UserProfileResponse.PersonalLoginLogItem> recentLogs = queryRecentLogs(user.getId());

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
        UserPreferencesData prefs = UserPreferencesHelper.parse(objectMapper, user);

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
                prefs.locale(),
                prefs.timezone(),
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
        try {
            List<String> names = jdbcTemplate.query(
                    "SELECT tenant_name FROM plat_tenant WHERE id = ? AND deleted = 0 LIMIT 1",
                    (rs, rowNum) -> rs.getString("tenant_name"),
                    tenantId
            );
            if (!names.isEmpty() && names.get(0) != null) {
                return names.get(0);
            }
        } catch (Exception ignored) {
        }
        return fallbackCode != null ? fallbackCode : "默认租户";
    }

    private List<String> queryRoleNames(Long userId) {
        try {
            return jdbcTemplate.query(
                    "SELECT r.role_name FROM iam_role r " +
                    "INNER JOIN iam_user_role ur ON r.id = ur.role_id " +
                    "WHERE ur.user_id = ? AND ur.deleted = 0 AND r.deleted = 0",
                    (rs, rowNum) -> rs.getString("role_name"),
                    userId
            );
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<UserProfileResponse.DataScopeSummaryItem> queryDataScopes(Long userId, Long tenantId) {
        try {
            return jdbcTemplate.query(
                    "SELECT r.role_name, COALESCE(ds.scope_code, r.role_code) AS scope_code, " +
                    "COALESCE(ds.scope_name, r.data_scope_type) AS scope_name, " +
                    "COALESCE(ds.scope_type, r.data_scope_type) AS scope_type " +
                    "FROM iam_user_role ur " +
                    "INNER JOIN iam_role r ON r.id = ur.role_id AND r.deleted = 0 " +
                    "LEFT JOIN iam_role_data_scope rds ON rds.role_id = r.id AND rds.deleted = 0 " +
                    "LEFT JOIN iam_data_scope ds ON ds.id = rds.data_scope_id AND ds.deleted = 0 " +
                    "WHERE ur.user_id = ? AND ur.tenant_id = ? AND ur.deleted = 0",
                    (rs, rowNum) -> new UserProfileResponse.DataScopeSummaryItem(
                            rs.getString("role_name"),
                            rs.getString("scope_code"),
                            rs.getString("scope_name"),
                            rs.getString("scope_type")
                    ),
                    userId, tenantId
            );
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<UserProfileResponse.IdentityBindingItem> queryIdentities(Long userId, Long tenantId) {
        try {
            return jdbcTemplate.query(
                    "SELECT identity_type, identifier, verified, status, bound_at " +
                    "FROM iam_user_identity WHERE user_id = ? AND tenant_id = ? AND deleted = 0 ORDER BY id ASC",
                    (rs, rowNum) -> new UserProfileResponse.IdentityBindingItem(
                            rs.getString("identity_type"),
                            rs.getString("identifier"),
                            rs.getBoolean("verified"),
                            rs.getString("status"),
                            rs.getTimestamp("bound_at") != null ? rs.getTimestamp("bound_at").toLocalDateTime() : null
                    ),
                    userId, tenantId
            );
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private UserProfileResponse.EmployeeProfileInfo resolveEmployeeInfo(UserEntity user, AuthPrincipal principal) {
        Long employeeId = user.getEmployeeId();
        String employeeNo = null;
        String employeeName = null;
        Long deptId = null;
        String deptName = null;
        String deptFullPath = null;
        Long positionId = null;
        String positionName = null;
        java.time.LocalDate hireDate = null;
        String employeeStatus = null;

        if (employeeId != null && employeeId > 0) {
            try {
                List<UserProfileResponse.EmployeeProfileInfo> rows = jdbcTemplate.query(
                        "SELECT e.id, e.employee_no, e.employee_name, e.dept_id, d.dept_name, d.dept_full_name, " +
                        "e.position_id, p.position_name, e.hire_date, e.employee_status " +
                        "FROM iam_employee e " +
                        "LEFT JOIN iam_department d ON d.id = e.dept_id AND d.deleted = 0 " +
                        "LEFT JOIN iam_position p ON p.id = e.position_id AND p.deleted = 0 " +
                        "WHERE e.id = ? AND e.deleted = 0 LIMIT 1",
                        (rs, rowNum) -> new UserProfileResponse.EmployeeProfileInfo(
                                rs.getLong("id"),
                                rs.getString("employee_no"),
                                rs.getString("employee_name"),
                                rs.getObject("dept_id") != null ? rs.getLong("dept_id") : null,
                                rs.getString("dept_name"),
                                rs.getString("dept_full_name"),
                                rs.getObject("position_id") != null ? rs.getLong("position_id") : null,
                                rs.getString("position_name"),
                                rs.getDate("hire_date") != null ? rs.getDate("hire_date").toLocalDate() : null,
                                rs.getString("employee_status"),
                                null, null, List.of()
                        ),
                        employeeId
                );
                if (!rows.isEmpty()) {
                    var r = rows.get(0);
                    employeeNo = r.employeeNo();
                    employeeName = r.employeeName();
                    deptId = r.deptId();
                    deptName = r.deptName();
                    deptFullPath = r.deptFullPath();
                    positionId = r.positionId();
                    positionName = r.positionName();
                    hireDate = r.hireDate();
                    employeeStatus = r.employeeStatus();
                }
            } catch (Exception ignored) {
            }
        }

        String personPublicId = principal.personPublicId() != null ? principal.personPublicId().toString() : null;
        String activeAssignmentPublicId = principal.organizationContext() != null
                && principal.organizationContext().assignmentPublicId() != null
                ? principal.organizationContext().assignmentPublicId().toString() : null;

        List<UserProfileResponse.AssignmentItem> assignments = new ArrayList<>();
        OrganizationDirectory directory = organizationDirectoryProvider != null
                ? organizationDirectoryProvider.getIfAvailable() : null;
        if (directory != null && principal.personPublicId() != null) {
            try {
                List<OrganizationDirectory.AssignmentSummary> raw =
                        directory.findEffectiveAssignments(principal.personPublicId(), Instant.now());
                for (OrganizationDirectory.AssignmentSummary a : raw) {
                    String orgName = resolveOrgName(a.organizationPublicId(), directory);
                    String unitName = resolveOrgUnitDisplayName(a.orgUnitPublicId());
                    String posName = resolveOrgPositionDisplayName(a.positionPublicId());
                    String aId = a.publicId().toString();
                    boolean active = activeAssignmentPublicId != null && activeAssignmentPublicId.equals(aId);
                    assignments.add(new UserProfileResponse.AssignmentItem(
                            aId, orgName, unitName, posName, a.primary(), active
                    ));
                }
            } catch (Exception ignored) {
            }
        }

        if (employeeId == null && personPublicId == null && assignments.isEmpty()) {
            return null;
        }
        return new UserProfileResponse.EmployeeProfileInfo(
                employeeId, employeeNo, employeeName, deptId, deptName, deptFullPath,
                positionId, positionName, hireDate, employeeStatus,
                personPublicId, activeAssignmentPublicId, assignments
        );
    }

    private String resolveOrgName(UUID organizationPublicId, OrganizationDirectory directory) {
        if (organizationPublicId == null) return null;
        try {
            return directory.getOrganization(organizationPublicId).displayName();
        } catch (Exception ignored) {
            return organizationPublicId.toString();
        }
    }

    private String resolveOrgUnitDisplayName(UUID orgUnitPublicId) {
        if (orgUnitPublicId == null) return null;
        try {
            List<String> rows = jdbcTemplate.query(
                    "SELECT unit_name FROM org_unit WHERE public_id = ? AND deleted = 0 LIMIT 1",
                    (rs, rowNum) -> rs.getString("unit_name"),
                    orgUnitPublicId.toString()
            );
            if (!rows.isEmpty() && rows.get(0) != null) return rows.get(0);
        } catch (Exception ignored) {
        }
        return orgUnitPublicId.toString();
    }

    private String resolveOrgPositionDisplayName(UUID positionPublicId) {
        if (positionPublicId == null) return null;
        try {
            List<String> rows = jdbcTemplate.query(
                    "SELECT position_name FROM org_position WHERE public_id = ? AND deleted = 0 LIMIT 1",
                    (rs, rowNum) -> rs.getString("position_name"),
                    positionPublicId.toString()
            );
            if (!rows.isEmpty() && rows.get(0) != null) return rows.get(0);
        } catch (Exception ignored) {
        }
        return positionPublicId.toString();
    }

    private List<UserProfileResponse.PersonalLoginLogItem> queryRecentLogs(Long userId) {
        try {
            return jdbcTemplate.query(
                    "SELECT id, login_ip, success, fail_reason, occurred_at FROM audit_login_log " +
                    "WHERE user_id = ? ORDER BY id DESC LIMIT 10",
                    (rs, rowNum) -> new UserProfileResponse.PersonalLoginLogItem(
                            rs.getLong("id"),
                            rs.getString("login_ip"),
                            rs.getBoolean("success"),
                            rs.getString("fail_reason"),
                            rs.getTimestamp("occurred_at") != null ? rs.getTimestamp("occurred_at").toLocalDateTime() : null
                    ),
                    userId
            );
        } catch (Exception ignored) {
            return List.of();
        }
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
