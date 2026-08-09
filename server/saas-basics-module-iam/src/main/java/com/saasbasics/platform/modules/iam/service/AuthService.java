package com.saasbasics.platform.modules.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.tenant.TenantContext;
import com.saasbasics.platform.config.SecurityProperties;
import com.saasbasics.platform.modules.audit.entity.LoginLogEntity;
import com.saasbasics.platform.modules.audit.mapper.LoginLogMapper;
import com.saasbasics.platform.modules.iam.dto.AuthCurrentUserResponse;
import com.saasbasics.platform.modules.iam.dto.AuthLoginRequest;
import com.saasbasics.platform.modules.iam.dto.AuthLoginResponse;
import com.saasbasics.platform.modules.iam.entity.AuthSessionEntity;
import com.saasbasics.platform.modules.iam.entity.LoginFailStatEntity;
import com.saasbasics.platform.modules.iam.entity.LoginPolicyEntity;
import com.saasbasics.platform.modules.iam.entity.PasswordHistoryEntity;
import com.saasbasics.platform.modules.iam.entity.PasswordPolicyEntity;
import com.saasbasics.platform.modules.iam.entity.PortalClientEntity;
import com.saasbasics.platform.modules.iam.entity.PortalTerminalEntity;
import com.saasbasics.platform.modules.iam.entity.UserEntity;
import com.saasbasics.platform.modules.iam.mapper.AuthSessionMapper;
import com.saasbasics.platform.modules.iam.mapper.LoginFailStatMapper;
import com.saasbasics.platform.modules.iam.mapper.LoginPolicyMapper;
import com.saasbasics.platform.modules.iam.mapper.PasswordHistoryMapper;
import com.saasbasics.platform.modules.iam.mapper.PasswordPolicyMapper;
import com.saasbasics.platform.modules.iam.mapper.RoleApiMapper;
import com.saasbasics.platform.modules.iam.mapper.UserMapper;
import com.saasbasics.platform.modules.iam.mapper.UserRoleMapper;
import com.saasbasics.platform.modules.tenant.entity.TenantEntity;
import com.saasbasics.platform.modules.tenant.mapper.TenantMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final List<String> DEFAULT_FEATURE_FLAGS = List.of(
            "saas.codegen.preview",
            "saas.tenant.package",
            "saas.file.versioning"
    );

    private final SecurityProperties securityProperties;
    private final PasswordSecurityService passwordSecurityService;
    private final ObjectProvider<UserMapper> userMapperProvider;
    private final ObjectProvider<TenantMapper> tenantMapperProvider;
    private final ObjectProvider<AuthSessionMapper> authSessionMapperProvider;
    private final ObjectProvider<LoginPolicyMapper> loginPolicyMapperProvider;
    private final ObjectProvider<PasswordPolicyMapper> passwordPolicyMapperProvider;
    private final ObjectProvider<LoginFailStatMapper> loginFailStatMapperProvider;
    private final ObjectProvider<PasswordHistoryMapper> passwordHistoryMapperProvider;
    private final ObjectProvider<LoginLogMapper> loginLogMapperProvider;
    private final ObjectProvider<UserRoleMapper> userRoleMapperProvider;
    private final ObjectProvider<RoleApiMapper> roleApiMapperProvider;
    private final PortalEntryService portalEntryService;
    private final MenuPermissionService menuPermissionService;

    public AuthService(SecurityProperties securityProperties,
                       PasswordSecurityService passwordSecurityService,
                       ObjectProvider<UserMapper> userMapperProvider,
                       ObjectProvider<TenantMapper> tenantMapperProvider,
                       ObjectProvider<AuthSessionMapper> authSessionMapperProvider,
                       ObjectProvider<LoginPolicyMapper> loginPolicyMapperProvider,
                       ObjectProvider<PasswordPolicyMapper> passwordPolicyMapperProvider,
                       ObjectProvider<LoginFailStatMapper> loginFailStatMapperProvider,
                       ObjectProvider<PasswordHistoryMapper> passwordHistoryMapperProvider,
                       ObjectProvider<LoginLogMapper> loginLogMapperProvider,
                       ObjectProvider<UserRoleMapper> userRoleMapperProvider,
                       ObjectProvider<RoleApiMapper> roleApiMapperProvider,
                       PortalEntryService portalEntryService,
                       MenuPermissionService menuPermissionService) {
        this.securityProperties = securityProperties;
        this.passwordSecurityService = passwordSecurityService;
        this.userMapperProvider = userMapperProvider;
        this.tenantMapperProvider = tenantMapperProvider;
        this.authSessionMapperProvider = authSessionMapperProvider;
        this.loginPolicyMapperProvider = loginPolicyMapperProvider;
        this.passwordPolicyMapperProvider = passwordPolicyMapperProvider;
        this.loginFailStatMapperProvider = loginFailStatMapperProvider;
        this.passwordHistoryMapperProvider = passwordHistoryMapperProvider;
        this.loginLogMapperProvider = loginLogMapperProvider;
        this.userRoleMapperProvider = userRoleMapperProvider;
        this.roleApiMapperProvider = roleApiMapperProvider;
        this.portalEntryService = portalEntryService;
        this.menuPermissionService = menuPermissionService;
    }

    public AuthLoginResponse login(AuthLoginRequest request, String loginIp, String userAgent) {
        TenantContext.setTenantCode(request.tenantCode());

        UserMapper userMapper = requiredUserMapper();
        TenantMapper tenantMapper = requiredTenantMapper();

        TenantEntity tenant = tenantMapper.selectOne(new LambdaQueryWrapper<TenantEntity>()
                .eq(TenantEntity::getTenantCode, request.tenantCode())
                .eq(TenantEntity::getDeleted, 0)
                .last("LIMIT 1"));
        if (tenant == null) {
            throw new BizException("TENANT_NOT_FOUND", "租户不存在");
        }
        if (!"ENABLED".equalsIgnoreCase(tenant.getStatus())) {
            throw new BizException("TENANT_DISABLED", "租户已停用");
        }

        PortalLoginContext portalLoginContext = resolvePortalLoginContext(request, tenant);
        LoginPolicyEntity loginPolicy = currentLoginPolicy(tenant.getId(), portalLoginContext.loginPolicyId());
        if (!Boolean.TRUE.equals(loginPolicy.getAllowPasswordLogin())) {
            throw new BizException("LOGIN_POLICY_BLOCKED", "当前租户已关闭密码登录");
        }

        UserEntity user = userMapper.selectByTenantAndUsername(tenant.getId(), request.username());
        if (user == null) {
            recordLoginFailure(tenant.getId(), request.username(), 0L, request.password(), loginIp, userAgent, "用户名或密码错误", loginPolicy);
            throw new BizException("AUTH_INVALID_CREDENTIALS", "账号或密码错误");
        }
        if (!"ENABLED".equalsIgnoreCase(user.getStatus())) {
            throw new BizException("AUTH_USER_DISABLED", "用户已停用");
        }

        LoginFailStatEntity failStat = currentFailStat(tenant.getId(), user.getId());
        ensureNotLocked(failStat);

        PasswordPolicyEntity passwordPolicy = currentPasswordPolicy(tenant.getId(), portalLoginContext.passwordPolicyId());
        if (!passwordSecurityService.matches(request.password(), user.getPasswordHash())) {
            recordLoginFailure(tenant.getId(), user.getUsername(), user.getId(), request.password(), loginIp, userAgent, "用户名或密码错误", loginPolicy);
            throw new BizException("AUTH_INVALID_CREDENTIALS", "账号或密码错误");
        }
        validatePasswordExpiry(passwordPolicy, user);

        clearFailStat(failStat);
        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(loginIp);
        userMapper.updateById(user);
        persistPasswordHistoryIfNeeded(user, passwordPolicy);

        String accessToken = generateAccessToken();
        String tokenHash = tokenHash(accessToken);
        LocalDateTime expireAt = LocalDateTime.now().plusMinutes(resolveSessionTtl(loginPolicy));

        AuthSessionEntity session = new AuthSessionEntity();
        session.setTenantId(tenant.getId());
        session.setTenantCode(tenant.getTenantCode());
        session.setSessionNo("SESS-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase());
        session.setUserId(user.getId());
        session.setUsername(user.getUsername());
        session.setNickname(user.getNickname());
        session.setUserType(user.getUserType());
        session.setLoginType("PASSWORD");
        session.setAccessTokenHash(tokenHash);
        session.setLoginIp(loginIp);
        session.setUserAgent(userAgent);
        session.setExpireAt(expireAt);
        session.setLastAccessAt(LocalDateTime.now());
        session.setStatus("ONLINE");
        requiredSessionMapper().insert(session);

        logLogin(tenant.getId(), user.getId(), user.getUsername(), loginIp, userAgent, true, null);
        return new AuthLoginResponse(accessToken, "Bearer", expireAt, toCurrentUserResponse(toPrincipal(session)));
    }

    public AuthCurrentUserResponse currentUser() {
        AuthPrincipal principal = requiredPrincipal();
        return toCurrentUserResponse(principal);
    }

    public void logout(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BizException("AUTH_TOKEN_MISSING", "Access token is required");
        }

        AuthSessionMapper sessionMapper = requiredSessionMapper();

        AuthSessionEntity session = sessionMapper.selectOnlineByTokenHash(tokenHash(accessToken), LocalDateTime.now());
        if (session == null) {
            return;
        }
        session.setStatus("OFFLINE");
        session.setLogoutAt(LocalDateTime.now());
        session.setLastAccessAt(LocalDateTime.now());
        sessionMapper.updateById(session);
    }

    public AuthPrincipal resolvePrincipal(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return null;
        }

        AuthSessionMapper sessionMapper = requiredSessionMapper();

        AuthSessionEntity session = sessionMapper.selectOnlineByTokenHash(tokenHash(accessToken), LocalDateTime.now());
        if (session == null) {
            return null;
        }

        TenantEntity tenant = requiredTenantMapper().selectById(session.getTenantId());
        if (tenant == null || tenant.getDeleted() != null && tenant.getDeleted() == 1 || !"ENABLED".equalsIgnoreCase(tenant.getStatus())) {
            session.setStatus("OFFLINE");
            session.setLogoutAt(LocalDateTime.now());
            session.setLastAccessAt(LocalDateTime.now());
            sessionMapper.updateById(session);
            return null;
        }

        UserEntity user = requiredUserMapper().selectById(session.getUserId());
        if (user == null || user.getDeleted() != null && user.getDeleted() == 1 || !"ENABLED".equalsIgnoreCase(user.getStatus())) {
            session.setStatus("OFFLINE");
            session.setLogoutAt(LocalDateTime.now());
            session.setLastAccessAt(LocalDateTime.now());
            sessionMapper.updateById(session);
            return null;
        }

        session.setLastAccessAt(LocalDateTime.now());
        sessionMapper.updateById(session);
        return toPrincipal(session);
    }

    private LoginPolicyEntity currentLoginPolicy(Long tenantId, Long policyId) {
        LoginPolicyMapper mapper = requiredLoginPolicyMapper();
        if (policyId != null && policyId > 0) {
            LoginPolicyEntity policy = mapper.selectById(policyId);
            if (policy != null && (policy.getDeleted() == null || policy.getDeleted() == 0) && "ENABLED".equalsIgnoreCase(policy.getStatus())) {
                return policy;
            }
        }
        LoginPolicyEntity policy = mapper.selectActivePolicy(tenantId);
        if (policy == null) {
            throw new BizException("LOGIN_POLICY_NOT_FOUND", "当前租户未配置可用的登录策略");
        }
        return policy;
    }

    private PasswordPolicyEntity currentPasswordPolicy(Long tenantId, Long policyId) {
        PasswordPolicyMapper mapper = requiredPasswordPolicyMapper();
        if (policyId != null && policyId > 0) {
            PasswordPolicyEntity policy = mapper.selectById(policyId);
            if (policy != null && (policy.getDeleted() == null || policy.getDeleted() == 0) && "ENABLED".equalsIgnoreCase(policy.getStatus())) {
                return policy;
            }
        }
        PasswordPolicyEntity policy = mapper.selectActivePolicy(tenantId);
        if (policy == null) {
            throw new BizException("PASSWORD_POLICY_NOT_FOUND", "当前租户未配置可用的密码策略");
        }
        return policy;
    }

    private void validatePasswordExpiry(PasswordPolicyEntity policy, UserEntity user) {
        if (policy == null || policy.getPasswordExpireDays() == null || policy.getPasswordExpireDays() <= 0) {
            return;
        }
        if (user.getPasswordChangedAt() == null) {
            return;
        }
        if (user.getPasswordChangedAt().plusDays(policy.getPasswordExpireDays()).isBefore(LocalDateTime.now())) {
            throw new BizException("PASSWORD_EXPIRED", "密码已过期，请联系管理员重置");
        }
    }

    private PortalLoginContext resolvePortalLoginContext(AuthLoginRequest request, TenantEntity tenant) {
        if ((request.clientId() == null || request.clientId().isBlank())
                && (request.terminalCode() == null || request.terminalCode().isBlank())) {
            return new PortalLoginContext(null, null);
        }

        PortalClientEntity client = portalEntryService.requireActiveClient(request.clientId());
        if (!tenant.getId().equals(client.getTenantId()) || !tenant.getTenantCode().equals(client.getTenantCode())) {
            throw new BizException("PORTAL_TENANT_MISMATCH", "当前入口不属于该租户");
        }

        PortalTerminalEntity terminal = portalEntryService.requireActiveTerminal(client.getId(), request.terminalCode());
        return new PortalLoginContext(
                terminal.getLoginPolicyId() != null ? terminal.getLoginPolicyId() : client.getLoginPolicyId(),
                terminal.getPasswordPolicyId() != null ? terminal.getPasswordPolicyId() : client.getPasswordPolicyId()
        );
    }

    private record PortalLoginContext(Long loginPolicyId, Long passwordPolicyId) {
    }

    private LoginFailStatEntity currentFailStat(Long tenantId, Long userId) {
        LoginFailStatMapper mapper = loginFailStatMapperProvider.getIfAvailable();
        if (mapper == null) {
            return null;
        }
        return mapper.selectOne(new LambdaQueryWrapper<LoginFailStatEntity>()
                .eq(LoginFailStatEntity::getTenantId, tenantId)
                .eq(LoginFailStatEntity::getUserId, userId)
                .eq(LoginFailStatEntity::getDeleted, 0)
                .last("LIMIT 1"));
    }

    private void ensureNotLocked(LoginFailStatEntity failStat) {
        if (failStat == null || failStat.getLockedUntil() == null) {
            return;
        }
        if (failStat.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new BizException("AUTH_ACCOUNT_LOCKED", "Account is temporarily locked");
        }
    }

    private void recordLoginFailure(Long tenantId, String username, Long userId, String rawPassword, String loginIp,
                                    String userAgent, String reason, LoginPolicyEntity loginPolicy) {
        logLogin(tenantId, userId, username, loginIp, userAgent, false, reason);
        LoginFailStatMapper mapper = loginFailStatMapperProvider.getIfAvailable();
        if (mapper == null || userId == null || userId <= 0) {
            return;
        }
        LoginFailStatEntity stat = mapper.selectOne(new LambdaQueryWrapper<LoginFailStatEntity>()
                .eq(LoginFailStatEntity::getTenantId, tenantId)
                .eq(LoginFailStatEntity::getUserId, userId)
                .eq(LoginFailStatEntity::getDeleted, 0)
                .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        if (stat == null) {
            stat = new LoginFailStatEntity();
            stat.setTenantId(tenantId);
            stat.setUserId(userId);
            stat.setUsername(username);
            stat.setFailedCount(1);
            stat.setFirstFailedAt(now);
            stat.setLastFailedAt(now);
            stat.setLastLoginIp(loginIp);
            if (loginPolicy.getMaxFailedCount() != null && loginPolicy.getLockMinutes() != null
                    && loginPolicy.getMaxFailedCount() <= 1) {
                stat.setLockedUntil(now.plusMinutes(loginPolicy.getLockMinutes()));
            }
            mapper.insert(stat);
            return;
        }
        int failedCount = stat.getFailedCount() == null ? 0 : stat.getFailedCount();
        stat.setFailedCount(failedCount + 1);
        stat.setLastFailedAt(now);
        stat.setLastLoginIp(loginIp);
        if (loginPolicy.getMaxFailedCount() != null
                && loginPolicy.getLockMinutes() != null
                && stat.getFailedCount() >= loginPolicy.getMaxFailedCount()) {
            stat.setLockedUntil(now.plusMinutes(loginPolicy.getLockMinutes()));
        }
        mapper.updateById(stat);
    }

    private void clearFailStat(LoginFailStatEntity failStat) {
        LoginFailStatMapper mapper = loginFailStatMapperProvider.getIfAvailable();
        if (mapper == null || failStat == null) {
            return;
        }
        failStat.setFailedCount(0);
        failStat.setFirstFailedAt(null);
        failStat.setLastFailedAt(null);
        failStat.setLockedUntil(null);
        mapper.updateById(failStat);
    }

    private void persistPasswordHistoryIfNeeded(UserEntity user, PasswordPolicyEntity passwordPolicy) {
        PasswordHistoryMapper mapper = passwordHistoryMapperProvider.getIfAvailable();
        if (mapper == null || user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            return;
        }
        long count = mapper.selectCount(new LambdaQueryWrapper<PasswordHistoryEntity>()
                .eq(PasswordHistoryEntity::getTenantId, user.getTenantId())
                .eq(PasswordHistoryEntity::getUserId, user.getId())
                .eq(PasswordHistoryEntity::getPasswordHash, user.getPasswordHash())
                .eq(PasswordHistoryEntity::getDeleted, 0));
        if (count > 0) {
            return;
        }
        PasswordHistoryEntity history = new PasswordHistoryEntity();
        history.setTenantId(user.getTenantId());
        history.setUserId(user.getId());
        history.setPasswordHash(user.getPasswordHash());
        history.setChangedAt(user.getPasswordChangedAt() == null ? LocalDateTime.now() : user.getPasswordChangedAt());
        history.setChangeReason("LOGIN_BASELINE");
        mapper.insert(history);

        Integer historyLimit = passwordPolicy == null ? null : passwordPolicy.getPasswordHistoryLimit();
        if (historyLimit == null || historyLimit <= 0) {
            return;
        }
        Long removableId = mapper.selectList(new LambdaQueryWrapper<PasswordHistoryEntity>()
                        .eq(PasswordHistoryEntity::getTenantId, user.getTenantId())
                        .eq(PasswordHistoryEntity::getUserId, user.getId())
                        .eq(PasswordHistoryEntity::getDeleted, 0)
                        .orderByDesc(PasswordHistoryEntity::getChangedAt))
                .stream()
                .skip(historyLimit)
                .map(PasswordHistoryEntity::getId)
                .findFirst()
                .orElse(null);
        if (removableId != null) {
            mapper.deleteById(removableId);
        }
    }

    private void logLogin(Long tenantId, Long userId, String username, String loginIp, String userAgent,
                          boolean success, String failReason) {
        LoginLogMapper mapper = loginLogMapperProvider.getIfAvailable();
        if (mapper == null) {
            return;
        }
        LoginLogEntity entity = new LoginLogEntity();
        entity.setTenantId(tenantId);
        entity.setUserId(userId == null ? 0L : userId);
        entity.setUsername(username);
        entity.setIdentityType("USERNAME");
        entity.setLoginType("PASSWORD");
        entity.setLoginIp(loginIp);
        entity.setDeviceInfo(userAgent);
        entity.setSuccess(success);
        entity.setFailReason(failReason);
        entity.setOccurredAt(LocalDateTime.now());
        mapper.insert(entity);
    }

    private long resolveSessionTtl(LoginPolicyEntity policy) {
        if (policy == null || policy.getSessionTimeoutMinutes() == null || policy.getSessionTimeoutMinutes() <= 0) {
            return securityProperties.getAuth().getSessionTtlMinutes();
        }
        return policy.getSessionTimeoutMinutes();
    }

    private AuthSessionMapper requiredSessionMapper() {
        AuthSessionMapper mapper = authSessionMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "Session persistence requires the db profile and MySQL connection");
        }
        return mapper;
    }

    private AuthPrincipal toPrincipal(AuthSessionEntity session) {
        List<Long> roleIds = loadRoleIds(session.getTenantId(), session.getUserId());
        AuthPrincipal basePrincipal = new AuthPrincipal(
                session.getId(),
                session.getTenantId(),
                session.getTenantCode(),
                session.getUserId(),
                session.getUsername(),
                session.getNickname(),
                session.getUserType(),
                session.getStatus(),
                session.getExpireAt(),
                loadPermissions(session.getTenantId(), session.getUserId()),
                DEFAULT_FEATURE_FLAGS,
                roleIds,
                List.of()
        );
        return new AuthPrincipal(
                basePrincipal.sessionId(),
                basePrincipal.tenantId(),
                basePrincipal.tenantCode(),
                basePrincipal.userId(),
                basePrincipal.username(),
                basePrincipal.nickname(),
                basePrincipal.userType(),
                basePrincipal.sessionStatus(),
                basePrincipal.expireAt(),
                basePrincipal.permissions(),
                basePrincipal.featureFlags(),
                basePrincipal.roleIds(),
                menuPermissionService.resolveButtonPermissions(basePrincipal)
        );
    }

    private AuthCurrentUserResponse toCurrentUserResponse(AuthPrincipal principal) {
        return new AuthCurrentUserResponse(
                principal.sessionId(),
                principal.tenantId(),
                principal.tenantCode(),
                principal.userId(),
                principal.username(),
                principal.nickname(),
                principal.userType(),
                principal.sessionStatus(),
                principal.expireAt(),
                principal.permissions(),
                principal.featureFlags(),
                principal.roleIds(),
                principal.buttonPermissions()
        );
    }

    private AuthPrincipal requiredPrincipal() {
        AuthPrincipal principal = AuthContext.get();
        if (principal == null) {
            throw new BizException("AUTH_UNAUTHORIZED", "Authentication is required");
        }
        return principal;
    }

    private String generateAccessToken() {
        byte[] raw = UUID.randomUUID().toString().concat(UUID.randomUUID().toString()).getBytes(StandardCharsets.UTF_8);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private String tokenHash(String accessToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(accessToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    private List<String> loadPermissions(Long tenantId, Long userId) {
        UserRoleMapper userRoleMapper = userRoleMapperProvider.getIfAvailable();
        RoleApiMapper roleApiMapper = roleApiMapperProvider.getIfAvailable();
        if (userRoleMapper == null || roleApiMapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "Permission resolution requires the db profile and MySQL connection");
        }
        if (tenantId == null || userId == null) {
            return List.of();
        }
        List<String> permissions = roleApiMapper.selectPermissionCodesByUserId(tenantId, userId, LocalDateTime.now());
        return permissions == null ? List.of() : permissions;
    }

    private List<Long> loadRoleIds(Long tenantId, Long userId) {
        UserRoleMapper userRoleMapper = userRoleMapperProvider.getIfAvailable();
        if (userRoleMapper == null || tenantId == null || userId == null) {
            return List.of();
        }
        List<Long> roleIds = userRoleMapper.selectRoleIdsByUserId(tenantId, userId, LocalDateTime.now());
        return roleIds == null ? List.of() : roleIds;
    }

    private UserMapper requiredUserMapper() {
        UserMapper mapper = userMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "User authentication requires the db profile and MySQL connection");
        }
        return mapper;
    }

    private TenantMapper requiredTenantMapper() {
        TenantMapper mapper = tenantMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "Tenant resolution requires the db profile and MySQL connection");
        }
        return mapper;
    }

    private LoginPolicyMapper requiredLoginPolicyMapper() {
        LoginPolicyMapper mapper = loginPolicyMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "Login policy resolution requires the db profile and MySQL connection");
        }
        return mapper;
    }

    private PasswordPolicyMapper requiredPasswordPolicyMapper() {
        PasswordPolicyMapper mapper = passwordPolicyMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "Password policy resolution requires the db profile and MySQL connection");
        }
        return mapper;
    }
}
