package com.saasbasics.platform.modules.iam.service;

import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.audit.api.AuditRecorder;
import com.saasbasics.platform.modules.iam.dto.SecurityBaselineDtos.SecurityBaselineResponse;
import com.saasbasics.platform.modules.iam.dto.SecurityBaselineDtos.SecurityBaselineSaveRequest;
import com.saasbasics.platform.modules.iam.entity.LoginPolicyEntity;
import com.saasbasics.platform.modules.iam.entity.PasswordPolicyEntity;
import com.saasbasics.platform.modules.iam.mapper.LoginPolicyMapper;
import com.saasbasics.platform.modules.iam.mapper.PasswordPolicyMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 企业安全基线聚合读写服务：直接同步租户生效中的密码策略与登录会话策略，确保控制台改动实时约束认证引擎。
 */
@Service
public class SecurityBaselineService {

    private final ObjectProvider<PasswordPolicyMapper> passwordPolicyMapperProvider;
    private final ObjectProvider<LoginPolicyMapper> loginPolicyMapperProvider;
    private final ObjectProvider<AuditRecorder> auditRecorderProvider;

    public SecurityBaselineService(
            ObjectProvider<PasswordPolicyMapper> passwordPolicyMapperProvider,
            ObjectProvider<LoginPolicyMapper> loginPolicyMapperProvider,
            ObjectProvider<AuditRecorder> auditRecorderProvider) {
        this.passwordPolicyMapperProvider = passwordPolicyMapperProvider;
        this.loginPolicyMapperProvider = loginPolicyMapperProvider;
        this.auditRecorderProvider = auditRecorderProvider;
    }

    public SecurityBaselineResponse getCurrentBaseline() {
        Long tenantId = currentTenantId();
        PasswordPolicyEntity pwd = passwordPolicyMapperProvider.getIfAvailable() != null
                ? passwordPolicyMapperProvider.getIfAvailable().selectActivePolicy(tenantId)
                : null;
        LoginPolicyEntity login = loginPolicyMapperProvider.getIfAvailable() != null
                ? loginPolicyMapperProvider.getIfAvailable().selectActivePolicy(tenantId)
                : null;

        return toResponse(pwd, login);
    }

    @Transactional(rollbackFor = Exception.class)
    public SecurityBaselineResponse saveBaseline(SecurityBaselineSaveRequest request) {
        PasswordPolicyMapper pwdMapper = passwordPolicyMapperProvider.getIfAvailable();
        LoginPolicyMapper loginMapper = loginPolicyMapperProvider.getIfAvailable();
        if (pwdMapper == null || loginMapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "保存安全基线需要启用数据库配置");
        }
        Long tenantId = currentTenantId();

        PasswordPolicyEntity pwd = pwdMapper.selectActivePolicy(tenantId);
        // 若命中系统默认行（tenant_id = 0）而当前在具体租户空间下修改，也支持更新当前租户专属行或活跃行
        boolean insertPwd = (pwd == null || !tenantId.equals(pwd.getTenantId()));
        PasswordPolicyEntity targetPwd = insertPwd ? new PasswordPolicyEntity() : pwd;
        if (insertPwd) {
            targetPwd.setTenantId(tenantId);
            targetPwd.setPolicyCode("TENANT_DEFAULT_" + tenantId);
            targetPwd.setPolicyName("租户默认密码基线策略");
            targetPwd.setPasswordExpireDays(pwd != null && pwd.getPasswordExpireDays() != null ? pwd.getPasswordExpireDays() : 90);
            targetPwd.setTempPasswordExpireHours(pwd != null && pwd.getTempPasswordExpireHours() != null ? pwd.getTempPasswordExpireHours() : 24);
            targetPwd.setStatus("ENABLED");
        }
        int minLen = Math.max(6, Math.min(32, request.minLength()));
        int maxLen = request.maxLength() != null ? Math.max(minLen, request.maxLength())
                : (pwd != null && pwd.getMaxLength() != null ? Math.max(minLen, pwd.getMaxLength()) : 32);
        targetPwd.setMinLength(minLen);
        targetPwd.setMaxLength(maxLen);
        targetPwd.setRequireUppercase(request.requireUppercase() != null ? request.requireUppercase()
                : (pwd != null && Boolean.TRUE.equals(pwd.getRequireUppercase())));
        targetPwd.setRequireLowercase(request.requireLowercase() != null ? request.requireLowercase()
                : (pwd == null || !Boolean.FALSE.equals(pwd.getRequireLowercase())));
        targetPwd.setRequireNumber(request.requireNumber() != null ? request.requireNumber()
                : (pwd == null || !Boolean.FALSE.equals(pwd.getRequireNumber())));
        targetPwd.setRequireSpecial(request.requireSpecial());
        targetPwd.setPasswordHistoryLimit(Math.max(1, Math.min(24, request.passwordHistory())));
        targetPwd.setRemark("安全基线控制台同步更新");
        if (insertPwd) {
            pwdMapper.insert(targetPwd);
        } else {
            pwdMapper.updateById(targetPwd);
        }

