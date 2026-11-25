package com.saasbasics.platform.modules.iam.service;

import org.springframework.transaction.annotation.Isolation;
import com.saasbasics.platform.common.tenant.TenantMembershipCapacity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.id.UuidV7Generator;
import com.saasbasics.platform.common.tenant.TenantAccessContext;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.modules.iam.dto.SelfServiceAccountDtos.PasswordResetConfirmRequest;
import com.saasbasics.platform.modules.iam.dto.SelfServiceAccountDtos.PasswordResetSendCodeRequest;
import com.saasbasics.platform.modules.iam.dto.SelfServiceAccountDtos.PasswordResetSendCodeResponse;
import com.saasbasics.platform.modules.iam.dto.SelfServiceAccountDtos.TenantInviteAcceptRequest;
import com.saasbasics.platform.modules.iam.dto.SelfServiceAccountDtos.TenantInviteCreateRequest;
import com.saasbasics.platform.modules.iam.dto.SelfServiceAccountDtos.TenantInviteResponse;
import com.saasbasics.platform.modules.iam.entity.AuthSessionEntity;
import com.saasbasics.platform.modules.iam.entity.PasswordPolicyEntity;
import com.saasbasics.platform.modules.iam.entity.TenantInviteEntity;
import com.saasbasics.platform.modules.iam.entity.UserEntity;
import com.saasbasics.platform.modules.iam.entity.UserRoleEntity;
import com.saasbasics.platform.modules.iam.mapper.AuthSessionMapper;
import com.saasbasics.platform.modules.iam.mapper.PasswordPolicyMapper;
import com.saasbasics.platform.modules.iam.mapper.TenantInviteMapper;
import com.saasbasics.platform.modules.iam.mapper.UserMapper;
import com.saasbasics.platform.modules.iam.mapper.UserRoleMapper;
import com.saasbasics.platform.modules.tenant.entity.TenantEntity;
import com.saasbasics.platform.modules.tenant.mapper.TenantMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 租户用户自助密码重置 (OTP) 与企业成员邀请链接自助入驻服务。
 */
@Service
public class SelfServiceAccountService {

    private static final Logger log = LoggerFactory.getLogger(SelfServiceAccountService.class);
    private static final int OTP_TTL_SECONDS = 300;

    private final TenantMembershipCapacity capacity;
    private final PasswordSecurityService passwordSecurityService;
    private final ObjectProvider<TenantInviteMapper> inviteMapperProvider;
    private final ObjectProvider<UserMapper> userMapperProvider;
    private final ObjectProvider<TenantMapper> tenantMapperProvider;
    private final ObjectProvider<PasswordPolicyMapper> passwordPolicyMapperProvider;
    private final ObjectProvider<AuthSessionMapper> authSessionMapperProvider;
    private final ObjectProvider<UserRoleMapper> userRoleMapperProvider;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final Environment environment;
    private final SecureRandom secureRandom = new SecureRandom();
    private final UuidV7Generator uuidGenerator = new UuidV7Generator();
    private final ConcurrentHashMap<String, OtpRecord> localOtpStore = new ConcurrentHashMap<>();

    @Autowired
    public SelfServiceAccountService(
            PasswordSecurityService passwordSecurityService,
            ObjectProvider<TenantInviteMapper> inviteMapperProvider,
            ObjectProvider<UserMapper> userMapperProvider,
            ObjectProvider<TenantMapper> tenantMapperProvider,
            ObjectProvider<PasswordPolicyMapper> passwordPolicyMapperProvider,
            ObjectProvider<AuthSessionMapper> authSessionMapperProvider,
            ObjectProvider<UserRoleMapper> userRoleMapperProvider,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            Environment environment,
            TenantMembershipCapacity capacity
    ) {
        this.capacity = capacity;
        this.passwordSecurityService = passwordSecurityService;
        this.inviteMapperProvider = inviteMapperProvider;
        this.userMapperProvider = userMapperProvider;
        this.tenantMapperProvider = tenantMapperProvider;
        this.passwordPolicyMapperProvider = passwordPolicyMapperProvider;
        this.authSessionMapperProvider = authSessionMapperProvider;
        this.userRoleMapperProvider = userRoleMapperProvider;
        this.redisTemplateProvider = redisTemplateProvider;
        this.environment = environment;
    }

