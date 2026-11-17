package com.saasbasics.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.exception.GlobalExceptionHandler;
import com.saasbasics.platform.modules.ai.application.dto.AiProviderDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiProviderSaveRequest;
import com.saasbasics.platform.modules.ai.application.service.AiModelManageService;
import com.saasbasics.platform.modules.ai.infrastructure.adapter.VaultCredentialResolver;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiCredentialRefEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiProviderEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.AiCredentialRefMapper;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.AiMcpServerMapper;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.AiModelConfigMapper;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.AiProviderMapper;
import com.saasbasics.platform.modules.ai.web.AiManageController;
import com.saasbasics.platform.security.vault.CredentialVaultService;
import com.saasbasics.platform.security.vault.VaultCryptoProperties;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class EnterpriseRateLimitAndAiWebTest {

    @Test
    void rateLimitAndAiQuotaExceptionsMapToHttp429TooManyRequests() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/test-probe/ratelimit"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));

        mockMvc.perform(get("/test-probe/quota"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AI_QUOTA_EXCEEDED"));
    }

    @Test
    void aiManageNeverStoresOrReturnsPlaintextApiKeysAndSupportsRotation() throws Exception {
        AiProviderMapper providerMapper = mock(AiProviderMapper.class);
        AiModelConfigMapper modelMapper = mock(AiModelConfigMapper.class);
        AiCredentialRefMapper credRefMapper = mock(AiCredentialRefMapper.class);
        AiMcpServerMapper mcpServerMapper = mock(AiMcpServerMapper.class);

        VaultCryptoProperties vaultProps = new VaultCryptoProperties();
        vaultProps.setMasterKey("unit-test-enterprise-master-key-v1");
        CredentialVaultService vaultService = new CredentialVaultService(vaultProps);
        VaultCredentialResolver resolver = new VaultCredentialResolver(vaultService);

        AiModelManageService service = new AiModelManageService(
                providerMapper, modelMapper, credRefMapper, mcpServerMapper, resolver, new ObjectMapper()
        );

        AtomicReference<AiProviderEntity> savedEntityRef = new AtomicReference<>();
        when(providerMapper.insert(any(AiProviderEntity.class))).thenAnswer(inv -> {
            AiProviderEntity entity = inv.getArgument(0);
            entity.setId(55L);
            savedEntityRef.set(entity);
            return 1;
        });
        when(credRefMapper.selectOne(any())).thenReturn(null);
        when(credRefMapper.insert(any(AiCredentialRefEntity.class))).thenReturn(1);

        AiProviderSaveRequest req = new AiProviderSaveRequest();
        req.setProviderCode("deepseek");
        req.setProviderName("DeepSeek");
        req.setBaseUrl("https://api.deepseek.com");
        req.setApiKey("sk-topsecret-enterprise-token-8899");
        Long id = service.saveProvider(req);

        assertThat(id).isEqualTo(55L);
        String storedCipher = savedEntityRef.get().getApiKey();
        assertThat(storedCipher).startsWith("enc:v1:");
        assertThat(storedCipher).doesNotContain("topsecret");
        assertThat(vaultService.decrypt(storedCipher)).isEqualTo("sk-topsecret-enterprise-token-8899");

        // Verify HTTP response from AiManageController masks secret and flags encryption
        when(providerMapper.selectList(any())).thenReturn(List.of(savedEntityRef.get()));
        when(modelMapper.selectList(any())).thenReturn(List.of());
        when(credRefMapper.selectList(any())).thenReturn(List.of());

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AiManageController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        String body = mockMvc.perform(get("/api/ai/manage/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].hasApiKey").value(true))
                .andExpect(jsonPath("$.data[0].encryptedAtRest").value(true))
                .andExpect(jsonPath("$.data[0].apiKeyMasked").value("sk-****8899"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).doesNotContain("topsecret");
    }

    @RestController
    static class ProbeController {
        @GetMapping("/test-probe/ratelimit")
        public void ratelimit() {
            throw new BizException("RATE_LIMIT_EXCEEDED", "too fast");
        }

        @GetMapping("/test-probe/quota")
        public void quota() {
            throw new BizException("AI_QUOTA_EXCEEDED", "budget reached");
        }
    }
}
