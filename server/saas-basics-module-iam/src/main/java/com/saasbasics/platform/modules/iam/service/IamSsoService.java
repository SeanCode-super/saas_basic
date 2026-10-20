package com.saasbasics.platform.modules.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.iam.dto.AuthLoginResponse;
import com.saasbasics.platform.modules.iam.dto.SsoAuthorizeResponse;
import com.saasbasics.platform.modules.iam.dto.SsoCallbackRequest;
import com.saasbasics.platform.modules.iam.dto.SsoConfigResponse;
import com.saasbasics.platform.modules.iam.dto.SsoConfigSaveRequest;
import com.saasbasics.platform.modules.iam.entity.IamSsoConfigEntity;
import com.saasbasics.platform.modules.iam.mapper.IamSsoConfigMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IamSsoService {

    private final IamSsoConfigMapper ssoConfigMapper;
    private final AuthService authService;

    public IamSsoService(IamSsoConfigMapper ssoConfigMapper,
                         AuthService authService) {
        this.ssoConfigMapper = ssoConfigMapper;
        this.authService = authService;
    }

    public List<SsoConfigResponse> listConfigs() {
        Long tenantId = getTenantId();
        List<IamSsoConfigEntity> list = ssoConfigMapper.selectList(new LambdaQueryWrapper<IamSsoConfigEntity>()
                .eq(IamSsoConfigEntity::getTenantId, tenantId)
                .eq(IamSsoConfigEntity::getDeleted, 0)
                .orderByDesc(IamSsoConfigEntity::getId));

        return list.stream().map(c -> new SsoConfigResponse(
                c.getId(),
                c.getTenantId(),
                c.getIdpType(),
                c.getAppId(),
                c.getRedirectUri(),
                c.getStatus(),
                c.getAutoCreateUser(),
                c.getConfigJson(),
                c.getRemark(),
                c.getCreatedAt()
        )).toList();
    }

    public List<Map<String, Object>> listAvailableChannels() {
        return List.of(
                Map.of("idpType", "WECOM", "name", "企业微信", "icon", "ChatRound", "desc", "企业微信扫码或工作台免登"),
                Map.of("idpType", "FEISHU", "name", "飞书", "icon", "Promotion", "desc", "飞书企业身份快速鉴权单点登录"),
                Map.of("idpType", "DINGTALK", "name", "钉钉", "icon", "Bell", "desc", "钉钉移动端与PC端扫码免登"),
                Map.of("idpType", "SAML_OIDC", "name", "SAML 2.0 / OIDC", "icon", "Key", "desc", "标准企业级身份联邦 (Okta/Azure AD)")
        );
    }

    @Transactional
    public SsoConfigResponse saveConfig(SsoConfigSaveRequest req) {
        Long tenantId = getTenantId();
        Long userId = getUserId();

        IamSsoConfigEntity entity;
        if (req.id() != null && req.id() > 0) {
            entity = ssoConfigMapper.selectById(req.id());
            if (entity == null || !entity.getTenantId().equals(tenantId) || entity.getDeleted() != 0) {
                throw new BizException("NOT_FOUND", "SSO配置不存在");
            }
        } else {
            entity = new IamSsoConfigEntity();
            entity.setTenantId(tenantId);
            entity.setCreatedBy(userId);
            entity.setCreatedAt(LocalDateTime.now());
            entity.setDeleted(0);
            entity.setVersion(0);
        }

        entity.setIdpType(req.idpType());
        entity.setAppId(req.appId());
        if (req.appSecret() != null && !req.appSecret().isBlank()) {
            entity.setAppSecret(req.appSecret());
        }
        entity.setRedirectUri(req.redirectUri() != null ? req.redirectUri() : "");
        entity.setStatus(req.status() != null ? req.status() : "ENABLED");
        entity.setAutoCreateUser(req.autoCreateUser() != null ? req.autoCreateUser() : 1);
        entity.setConfigJson(req.configJson() != null ? req.configJson() : "{}");
        entity.setRemark(req.remark());
        entity.setUpdatedBy(userId);
        entity.setUpdatedAt(LocalDateTime.now());

        if (entity.getId() != null) {
            ssoConfigMapper.updateById(entity);
        } else {
            ssoConfigMapper.insert(entity);
        }

        return new SsoConfigResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getIdpType(),
                entity.getAppId(),
                entity.getRedirectUri(),
                entity.getStatus(),
                entity.getAutoCreateUser(),
                entity.getConfigJson(),
                entity.getRemark(),
                entity.getCreatedAt()
        );
    }

    @Transactional
    public void deleteConfig(Long id) {
        Long tenantId = getTenantId();
        IamSsoConfigEntity entity = ssoConfigMapper.selectById(id);
        if (entity == null || !entity.getTenantId().equals(tenantId) || entity.getDeleted() != 0) {
            throw new BizException("NOT_FOUND", "SSO配置不存在");
        }
        entity.setDeleted(1);
        entity.setDeletedAt(LocalDateTime.now());
        ssoConfigMapper.updateById(entity);
    }

    public SsoAuthorizeResponse getAuthorizeUrl(String idpType, String redirectUri) {
        String state = UUID.randomUUID().toString().replace("-", "");
        String encodedRedirect = URLEncoder.encode(redirectUri != null ? redirectUri : "http://localhost:5173/auth/sso/callback", StandardCharsets.UTF_8);

        String authorizeUrl = switch (idpType.toUpperCase()) {
            case "WECOM" -> "https://open.work.weixin.qq.com/wwopen/sso/qrConnect?appid=ww_mock_enterprise&agentid=100001&redirect_uri=" + encodedRedirect + "&state=" + state;
            case "FEISHU" -> "https://passport.feishu.cn/suite/pas/oauth/authorize?client_id=cli_mock_feishu&response_type=code&redirect_uri=" + encodedRedirect + "&state=" + state;
            case "DINGTALK" -> "https://login.dingtalk.com/oauth2/auth?client_id=ding_mock_app&response_type=code&scope=openid&redirect_uri=" + encodedRedirect + "&state=" + state;
            default -> "https://login.microsoftonline.com/common/oauth2/v2.0/authorize?client_id=oidc_client_mock&response_type=code&redirect_uri=" + encodedRedirect + "&state=" + state;
        };

        return new SsoAuthorizeResponse(idpType, authorizeUrl, state);
    }

    @Transactional
    public com.saasbasics.platform.modules.iam.dto.AuthLoginResponse handleCallback(SsoCallbackRequest req) {
        return authService.loginBySso(req, "127.0.0.1", "SSO-Web-Browser");
    }

    private Long getTenantId() {
        AuthPrincipal p = AuthContext.get();
        return p != null && p.tenantId() != null ? p.tenantId() : 1L;
    }

    private Long getUserId() {
        AuthPrincipal p = AuthContext.get();
        return p != null && p.userId() != null ? p.userId() : 1L;
    }
}
