package com.saasbasics.platform.security.vault;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saasbasics.platform.config.SecurityProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;

class ProdSecurityStartupGuardTest {

    @Test
    void allowsDevelopmentProfileWithDefaults() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        VaultCryptoProperties vaultProps = new VaultCryptoProperties();
        CredentialVaultService vault = new CredentialVaultService(vaultProps);

        SecurityProperties secProps = new SecurityProperties();
        secProps.setCorsAllowedOrigins(List.of("*"));

        StaticListableBeanFactory bf = new StaticListableBeanFactory();
        bf.addBean("vault", vault);
        bf.addBean("sec", secProps);

        ProdSecurityStartupGuard guard = new ProdSecurityStartupGuard(
                env, bf.getBeanProvider(CredentialVaultService.class), bf.getBeanProvider(SecurityProperties.class));

        assertThatCode(guard::verifyProductionSecurityBaseline).doesNotThrowAnyException();
    }

    @Test
    void rejectsProdProfileWithDefaultMasterKeyOrWeakDbPasswordOrWildcardCors() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("spring.datasource.password", "Str0ng#Prod$Pass!");

        StaticListableBeanFactory bfDefaultKey = new StaticListableBeanFactory();
        bfDefaultKey.addBean("vault", new CredentialVaultService(new VaultCryptoProperties()));
        SecurityProperties okCors = new SecurityProperties();
        okCors.setCorsAllowedOrigins(List.of("https://app.tianshu.example"));
        bfDefaultKey.addBean("sec", okCors);

        ProdSecurityStartupGuard guardDefaultKey = new ProdSecurityStartupGuard(
                env,
                bfDefaultKey.getBeanProvider(CredentialVaultService.class),
                bfDefaultKey.getBeanProvider(SecurityProperties.class));

        assertThatThrownBy(guardDefaultKey::verifyProductionSecurityBaseline)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SAAS_VAULT_MASTER_KEY");

        VaultCryptoProperties strongVaultProps = new VaultCryptoProperties();
        strongVaultProps.setMasterKey("prod-strong-master-key-2026-xyz!");
        CredentialVaultService strongVault = new CredentialVaultService(strongVaultProps);

        MockEnvironment weakDbEnv = new MockEnvironment();
        weakDbEnv.setActiveProfiles("prod");
        weakDbEnv.setProperty("spring.datasource.password", "root");
        StaticListableBeanFactory bfStrongVault = new StaticListableBeanFactory();
        bfStrongVault.addBean("vault", strongVault);
        bfStrongVault.addBean("sec", okCors);

        ProdSecurityStartupGuard guardWeakDb = new ProdSecurityStartupGuard(
                weakDbEnv,
                bfStrongVault.getBeanProvider(CredentialVaultService.class),
                bfStrongVault.getBeanProvider(SecurityProperties.class));

        assertThatThrownBy(guardWeakDb::verifyProductionSecurityBaseline)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_PASSWORD");

        StaticListableBeanFactory bfWildcardCors = new StaticListableBeanFactory();
        bfWildcardCors.addBean("vault", strongVault);
        SecurityProperties badCors = new SecurityProperties();
        badCors.setCorsAllowedOrigins(List.of("https://app.tianshu.example", "*"));
        bfWildcardCors.addBean("sec", badCors);

        ProdSecurityStartupGuard guardWildcardCors = new ProdSecurityStartupGuard(
                env,
                bfWildcardCors.getBeanProvider(CredentialVaultService.class),
                bfWildcardCors.getBeanProvider(SecurityProperties.class));

        assertThatThrownBy(guardWildcardCors::verifyProductionSecurityBaseline)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORS");
    }

    @Test
    void passesProdProfileWhenAllSecretsAndCorsAreHardened() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("spring.datasource.password", "Str0ng#Prod$Pass!");

        VaultCryptoProperties strongVaultProps = new VaultCryptoProperties();
        strongVaultProps.setMasterKey("prod-strong-master-key-2026-xyz!");

        SecurityProperties okCors = new SecurityProperties();
        okCors.setCorsAllowedOrigins(List.of("https://console.tianshu.example"));

        StaticListableBeanFactory bf = new StaticListableBeanFactory();
        bf.addBean("vault", new CredentialVaultService(strongVaultProps));
        bf.addBean("sec", okCors);

        ProdSecurityStartupGuard guard = new ProdSecurityStartupGuard(
                env, bf.getBeanProvider(CredentialVaultService.class), bf.getBeanProvider(SecurityProperties.class));

        assertThatCode(guard::verifyProductionSecurityBaseline).doesNotThrowAnyException();
    }
}