    public PasswordResetSendCodeResponse sendPasswordResetCode(PasswordResetSendCodeRequest request) {
        String tenantCode = (request.tenantCode() != null && !request.tenantCode().isBlank())
                ? request.tenantCode().trim()
                : "platform";
        String username = request.username().trim();
        String contact = request.contact().trim();
        String deliveryChannel = contact.contains("@") ? "EMAIL" : "SMS";
        boolean isProd = environment != null && environment.acceptsProfiles(Profiles.of("prod"));

        TenantEntity tenant = findActiveTenantByCode(tenantCode);
        if (tenant == null) {
            if (isProd) {
                return new PasswordResetSendCodeResponse(true, OTP_TTL_SECONDS, deliveryChannel, null);
            }
            throw new BizException("TENANT_NOT_FOUND", "租户不存在: " + tenantCode);
        }

        try (TenantAccessContextHolder.Scope ignored = TenantAccessContextHolder.openTenant(
                tenant.getId(), tenant.getTenantCode(), null)) {
            UserEntity user = requiredUserMapper().selectByTenantAndUsername(tenant.getId(), username);
            if (user == null || !"ENABLED".equalsIgnoreCase(user.getStatus())) {
                if (isProd) {
                    return new PasswordResetSendCodeResponse(true, OTP_TTL_SECONDS, deliveryChannel, null);
                }
                throw new BizException("USER_NOT_FOUND", "账号不存在或已停用");
            }
            if (!matchesRegisteredContact(user, contact, isProd)) {
                if (isProd) {
                    return new PasswordResetSendCodeResponse(true, OTP_TTL_SECONDS, deliveryChannel, null);
                }
                throw new BizException("PASSWORD_RESET_CONTACT_MISMATCH", "输入的邮箱或手机号与账号绑定信息不匹配");
            }

            String otpCode = String.format("%06d", secureRandom.nextInt(1_000_000));
            storeOtp(otpKey(tenant.getTenantCode(), username), otpCode);
            log.info("[PasswordReset] Dispatched {} OTP for tenant={} user={}", deliveryChannel, tenant.getTenantCode(), username);

            return new PasswordResetSendCodeResponse(
                    true,
                    OTP_TTL_SECONDS,
                    deliveryChannel,
                    isProd ? null : otpCode
            );
        }
    }

    @Transactional
    public void confirmPasswordReset(PasswordResetConfirmRequest request) {
        String tenantCode = (request.tenantCode() != null && !request.tenantCode().isBlank())
                ? request.tenantCode().trim()
                : "platform";
        String username = request.username().trim();
        String otpKey = otpKey(tenantCode, username);
        verifyAndConsumeOtp(otpKey, request.otpCode().trim());

        TenantEntity tenant = findActiveTenantByCode(tenantCode);
        if (tenant == null) {
            throw new BizException("TENANT_NOT_FOUND", "租户不存在: " + tenantCode);
        }

        try (TenantAccessContextHolder.Scope ignored = TenantAccessContextHolder.openTenant(
                tenant.getId(), tenant.getTenantCode(), null)) {
            UserMapper userMapper = requiredUserMapper();
            UserEntity user = userMapper.selectByTenantAndUsername(tenant.getId(), username);
            if (user == null || !"ENABLED".equalsIgnoreCase(user.getStatus())) {
                throw new BizException("USER_NOT_FOUND", "账号不存在或已停用");
            }

            PasswordPolicyMapper policyMapper = passwordPolicyMapperProvider != null
                    ? passwordPolicyMapperProvider.getIfAvailable() : null;
            if (policyMapper != null) {
                PasswordPolicyEntity policy = policyMapper.selectActivePolicy(tenant.getId());
                passwordSecurityService.validate(request.newPassword(), policy);
            } else if (request.newPassword().length() < 8) {
                throw new BizException("PASSWORD_POLICY_VIOLATION", "新密码长度不能少于 8 位");
            }

            user.setPasswordHash(passwordSecurityService.hash(request.newPassword()));
            user.setPasswordChangedAt(LocalDateTime.now());
            user.setNeedResetPassword(false);
            userMapper.updateById(user);

            AuthSessionMapper sessionMapper = authSessionMapperProvider != null
                    ? authSessionMapperProvider.getIfAvailable() : null;
            if (sessionMapper != null) {
                LocalDateTime now = LocalDateTime.now();
                sessionMapper.update(null, new UpdateWrapper<AuthSessionEntity>()
                        .eq("tenant_id", tenant.getId())
                        .eq("user_id", user.getId())
                        .eq("status", "ONLINE")
                        .set("status", "OFFLINE")
                        .set("logout_at", now));
            }
        }
    }

