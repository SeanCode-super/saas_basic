package com.saasbasics.platform.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.iam.dto.PortalEntryResponse;
import com.saasbasics.platform.modules.iam.entity.LoginPolicyEntity;
import com.saasbasics.platform.modules.iam.entity.PasswordPolicyEntity;
import com.saasbasics.platform.modules.iam.entity.PortalClientEntity;
import com.saasbasics.platform.modules.iam.entity.PortalTerminalEntity;
import com.saasbasics.platform.modules.iam.mapper.LoginPolicyMapper;
import com.saasbasics.platform.modules.iam.mapper.PasswordPolicyMapper;
import com.saasbasics.platform.modules.iam.mapper.PortalClientMapper;
import com.saasbasics.platform.modules.iam.mapper.PortalTerminalMapper;
import com.saasbasics.platform.modules.iam.service.PortalEntryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class PortalEntryServiceTest {

    private PortalClientMapper clientMapper;
    private PortalTerminalMapper terminalMapper;
    private PortalEntryService service;
    private PortalClientEntity client;

    @BeforeEach
    void setUp() {
        clientMapper = mock(PortalClientMapper.class);
        terminalMapper = mock(PortalTerminalMapper.class);
        LoginPolicyMapper loginPolicyMapper = mock(LoginPolicyMapper.class);
        PasswordPolicyMapper passwordPolicyMapper = mock(PasswordPolicyMapper.class);

        client = activeClient();
        PortalTerminalEntity terminal = activeTerminal();
        LoginPolicyEntity loginPolicy = activeLoginPolicy();
        PasswordPolicyEntity passwordPolicy = activePasswordPolicy();

        when(terminalMapper.selectByClientAndTerminalCode(1L, "web")).thenReturn(terminal);
        when(loginPolicyMapper.selectById(11L)).thenReturn(loginPolicy);
        when(passwordPolicyMapper.selectById(12L)).thenReturn(passwordPolicy);

        service = new PortalEntryService(
                provider(clientMapper),
                provider(terminalMapper),
                provider(loginPolicyMapper),
                provider(passwordPolicyMapper)
        );
    }

    @Test
    void resolvesTheDefaultPortalWhenClientIdIsAbsent() {
        when(clientMapper.selectDefaultClient()).thenReturn(client);

        PortalEntryResponse response = service.resolveEntry(null, null);

        assertThat(response.clientId()).isEqualTo("default-client");
        assertThat(response.terminal().terminalCode()).isEqualTo("web");
        verify(clientMapper).selectDefaultClient();
        verify(clientMapper, never()).selectByClientId("default-client");
    }

    @Test
    void keepsExplicitClientResolutionAsTheHighestPriority() {
        when(clientMapper.selectByClientId("explicit-client")).thenReturn(client);

        PortalEntryResponse response = service.resolveEntry(" explicit-client ", "web");

        assertThat(response.clientId()).isEqualTo("default-client");
        verify(clientMapper).selectByClientId("explicit-client");
        verify(clientMapper, never()).selectDefaultClient();
    }

    @Test
    void reportsAConfigurationErrorWhenNoDefaultPortalExists() {
        when(clientMapper.selectDefaultClient()).thenReturn(null);

        assertThatThrownBy(() -> service.resolveEntry(null, "web"))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).getCode())
                .isEqualTo("PORTAL_DEFAULT_CLIENT_NOT_FOUND");
    }

    private PortalClientEntity activeClient() {
        PortalClientEntity entity = new PortalClientEntity();
        entity.setId(1L);
        entity.setTenantId(1L);
        entity.setTenantCode("platform");
        entity.setClientId("default-client");
        entity.setClientName("Default Portal");
        entity.setPortalTitle("Platform Console");
        entity.setWelcomeTitle("Welcome");
        entity.setWelcomeText("Sign in");
        entity.setThemeCode("enterprise");
        entity.setBackgroundColor("#20262d");
        entity.setLoginPolicyId(11L);
        entity.setPasswordPolicyId(12L);
        entity.setCaptchaMode("IMAGE");
        entity.setSliderReserved(true);
        entity.setIsDefault(true);
        entity.setStatus("ENABLED");
        entity.setDeleted(0);
        return entity;
    }

    private PortalTerminalEntity activeTerminal() {
        PortalTerminalEntity entity = new PortalTerminalEntity();
        entity.setId(2L);
        entity.setTenantId(1L);
        entity.setPortalClientId(1L);
        entity.setTerminalCode("web");
        entity.setTerminalName("Web Console");
        entity.setTerminalType("BROWSER");
        entity.setIsDefault(true);
        entity.setStatus("ENABLED");
        entity.setDeleted(0);
        return entity;
    }

    private LoginPolicyEntity activeLoginPolicy() {
        LoginPolicyEntity entity = new LoginPolicyEntity();
        entity.setId(11L);
        entity.setPolicyCode("DEFAULT");
        entity.setPolicyName("Default Login Policy");
        entity.setStatus("ENABLED");
        entity.setDeleted(0);
        return entity;
    }

    private PasswordPolicyEntity activePasswordPolicy() {
        PasswordPolicyEntity entity = new PasswordPolicyEntity();
        entity.setId(12L);
        entity.setPolicyCode("DEFAULT");
        entity.setPolicyName("Default Password Policy");
        entity.setStatus("ENABLED");
        entity.setDeleted(0);
        return entity;
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
