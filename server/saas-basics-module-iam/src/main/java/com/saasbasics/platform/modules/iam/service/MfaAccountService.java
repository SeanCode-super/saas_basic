package com.saasbasics.platform.modules.iam.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.iam.entity.LoginPolicyEntity;
import com.saasbasics.platform.modules.iam.entity.UserEntity;
import com.saasbasics.platform.modules.iam.mapper.LoginPolicyMapper;
import com.saasbasics.platform.modules.iam.mapper.UserMapper;
import com.saasbasics.platform.security.vault.CredentialVaultService;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 个人 TOTP 多因子认证 (MFA) 生命周期管理服务：支持密钥签发、激活绑定、防重放核验与安全解绑。
 */
@Service
public class MfaAccountService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    public record MfaStatusResponse(
            boolean enabled,
            boolean policyForceMfa,
            int remainingRecoveryCodes
    ) {
    }

    public record MfaSetupResponse(
            String secret,
            String otpAuthUri,
            List<String> recoveryCodes
    ) {
    }

    private final ObjectProvider<UserMapper> userMapperProvider;
    private final ObjectProvider<LoginPolicyMapper> loginPolicyMapperProvider;
    private final TotpMfaService totpMfaService;
    private final PasswordSecurityService passwordSecurityService;
    private final ObjectProvider<CredentialVaultService> vaultProvider;
    private final ObjectMapper objectMapper;

    public MfaAccountService(
            ObjectProvider<UserMapper> userMapperProvider,
            ObjectProvider<LoginPolicyMapper> loginPolicyMapperProvider,
            TotpMfaService totpMfaService,
            PasswordSecurityService passwordSecurityService,
            ObjectProvider<CredentialVaultService> vaultProvider,
            ObjectMapper objectMapper) {
        this.userMapperProvider = userMapperProvider;
        this.loginPolicyMapperProvider = loginPolicyMapperProvider;
        this.totpMfaService = totpMfaService;
        this.passwordSecurityService = passwordSecurityService;
        this.vaultProvider = vaultProvider;
        this.objectMapper = objectMapper;
    }

    public MfaStatusResponse getStatus() {
        UserEntity user = requireCurrentUser();
        LoginPolicyMapper policyMapper = loginPolicyMapperProvider.getIfAvailable();
        LoginPolicyEntity policy = policyMapper != null ? policyMapper.selectActivePolicy(user.getTenantId()) : null;
        boolean forcePolicy = policy != null && Boolean.TRUE.equals(policy.getForceMfa());
        int recoveryCount = readRecoveryCodes(user).size();
        return new MfaStatusResponse(Boolean.TRUE.equals(user.getMfaEnabled()), forcePolicy, recoveryCount);
    }

    @Transactional(rollbackFor = Exception.class)
    public MfaSetupResponse setup() {
        UserEntity user = requireCurrentUser();
        if (Boolean.TRUE.equals(user.getMfaEnabled())) {
            throw new BizException("MFA_ALREADY_ENABLED", "当前账号已启用 MFA，如需换绑请先安全停用后重新绑定");
        }
        String secret = totpMfaService.generateSecret();
        List<String> recoveryCodes = totpMfaService.generateRecoveryCodes();
        user.setMfaSecretEnc(seal(secret));
        user.setMfaRecoveryCodesEnc(sealJson(recoveryCodes));
        user.setMfaEnabled(false);
        requireUserMapper().updateById(user);

        AuthPrincipal principal = AuthContext.get();
        String tenantCode = principal != null && principal.tenantCode() != null ? principal.tenantCode() : "platform";
        String uri = totpMfaService.buildOtpAuthUri("TianshuSaaS-" + tenantCode, user.getUsername(), secret);
        return new MfaSetupResponse(secret, uri, recoveryCodes);
    }

    @Transactional(rollbackFor = Exception.class)
    public MfaStatusResponse enable(String totpCode) {
        UserEntity user = requireCurrentUser();
        if (user.getMfaSecretEnc() == null || user.getMfaSecretEnc().isBlank()) {
            throw new BizException("MFA_NOT_INITIALIZED", "请先点击“生成 MFA 密钥”后再验证绑定");
        }
        String secret = unseal(user.getMfaSecretEnc());
        if (!totpMfaService.verifyCode(secret, totpCode, user.getId())) {
            throw new BizException("MFA_CODE_INVALID", "动态验证码校验失败，请确认验证器时间同步且输入正确");
        }
        user.setMfaEnabled(true);
        requireUserMapper().updateById(user);
        return getStatus();
    }

    @Transactional(rollbackFor = Exception.class)
    public MfaStatusResponse disable(String password, String totpCode) {
        UserEntity user = requireCurrentUser();
        if (!Boolean.TRUE.equals(user.getMfaEnabled())) {
            return getStatus();
        }
        if (password == null || !passwordSecurityService.matches(password, user.getPasswordHash())) {
            throw new BizException("INVALID_PASSWORD", "当前登录密码校验错误");
        }
        String secret = unseal(user.getMfaSecretEnc());
        if (!totpMfaService.verifyCode(secret, totpCode, user.getId())) {
            throw new BizException("MFA_CODE_INVALID", "6 位动态验证码错误或已使用");
        }
        user.setMfaEnabled(false);
        user.setMfaSecretEnc(null);
        user.setMfaRecoveryCodesEnc(null);
        requireUserMapper().updateById(user);
        return getStatus();
    }

    private UserEntity requireCurrentUser() {
        AuthPrincipal principal = AuthContext.get();
        if (principal == null || principal.userId() == null) {
            throw new BizException("UNAUTHORIZED", "请先登录");
        }
        UserEntity user = requireUserMapper().selectById(principal.userId());
        if (user == null || Integer.valueOf(1).equals(user.getDeleted())) {
            throw new BizException("USER_NOT_FOUND", "当前用户不存在");
        }
        return user;
    }

    private UserMapper requireUserMapper() {
        UserMapper mapper = userMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "MFA 操作需要数据库连接");
        }
        return mapper;
    }

    private String seal(String raw) {
        CredentialVaultService vault = vaultProvider.getIfAvailable();
        return vault != null ? vault.encrypt(raw) : raw;
    }

    private String unseal(String cipher) {
        CredentialVaultService vault = vaultProvider.getIfAvailable();
        return vault != null ? vault.decrypt(cipher) : cipher;
    }

    private String sealJson(List<String> list) {
        try {
            return seal(objectMapper.writeValueAsString(list));
        } catch (Exception ex) {
            return seal("[]");
        }
    }

    private List<String> readRecoveryCodes(UserEntity user) {
        if (user.getMfaRecoveryCodesEnc() == null || user.getMfaRecoveryCodesEnc().isBlank()) {
            return Collections.emptyList();
        }
        try {
            String json = unseal(user.getMfaRecoveryCodesEnc());
            return objectMapper.readValue(json, STRING_LIST);
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }
}