    @Transactional
    public TenantInviteResponse createInvite(TenantInviteCreateRequest request) {
        AuthPrincipal principal = AuthContext.get();
        if (principal == null || principal.tenantId() == null) {
            throw new BizException("AUTH_UNAUTHORIZED", "请先登录后生成邀请链接");
        }
        int maxUses = request != null && request.maxUses() != null
                ? Math.max(1, Math.min(100, request.maxUses())) : 1;
        int expireHours = request != null && request.expireHours() != null
                ? Math.max(1, Math.min(720, request.expireHours())) : 72;

        byte[] tokenBytes = new byte[18];
        secureRandom.nextBytes(tokenBytes);
        String inviteToken = "inv_" + HexFormat.of().formatHex(tokenBytes);
        LocalDateTime now = LocalDateTime.now();

        TenantInviteEntity entity = new TenantInviteEntity();
        entity.setInviteToken(inviteToken);
        entity.setTenantId(principal.tenantId());
        entity.setInviterUserId(principal.userId() != null ? principal.userId() : 0L);
        entity.setInvitedEmail(request != null && request.invitedEmail() != null && !request.invitedEmail().isBlank()
                ? request.invitedEmail().trim() : null);
        entity.setRoleId(request != null ? request.roleId() : null);
        entity.setMaxUses(maxUses);
        entity.setUsedCount(0);
        entity.setStatus("ACTIVE");
        entity.setExpireAt(now.plusHours(expireHours));
        entity.setCreatedAt(now);

        requiredInviteMapper().insert(entity);
        TenantEntity tenant = findTenantById(principal.tenantId());
        return toInviteResponse(entity, tenant);
    }

    public List<TenantInviteResponse> listInvites() {
        AuthPrincipal principal = AuthContext.get();
        if (principal == null || principal.tenantId() == null) {
            throw new BizException("AUTH_UNAUTHORIZED", "未登录");
        }
        TenantEntity tenant = findTenantById(principal.tenantId());
        return requiredInviteMapper().selectList(new LambdaQueryWrapper<TenantInviteEntity>()
                        .eq(TenantInviteEntity::getTenantId, principal.tenantId())
                        .orderByDesc(TenantInviteEntity::getId)
                        .last("LIMIT 30"))
                .stream()
                .map(invite -> toInviteResponse(invite, tenant))
                .toList();
    }

