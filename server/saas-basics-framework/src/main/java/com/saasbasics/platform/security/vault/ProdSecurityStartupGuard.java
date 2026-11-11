package com.saasbasics.platform.security.vault;

import com.saasbasics.platform.config.SecurityProperties;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * 生产环境安全基线启动强校验：禁止生产环境使用开发默认 Vault 主密钥或弱 CORS 通配配置启动。
 */
@Component
public class ProdSecurityStartupGuard implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(ProdSecurityStartupGuard.class);

    private final Environment environment;
    private final ObjectProvider<CredentialVaultService> vaultProvider;
    private final ObjectProvider<SecurityProperties> securityPropertiesProvider;

    public ProdSecurityStartupGuard(
            Environment environment,
            ObjectProvider<CredentialVaultService> vaultProvider,
            ObjectProvider<SecurityProperties> securityPropertiesProvider) {
        this.environment = environment;
        this.vaultProvider = vaultProvider;
        this.securityPropertiesProvider = securityPropertiesProvider;
    }

    @Override
    public void afterSingletonsInstantiated() {
        verifyProductionSecurityBaseline();
    }

    public void verifyProductionSecurityBaseline() {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }
        CredentialVaultService vault = vaultProvider.getIfAvailable();
        if (vault != null && vault.isUsingDefaultMasterKey()) {
            throw new IllegalStateException(
                    "[SecurityGuard] 生产环境禁止使用默认开发 Vault 主密钥，请配置环境变量 SAAS_VAULT_MASTER_KEY 或接入 KMS"
            );
        }
        String dbPass = environment.getProperty("spring.datasource.password", "");
        if ("root".equals(dbPass) || dbPass.isBlank()) {
            throw new IllegalStateException(
                    "[SecurityGuard] 生产环境禁止使用空密码或默认 root 数据库密码，请通过 DB_PASSWORD 注入高强度凭据"
            );
        }
        SecurityProperties secProps = securityPropertiesProvider.getIfAvailable();
        if (secProps != null) {
            List<String> origins = secProps.getCorsAllowedOrigins();
            if (origins != null && origins.stream().anyMatch(o -> "*".equals(o != null ? o.trim() : ""))) {
                throw new IllegalStateException(
                        "[SecurityGuard] 生产环境禁止 CORS 使用通配符 *，请明确配置允许的域名白名单"
                );
            }
        }
        log.info("[SecurityGuard] 生产环境密钥与安全基线启动强校验通过");
    }
}
