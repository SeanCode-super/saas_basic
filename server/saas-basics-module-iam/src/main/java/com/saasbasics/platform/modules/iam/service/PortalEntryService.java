package com.saasbasics.platform.modules.iam.service;

import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.iam.dto.PortalClientSaveRequest;
import com.saasbasics.platform.modules.iam.dto.PortalEntryResponse;
import com.saasbasics.platform.modules.iam.dto.PortalClientResponse;
import com.saasbasics.platform.modules.iam.dto.PortalTerminalSaveRequest;
import com.saasbasics.platform.modules.iam.dto.PortalTerminalResponse;
import com.saasbasics.platform.modules.iam.entity.LoginPolicyEntity;
import com.saasbasics.platform.modules.iam.entity.PasswordPolicyEntity;
import com.saasbasics.platform.modules.iam.entity.PortalClientEntity;
import com.saasbasics.platform.modules.iam.entity.PortalTerminalEntity;
import com.saasbasics.platform.modules.iam.mapper.LoginPolicyMapper;
import com.saasbasics.platform.modules.iam.mapper.PasswordPolicyMapper;
import com.saasbasics.platform.modules.iam.mapper.PortalClientMapper;
import com.saasbasics.platform.modules.iam.mapper.PortalTerminalMapper;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class PortalEntryService {

    private static final String DEFAULT_TERMINAL_CODE = "web";

    private final ObjectProvider<PortalClientMapper> portalClientMapperProvider;
    private final ObjectProvider<PortalTerminalMapper> portalTerminalMapperProvider;
    private final ObjectProvider<LoginPolicyMapper> loginPolicyMapperProvider;
    private final ObjectProvider<PasswordPolicyMapper> passwordPolicyMapperProvider;

    public PortalEntryService(ObjectProvider<PortalClientMapper> portalClientMapperProvider,
                              ObjectProvider<PortalTerminalMapper> portalTerminalMapperProvider,
                              ObjectProvider<LoginPolicyMapper> loginPolicyMapperProvider,
                              ObjectProvider<PasswordPolicyMapper> passwordPolicyMapperProvider) {
        this.portalClientMapperProvider = portalClientMapperProvider;
        this.portalTerminalMapperProvider = portalTerminalMapperProvider;
        this.loginPolicyMapperProvider = loginPolicyMapperProvider;
        this.passwordPolicyMapperProvider = passwordPolicyMapperProvider;
    }

    public PortalEntryResponse resolveEntry(String clientId, String terminalCode) {
        PortalClientMapper clientMapper = requiredClientMapper();
        PortalTerminalMapper terminalMapper = requiredTerminalMapper();

        String normalizedClientId = normalizeClientId(clientId);
        PortalClientEntity client = clientMapper.selectByClientId(normalizedClientId);
        if (client == null || !"ENABLED".equalsIgnoreCase(client.getStatus())) {
            throw new BizException("PORTAL_CLIENT_NOT_FOUND", "Portal client is not available");
        }

        PortalTerminalEntity terminal = resolveTerminal(terminalMapper, client.getId(), terminalCode);
        if (terminal == null || !"ENABLED".equalsIgnoreCase(terminal.getStatus())) {
            throw new BizException("PORTAL_TERMINAL_NOT_FOUND", "Portal terminal is not available");
        }

        LoginPolicyEntity loginPolicy = resolveLoginPolicy(client, terminal);
        PasswordPolicyEntity passwordPolicy = resolvePasswordPolicy(client, terminal);

        return new PortalEntryResponse(
                client.getClientId(),
                client.getClientName(),
                client.getTenantCode(),
                requiredValue(firstNonBlank(terminal.getPortalTitle(), client.getPortalTitle()), "PORTAL_CONFIG_INCOMPLETE", "门户标题未配置"),
                requiredValue(client.getWelcomeTitle(), "PORTAL_CONFIG_INCOMPLETE", "欢迎标题未配置"),
                requiredValue(client.getWelcomeText(), "PORTAL_CONFIG_INCOMPLETE", "欢迎文案未配置"),
                firstNonBlank(terminal.getLogoUrl(), client.getLogoUrl()),
                requiredValue(firstNonBlank(terminal.getThemeCode(), client.getThemeCode()), "PORTAL_CONFIG_INCOMPLETE", "主题编码未配置"),
                firstNonBlank(terminal.getBackgroundImageUrl(), client.getBackgroundImageUrl()),
                requiredValue(firstNonBlank(terminal.getBackgroundColor(), client.getBackgroundColor()), "PORTAL_CONFIG_INCOMPLETE", "背景色未配置"),
                client.getFilingInfo(),
                new PortalEntryResponse.TerminalSnapshot(
                        terminal.getTerminalCode(),
                        terminal.getTerminalName(),
                        terminal.getTerminalType()
                ),
                toLoginPolicySnapshot(loginPolicy),
                toPasswordPolicySnapshot(passwordPolicy),
                new PortalEntryResponse.CaptchaSnapshot(
                        requiredValue(firstNonBlank(terminal.getCaptchaMode(), client.getCaptchaMode()), "PORTAL_CONFIG_INCOMPLETE", "验证码模式未配置"),
                        Boolean.TRUE.equals(terminal.getSliderReserved()) || Boolean.TRUE.equals(client.getSliderReserved())
                )
        );
    }

    public PortalClientEntity requireActiveClient(String clientId) {
        PortalClientMapper mapper = requiredClientMapper();
        PortalClientEntity client = mapper.selectByClientId(normalizeClientId(clientId));
        if (client == null || !"ENABLED".equalsIgnoreCase(client.getStatus())) {
            throw new BizException("PORTAL_CLIENT_NOT_FOUND", "Portal client is not available");
        }
        return client;
    }

    public PortalTerminalEntity requireActiveTerminal(Long portalClientId, String terminalCode) {
        PortalTerminalMapper mapper = requiredTerminalMapper();
        PortalTerminalEntity terminal = resolveTerminal(mapper, portalClientId, terminalCode);
        if (terminal == null || !"ENABLED".equalsIgnoreCase(terminal.getStatus())) {
            throw new BizException("PORTAL_TERMINAL_NOT_FOUND", "Portal terminal is not available");
        }
        return terminal;
    }

    public List<PortalClientResponse> listClients() {
        PortalClientMapper mapper = requiredClientMapper();
        return mapper.selectList(null)
                .stream()
                .filter(item -> !Objects.equals(item.getDeleted(), 1))
                .map(this::toClientResponse)
                .collect(Collectors.toList());
    }

    public PortalClientResponse createClient(PortalClientSaveRequest request) {
        PortalClientEntity entity = new PortalClientEntity();
        apply(entity, request);
        requiredClientMapper().insert(entity);
        return getClient(entity.getId());
    }

    public PortalClientResponse updateClient(Long id, PortalClientSaveRequest request) {
        PortalClientMapper mapper = requiredClientMapper();
        PortalClientEntity entity = mapper.selectById(id);
        if (entity == null || Objects.equals(entity.getDeleted(), 1)) {
            throw new BizException("PORTAL_CLIENT_NOT_FOUND", "Portal client is not available");
        }
        apply(entity, request);
        mapper.updateById(entity);
        return getClient(id);
    }

    public List<PortalTerminalResponse> listTerminals(String clientId) {
        PortalTerminalMapper terminalMapper = requiredTerminalMapper();
        PortalClientMapper clientMapper = requiredClientMapper();

        Long resolvedPortalClientId = null;
        if (clientId != null && !clientId.isBlank()) {
            PortalClientEntity client = clientMapper.selectByClientId(clientId);
            resolvedPortalClientId = client == null ? -1L : client.getId();
        }
        final Long filterPortalClientId = resolvedPortalClientId;

        return terminalMapper.selectTerminalList()
                .stream()
                .filter(item -> !Objects.equals(item.getDeleted(), 1))
                .filter(item -> filterPortalClientId == null || Objects.equals(item.getPortalClientId(), filterPortalClientId))
                .map(this::toTerminalResponse)
                .collect(Collectors.toList());
    }

    public PortalTerminalResponse createTerminal(PortalTerminalSaveRequest request) {
        PortalTerminalEntity entity = new PortalTerminalEntity();
        apply(entity, request);
        normalizeDefaultTerminal(entity.getPortalClientId(), null, entity.getIsDefault());
        requiredTerminalMapper().insert(entity);
        return getTerminal(entity.getId());
    }

    public PortalTerminalResponse updateTerminal(Long id, PortalTerminalSaveRequest request) {
        PortalTerminalMapper mapper = requiredTerminalMapper();
        PortalTerminalEntity entity = mapper.selectById(id);
        if (entity == null || Objects.equals(entity.getDeleted(), 1)) {
            throw new BizException("PORTAL_TERMINAL_NOT_FOUND", "Portal terminal is not available");
        }
        apply(entity, request);
        normalizeDefaultTerminal(entity.getPortalClientId(), id, entity.getIsDefault());
        mapper.updateById(entity);
        return getTerminal(id);
    }

    private PortalClientResponse getClient(Long id) {
        PortalClientEntity entity = requiredClientMapper().selectById(id);
        if (entity == null || Objects.equals(entity.getDeleted(), 1)) {
            throw new BizException("PORTAL_CLIENT_NOT_FOUND", "Portal client is not available");
        }
        return toClientResponse(entity);
    }

    private PortalTerminalResponse getTerminal(Long id) {
        PortalTerminalEntity entity = requiredTerminalMapper().selectById(id);
        if (entity == null || Objects.equals(entity.getDeleted(), 1)) {
            throw new BizException("PORTAL_TERMINAL_NOT_FOUND", "Portal terminal is not available");
        }
        return toTerminalResponse(entity);
    }

    private PortalTerminalEntity resolveTerminal(PortalTerminalMapper mapper, Long portalClientId, String terminalCode) {
        String normalizedTerminalCode = normalizeTerminalCode(terminalCode);
        PortalTerminalEntity terminal = mapper.selectByClientAndTerminalCode(portalClientId, normalizedTerminalCode);
        if (terminal != null) {
            return terminal;
        }
        return mapper.selectDefaultTerminal(portalClientId);
    }

    private LoginPolicyEntity resolveLoginPolicy(PortalClientEntity client, PortalTerminalEntity terminal) {
        LoginPolicyMapper mapper = requiredLoginPolicyMapper();
        Long policyId = terminal.getLoginPolicyId() != null ? terminal.getLoginPolicyId() : client.getLoginPolicyId();
        if (policyId != null && policyId > 0) {
            LoginPolicyEntity policy = mapper.selectById(policyId);
            if (policy != null && !Objects.equals(policy.getDeleted(), 1) && "ENABLED".equalsIgnoreCase(policy.getStatus())) {
                return policy;
            }
        }
        LoginPolicyEntity policy = mapper.selectActivePolicy(client.getTenantId());
        if (policy == null) {
            throw new BizException("LOGIN_POLICY_NOT_FOUND", "No active login policy is configured for this tenant");
        }
        return policy;
    }

    private PasswordPolicyEntity resolvePasswordPolicy(PortalClientEntity client, PortalTerminalEntity terminal) {
        PasswordPolicyMapper mapper = requiredPasswordPolicyMapper();
        Long policyId = terminal.getPasswordPolicyId() != null ? terminal.getPasswordPolicyId() : client.getPasswordPolicyId();
        if (policyId != null && policyId > 0) {
            PasswordPolicyEntity policy = mapper.selectById(policyId);
            if (policy != null && !Objects.equals(policy.getDeleted(), 1) && "ENABLED".equalsIgnoreCase(policy.getStatus())) {
                return policy;
            }
        }
        PasswordPolicyEntity policy = mapper.selectActivePolicy(client.getTenantId());
        if (policy == null) {
            throw new BizException("PASSWORD_POLICY_NOT_FOUND", "No active password policy is configured for this tenant");
        }
        return policy;
    }

    private PortalEntryResponse.LoginPolicySnapshot toLoginPolicySnapshot(LoginPolicyEntity policy) {
        return new PortalEntryResponse.LoginPolicySnapshot(
                policy.getId(),
                policy.getPolicyCode(),
                policy.getPolicyName(),
                Boolean.TRUE.equals(policy.getAllowPasswordLogin()),
                Boolean.TRUE.equals(policy.getAllowSmsLogin()),
                Boolean.TRUE.equals(policy.getAllowEmailLogin()),
                Boolean.TRUE.equals(policy.getAllowSocialLogin()),
                Boolean.TRUE.equals(policy.getForceMfa()),
                policy.getSessionTimeoutMinutes(),
                policy.getMaxFailedCount(),
                policy.getLockMinutes()
        );
    }

    private PortalEntryResponse.PasswordPolicySnapshot toPasswordPolicySnapshot(PasswordPolicyEntity policy) {
        return new PortalEntryResponse.PasswordPolicySnapshot(
                policy.getId(),
                policy.getPolicyCode(),
                policy.getPolicyName(),
                policy.getMinLength(),
                policy.getMaxLength(),
                Boolean.TRUE.equals(policy.getRequireUppercase()),
                Boolean.TRUE.equals(policy.getRequireLowercase()),
                Boolean.TRUE.equals(policy.getRequireNumber()),
                Boolean.TRUE.equals(policy.getRequireSpecial()),
                policy.getPasswordHistoryLimit(),
                policy.getPasswordExpireDays()
        );
    }

    private PortalClientResponse toClientResponse(PortalClientEntity entity) {
        return new PortalClientResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getClientId(),
                entity.getTenantCode(),
                entity.getClientName(),
                entity.getPortalTitle(),
                entity.getWelcomeTitle(),
                entity.getWelcomeText(),
                entity.getLogoUrl(),
                entity.getThemeCode(),
                entity.getBackgroundImageUrl(),
                entity.getBackgroundColor(),
                entity.getFilingInfo(),
                entity.getLoginPolicyId(),
                entity.getPasswordPolicyId(),
                entity.getCaptchaMode(),
                entity.getSliderReserved(),
                entity.getStatus(),
                entity.getRemark()
        );
    }

    private PortalTerminalResponse toTerminalResponse(PortalTerminalEntity entity) {
        return new PortalTerminalResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getPortalClientId(),
                entity.getTerminalCode(),
                entity.getTerminalName(),
                entity.getTerminalType(),
                entity.getPortalTitle(),
                entity.getLogoUrl(),
                entity.getThemeCode(),
                entity.getBackgroundImageUrl(),
                entity.getBackgroundColor(),
                entity.getLoginPolicyId(),
                entity.getPasswordPolicyId(),
                entity.getCaptchaMode(),
                entity.getSliderReserved(),
                entity.getIsDefault(),
                entity.getStatus(),
                entity.getRemark()
        );
    }

    private String normalizeClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            throw new BizException("PORTAL_CLIENT_REQUIRED", "缺少 clientId");
        }
        return clientId;
    }

    private String normalizeTerminalCode(String terminalCode) {
        return terminalCode == null || terminalCode.isBlank() ? DEFAULT_TERMINAL_CODE : terminalCode;
    }

    private String firstNonBlank(String primary, String secondary) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        if (secondary != null && !secondary.isBlank()) {
            return secondary;
        }
        return null;
    }

    private String requiredValue(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BizException(code, message);
        }
        return value;
    }

    private void apply(PortalClientEntity entity, PortalClientSaveRequest request) {
        entity.setTenantId(request.tenantId());
        entity.setClientId(request.clientId());
        entity.setTenantCode(request.tenantCode());
        entity.setClientName(request.clientName());
        entity.setPortalTitle(request.portalTitle());
        entity.setWelcomeTitle(request.welcomeTitle());
        entity.setWelcomeText(request.welcomeText());
        entity.setLogoUrl(request.logoUrl());
        entity.setThemeCode(request.themeCode());
        entity.setBackgroundImageUrl(request.backgroundImageUrl());
        entity.setBackgroundColor(request.backgroundColor());
        entity.setFilingInfo(request.filingInfo());
        entity.setLoginPolicyId(request.loginPolicyId());
        entity.setPasswordPolicyId(request.passwordPolicyId());
        entity.setCaptchaMode(request.captchaMode());
        entity.setSliderReserved(request.sliderReserved());
        entity.setStatus(request.status());
        entity.setRemark(request.remark());
    }

    private void apply(PortalTerminalEntity entity, PortalTerminalSaveRequest request) {
        entity.setTenantId(request.tenantId());
        entity.setPortalClientId(request.portalClientId());
        entity.setTerminalCode(request.terminalCode());
        entity.setTerminalName(request.terminalName());
        entity.setTerminalType(request.terminalType());
        entity.setPortalTitle(request.portalTitle());
        entity.setLogoUrl(request.logoUrl());
        entity.setThemeCode(request.themeCode());
        entity.setBackgroundImageUrl(request.backgroundImageUrl());
        entity.setBackgroundColor(request.backgroundColor());
        entity.setLoginPolicyId(request.loginPolicyId());
        entity.setPasswordPolicyId(request.passwordPolicyId());
        entity.setCaptchaMode(request.captchaMode());
        entity.setSliderReserved(request.sliderReserved());
        entity.setIsDefault(request.isDefault());
        entity.setStatus(request.status());
        entity.setRemark(request.remark());
    }

    private PortalClientMapper requiredClientMapper() {
        PortalClientMapper mapper = portalClientMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "Portal client resolution requires the db profile and MySQL connection");
        }
        return mapper;
    }

    private PortalTerminalMapper requiredTerminalMapper() {
        PortalTerminalMapper mapper = portalTerminalMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "Portal terminal resolution requires the db profile and MySQL connection");
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

    private void normalizeDefaultTerminal(Long portalClientId, Long currentTerminalId, Boolean isDefault) {
        if (!Boolean.TRUE.equals(isDefault)) {
            return;
        }
        PortalTerminalMapper mapper = requiredTerminalMapper();
        mapper.selectTerminalList().stream()
                .filter(item -> Objects.equals(item.getPortalClientId(), portalClientId))
                .filter(item -> currentTerminalId == null || !Objects.equals(item.getId(), currentTerminalId))
                .filter(item -> !Objects.equals(item.getDeleted(), 1))
                .filter(item -> Boolean.TRUE.equals(item.getIsDefault()))
                .forEach(item -> {
                    item.setIsDefault(false);
                    mapper.updateById(item);
                });
    }
}
