package com.saasbasics.platform.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.SaasBasicsApplication;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.iam.service.AuthService;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = SaasBasicsApplication.class)
class PlatformStandardsContractTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.3.0")
            .withDatabaseName("saas_basics")
            .withUsername("root")
            .withPassword("root");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void openApiDocumentPublishesValidOpenApi3SchemaAndCoreRoutes() throws Exception {
        MvcResult result = mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(root.path("openapi").asText()).startsWith("3.");
        assertThat(root.path("paths").size()).isGreaterThan(30);
        assertThat(root.path("components").path("schemas").size()).isGreaterThan(10);
        assertThat(root.path("paths").has("/api/iam/organization-migration/apply")).isTrue();
    }

    @Test
    void scim20ServiceProviderConfigAndSchemasComplyWithRfc7643And7644() throws Exception {
        mockMvc.perform(get("/scim/v2/ServiceProviderConfig"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemas[0]").value("urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig"));

        mockMvc.perform(get("/scim/v2/Schemas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemas[0]").value("urn:ietf:params:scim:api:messages:2.0:ListResponse"))
                .andExpect(jsonPath("$.totalResults").isNumber());
    }

    @Test
    void rfc9457ProblemDetailsContractAppliesOnExceptionWithProblemAcceptHeader() throws Exception {
        mockMvc.perform(post("/api/iam/organization-migration/verify")
                        .accept(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(status().is4xxClientError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isString())
                .andExpect(jsonPath("$.status").isNumber())
                .andExpect(jsonPath("$.title").isString())
                .andExpect(jsonPath("$.instance").value("/api/iam/organization-migration/verify"));
    }

    @Test
    void oidcPkceS256ChallengeVerificationAcceptsMatchingVerifierAndRejectsMismatch() throws Exception {
        String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        String stateMeta = "OIDC|http://localhost/cb|pkce:" + challenge;

        Method verify = AuthService.class.getDeclaredMethod("verifyPkceIfBound", String.class, String.class);
        verify.setAccessible(true);
        verify.invoke(null, stateMeta, verifier);

        assertThatThrownBy(() -> verify.invoke(null, stateMeta, "wrong-verifier-value"))
                .hasCauseInstanceOf(BizException.class);
    }
}