        LoginPolicyEntity login = loginMapper.selectActivePolicy(tenantId);
        boolean insertLogin = (login == null || !tenantId.equals(login.getTenantId()));
        LoginPolicyEntity targetLogin = insertLogin ? new LoginPolicyEntity() : login;
        if (insertLogin) {
            targetLogin.setTenantId(tenantId);
            targetLogin.setPolicyCode("TENANT_DEFAULT_" + tenantId);
            targetLogin.setPolicyName("租户默认登录基线策略");
            targetLogin.setAllowPasswordLogin(login == null || !Boolean.FALSE.equals(login.getAllowPasswordLogin()));
            targetLogin.setAllowSmsLogin(login != null && Boolean.TRUE.equals(login.getAllowSmsLogin()));
            targetLogin.setAllowEmailLogin(login != null && Boolean.TRUE.equals(login.getAllowEmailLogin()));
            targetLogin.setAllowSocialLogin(login != null && Boolean.TRUE.equals(login.getAllowSocialLogin()));
            targetLogin.setDeviceTrustDays(login != null && login.getDeviceTrustDays() != null ? login.getDeviceTrustDays() : 7);
            targetLogin.setIpAllowlistJson(login != null ? login.getIpAllowlistJson() : "[]");
            targetLogin.setStatus("ENABLED");
        }
        targetLogin.setForceMfa(request.forceMfa());
        targetLogin.setSessionTimeoutMinutes(Math.max(5, Math.min(1440, request.sessionTimeoutMinutes())));
        targetLogin.setMaxFailedCount(request.maxFailedCount() != null ? request.maxFailedCount()
                : (login != null && login.getMaxFailedCount() != null ? login.getMaxFailedCount() : 5));
        targetLogin.setLockMinutes(request.lockMinutes() != null ? request.lockMinutes()
                : (login != null && login.getLockMinutes() != null ? login.getLockMinutes() : 30));
        targetLogin.setRemark("安全基线控制台同步更新");
        if (insertLogin) {
            loginMapper.insert(targetLogin);
        } else {
            loginMapper.updateById(targetLogin);
        }

        AuditRecorder audit = auditRecorderProvider.getIfAvailable();
        if (audit != null) {
            audit.record(
                    "iam",
                    "security_baseline",
                    String.valueOf(tenantId),
                    "UPDATE_BASELINE",
                    "minLength=" + minLen + ",forceMfa=" + request.forceMfa() + ",sessionTimeout=" + request.sessionTimeoutMinutes(),
                    "ENABLED",
                    true
            );
        }
        return toResponse(targetPwd, targetLogin);
    }

    private SecurityBaselineResponse toResponse(PasswordPolicyEntity pwd, LoginPolicyEntity login) {
        return new SecurityBaselineResponse(
                pwd != null && pwd.getMinLength() != null ? pwd.getMinLength() : 8,
                pwd != null && pwd.getMaxLength() != null ? pwd.getMaxLength() : 20,
                pwd != null && Boolean.TRUE.equals(pwd.getRequireUppercase()),
                pwd == null || !Boolean.FALSE.equals(pwd.getRequireLowercase()),
                pwd == null || !Boolean.FALSE.equals(pwd.getRequireNumber()),
                pwd != null && Boolean.TRUE.equals(pwd.getRequireSpecial()),
                pwd != null && pwd.getPasswordHistoryLimit() != null ? pwd.getPasswordHistoryLimit() : 5,
                login != null && login.getSessionTimeoutMinutes() != null ? login.getSessionTimeoutMinutes() : 480,
                login != null && Boolean.TRUE.equals(login.getForceMfa()),
                login != null && login.getMaxFailedCount() != null ? login.getMaxFailedCount() : 5,
                login != null && login.getLockMinutes() != null ? login.getLockMinutes() : 30
        );
    }

    private Long currentTenantId() {
        AuthPrincipal principal = AuthContext.get();
        return principal != null && principal.tenantId() != null ? principal.tenantId() : 1L;
    }
}