    public TenantInviteResponse previewInvite(String token) {
        ResolvedInvite resolved = loadValidInvite(token);
        return toInviteResponse(resolved.invite(), resolved.tenant());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TenantInviteResponse acceptInvite(String token, TenantInviteAcceptRequest request) {
        ResolvedInvite resolved = loadValidInvite(token);
        TenantInviteEntity invite = resolved.invite();
        TenantEntity tenant = resolved.tenant();

        try (TenantAccessContextHolder.Scope ignored = TenantAccessContextHolder.openTenant(
                tenant.getId(), tenant.getTenantCode(), invite.getInviterUserId())) {
            capacity.requireSeat(tenant.getId());
            // Another invite acceptance may have committed while this transaction waited for capacity.
            invite = requiredInviteMapper().selectById(invite.getId());
            validateInvite(invite);
            UserMapper userMapper = requiredUserMapper();
            String username = request.username().trim();
            UserEntity existing = userMapper.selectByTenantAndUsername(tenant.getId(), username);
            if (existing != null) {
                throw new BizException("IAM_USERNAME_EXISTS", "用户名已存在于目标租户: " + username);
            }

            PasswordPolicyMapper policyMapper = passwordPolicyMapperProvider != null
                    ? passwordPolicyMapperProvider.getIfAvailable() : null;
            if (policyMapper != null) {
                PasswordPolicyEntity policy = policyMapper.selectActivePolicy(tenant.getId());
                passwordSecurityService.validate(request.password(), policy);
            } else if (request.password().length() < 8) {
                throw new BizException("PASSWORD_POLICY_VIOLATION", "密码长度不能少于 8 位");
            }

            UserEntity user = new UserEntity();
            user.setTenantId(tenant.getId());
            user.setPublicId(uuidGenerator.generate().toString());
            user.setUserCode("USR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT));
            user.setUsername(username);
            user.setNickname(request.nickname().trim());
            user.setEmail(request.email() != null && !request.email().isBlank()
                    ? request.email().trim()
                    : invite.getInvitedEmail());
            user.setMobile(request.mobile() != null && !request.mobile().isBlank() ? request.mobile().trim() : null);
            user.setUserType("TENANT_USER");
            user.setStatus("ENABLED");
            user.setPasswordHash(passwordSecurityService.hash(request.password()));
            user.setPasswordChangedAt(LocalDateTime.now());
            user.setNeedResetPassword(false);
            user.setMfaEnabled(false);
            user.setDeleted(0);
            userMapper.insert(user);

            UserRoleMapper userRoleMapper = userRoleMapperProvider != null
                    ? userRoleMapperProvider.getIfAvailable() : null;
            if (invite.getRoleId() != null && invite.getRoleId() > 0 && userRoleMapper != null) {
                UserRoleEntity userRole = new UserRoleEntity();
                userRole.setTenantId(tenant.getId());
                userRole.setUserId(user.getId());
                userRole.setRoleId(invite.getRoleId());
                userRole.setSourceType("INVITE");
                userRole.setDeleted(0);
                userRoleMapper.insert(userRole);
            }

            int nextUsed = (invite.getUsedCount() == null ? 0 : invite.getUsedCount()) + 1;
            int maxUses = invite.getMaxUses() == null ? 1 : invite.getMaxUses();
            invite.setUsedCount(nextUsed);
            if (nextUsed >= maxUses) {
                invite.setStatus("EXHAUSTED");
            }
            requiredInviteMapper().updateById(invite);
            return toInviteResponse(invite, tenant);
        }
    }

    private ResolvedInvite loadValidInvite(String token) {
        if (token == null || token.isBlank()) {
            throw new BizException("INVITE_TOKEN_INVALID", "邀请凭证不能为空");
        }
        TenantInviteEntity invite;
        try (TenantAccessContextHolder.Scope ignored = TenantAccessContextHolder.openSystemBypass(
                TenantAccessContext.BypassOperation.PUBLIC_PORTAL_RESOLUTION,
                "Resolve tenant member invitation by token")) {
            invite = requiredInviteMapper().selectOne(new LambdaQueryWrapper<TenantInviteEntity>()
                    .eq(TenantInviteEntity::getInviteToken, token.trim())
                    .last("LIMIT 1"));
        }
        validateInvite(invite);
        TenantEntity tenant = findTenantById(invite.getTenantId());
        if (tenant == null || "DISABLED".equalsIgnoreCase(tenant.getStatus()) || "SUSPENDED".equalsIgnoreCase(tenant.getStatus())) {
            throw new BizException("TENANT_DISABLED", "邀请所属租户不存在或已停用");
        }
        return new ResolvedInvite(invite, tenant);
    }

    private void validateInvite(TenantInviteEntity invite) {
        if (invite == null) {
            throw new BizException("INVITE_TOKEN_NOT_FOUND", "邀请链接不存在或已失效");
        }
        if (!"ACTIVE".equalsIgnoreCase(invite.getStatus())) {
            throw new BizException("INVITE_TOKEN_CONSUMED", "邀请链接已被使用或已作废");
        }
        if (invite.getExpireAt() != null && invite.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new BizException("INVITE_TOKEN_EXPIRED", "邀请链接已过期");
        }
        int used = invite.getUsedCount() == null ? 0 : invite.getUsedCount();
        int maxUses = invite.getMaxUses() == null ? 1 : invite.getMaxUses();
        if (used >= maxUses) {
            throw new BizException("INVITE_TOKEN_EXHAUSTED", "邀请链接已达到最大使用次数");
        }
    }

    private boolean matchesRegisteredContact(UserEntity user, String contact, boolean isProd) {
        boolean hasEmail = user.getEmail() != null && !user.getEmail().isBlank();
        boolean hasMobile = user.getMobile() != null && !user.getMobile().isBlank();
        if (!hasEmail && !hasMobile) {
            return !isProd;
        }
        if (hasEmail && contact.equalsIgnoreCase(user.getEmail().trim())) {
            return true;
        }
        return hasMobile && contact.equalsIgnoreCase(user.getMobile().trim());
    }

    private TenantEntity findActiveTenantByCode(String tenantCode) {
        TenantMapper mapper = tenantMapperProvider != null ? tenantMapperProvider.getIfAvailable() : null;
        if (mapper == null) {
            return null;
        }
        return mapper.selectOne(new LambdaQueryWrapper<TenantEntity>()
                .eq(TenantEntity::getTenantCode, tenantCode)
                .eq(TenantEntity::getDeleted, 0)
                .last("LIMIT 1"));
    }

    private TenantEntity findTenantById(Long tenantId) {
        TenantMapper mapper = tenantMapperProvider != null ? tenantMapperProvider.getIfAvailable() : null;
        return mapper != null ? mapper.selectById(tenantId) : null;
    }

    private TenantInviteResponse toInviteResponse(TenantInviteEntity invite, TenantEntity tenant) {
        String tenantCode = tenant != null ? tenant.getTenantCode() : "platform";
        String tenantName = tenant != null ? tenant.getTenantName() : "Enterprise Tenant";
        return new TenantInviteResponse(
                invite.getId(),
                invite.getInviteToken(),
                "/login?inviteToken=" + invite.getInviteToken(),
                tenantCode,
                tenantName,
                invite.getInvitedEmail(),
                invite.getMaxUses(),
                invite.getUsedCount(),
                invite.getStatus(),
                invite.getExpireAt()
        );
    }

    private String otpKey(String tenantCode, String username) {
        return tenantCode.toLowerCase(Locale.ROOT) + ":" + username.toLowerCase(Locale.ROOT);
    }

    private void storeOtp(String key, String otpCode) {
        StringRedisTemplate redis = redisTemplateProvider != null ? redisTemplateProvider.getIfAvailable() : null;
        if (redis != null) {
            try {
                redis.opsForValue().set("saas:auth:pwdreset:" + key, otpCode, Duration.ofSeconds(OTP_TTL_SECONDS));
                return;
            } catch (Exception ignored) {
                // Fallback to local map
            }
        }
        if (localOtpStore.size() > 4096) {
            localOtpStore.clear();
        }
        localOtpStore.put(key, new OtpRecord(otpCode, System.currentTimeMillis() + OTP_TTL_SECONDS * 1000L));
    }

    private void verifyAndConsumeOtp(String key, String candidateOtp) {
        String expected = null;
        StringRedisTemplate redis = redisTemplateProvider != null ? redisTemplateProvider.getIfAvailable() : null;
        if (redis != null) {
            try {
                expected = redis.opsForValue().getAndDelete("saas:auth:pwdreset:" + key);
            } catch (Exception ignored) {
                expected = null;
            }
        }
        if (expected == null) {
            OtpRecord record = localOtpStore.remove(key);
            if (record != null && record.expiresAtMs() >= System.currentTimeMillis()) {
                expected = record.otp();
            }
        }
        if (expected == null || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                candidateOtp.getBytes(StandardCharsets.UTF_8))) {
            throw new BizException("PASSWORD_RESET_OTP_INVALID", "验证码错误或已过期，请重新获取");
        }
    }

    private UserMapper requiredUserMapper() {
        UserMapper mapper = userMapperProvider != null ? userMapperProvider.getIfAvailable() : null;
        if (mapper == null) {
            throw new BizException("MAPPER_UNAVAILABLE", "UserMapper unavailable");
        }
        return mapper;
    }

    private TenantInviteMapper requiredInviteMapper() {
        TenantInviteMapper mapper = inviteMapperProvider != null ? inviteMapperProvider.getIfAvailable() : null;
        if (mapper == null) {
            throw new BizException("MAPPER_UNAVAILABLE", "TenantInviteMapper unavailable");
        }
        return mapper;
    }

    private record OtpRecord(String otp, long expiresAtMs) {
    }

    private record ResolvedInvite(TenantInviteEntity invite, TenantEntity tenant) {
    }
}
