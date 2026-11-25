package com.saasbasics.platform.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.SaasBasicsApplication;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.modules.iam.service.AuthService;
import com.saasbasics.platform.modules.iam.service.PasskeyService;
import com.saasbasics.platform.modules.iam.service.PasskeyService.AuthenticationBeginRequest;
import com.saasbasics.platform.modules.iam.service.PasskeyService.AuthenticationFinishRequest;
import com.saasbasics.platform.modules.iam.service.PasskeyService.RegistrationBeginRequest;
import com.saasbasics.platform.modules.iam.service.PasskeyService.RegistrationFinishRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = SaasBasicsApplication.class)
class WebAuthnPasskeyIntegrationTest {

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

    @Autowired private PasskeyService passkeyService;
    @Autowired private AuthService authService;
    @Autowired private ObjectMapper objectMapper;

    @AfterEach
    void cleanup() {
        AuthContext.clear();
        TenantAccessContextHolder.clear();
    }

    @Test
    void registersEs256PasskeyAuthenticatesAndRejectsTamperedOrReplayedAssertions() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair kp = kpg.generateKeyPair();
        String spkiB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(kp.getPublic().getEncoded());
        String credId = Base64.getUrlEncoder().withoutPadding().encodeToString("passkey-cred-1".getBytes(StandardCharsets.UTF_8));

        var admin = new AuthPrincipal(1L, 1L, "platform", 1L, "admin", "Platform Admin",
                "PLATFORM", "ONLINE", null, List.of("*"), List.of(), List.of(1L), List.of());
        AuthContext.set(admin);
        try (var ignored = TenantAccessContextHolder.openAuthenticated(admin)) {
            var regOpts = passkeyService.beginRegistration(new RegistrationBeginRequest("localhost", "Tianshu"));
            String regClientData = b64(objectMapper.writeValueAsBytes(Map.of(
                    "type", "webauthn.create",
                    "challenge", regOpts.challenge(),
                    "origin", "https://localhost:9527"
            )));
            String regAuthData = b64(buildAuthData("localhost", (byte) 0x05, 1));
            var summary = passkeyService.finishRegistration(new RegistrationFinishRequest(
                    regOpts.challengeId(), "localhost", "https://localhost:9527", credId,
                    regClientData, regAuthData, spkiB64, -7, "00000000-0000-0000-0000-000000000000",
                    "internal,hybrid", "MacBook TouchID"
            ));
            assertThat(summary.deviceName()).isEqualTo("MacBook TouchID");
            assertThat(summary.signCount()).isEqualTo(1L);
            assertThat(passkeyService.listMyPasskeys()).hasSize(1);
        } finally {
            AuthContext.clear();
            TenantAccessContextHolder.clear();
        }

        // Authenticate with signCount = 2 and real ECDSA signature
        var authOpts = passkeyService.beginAuthentication(new AuthenticationBeginRequest("platform", "admin", "localhost"));
        assertThat(authOpts.allowCredentials()).hasSize(1);

        byte[] clientDataBytes = objectMapper.writeValueAsBytes(Map.of(
                "type", "webauthn.get",
                "challenge", authOpts.challenge(),
                "origin", "https://localhost:9527"
        ));
        byte[] authDataBytes = buildAuthData("localhost", (byte) 0x05, 2);
        byte[] sigBytes = signEs256(kp, authDataBytes, clientDataBytes);

        var loginResp = passkeyService.finishAuthentication(new AuthenticationFinishRequest(
                authOpts.challengeId(), "platform", "localhost", "https://localhost:9527",
                credId, b64(clientDataBytes), b64(authDataBytes), b64(sigBytes)
        ), "127.0.0.1", "JUnit-WebAuthn");
        assertThat(loginResp.accessToken()).isNotBlank();
        assertThat(authService.resolvePrincipal(loginResp.accessToken())).isNotNull();

        // Replay same challenge fails
        assertThatThrownBy(() -> passkeyService.finishAuthentication(new AuthenticationFinishRequest(
                authOpts.challengeId(), "platform", "localhost", "https://localhost:9527",
                credId, b64(clientDataBytes), b64(authDataBytes), b64(sigBytes)
        ), "127.0.0.1", "JUnit")).isInstanceOf(BizException.class);

        // Tampered signature fails
        var authOpts2 = passkeyService.beginAuthentication(new AuthenticationBeginRequest("platform", "admin", "localhost"));
        byte[] clientData2 = objectMapper.writeValueAsBytes(Map.of(
                "type", "webauthn.get",
                "challenge", authOpts2.challenge(),
                "origin", "https://localhost:9527"
        ));
        byte[] authData2 = buildAuthData("localhost", (byte) 0x05, 3);
        byte[] badSig = signEs256(kp, authData2, clientData2);
        badSig[badSig.length - 1] ^= 0x55;
        assertThatThrownBy(() -> passkeyService.finishAuthentication(new AuthenticationFinishRequest(
                authOpts2.challengeId(), "platform", "localhost", "https://localhost:9527",
                credId, b64(clientData2), b64(authData2), b64(badSig)
        ), "127.0.0.1", "JUnit")).isInstanceOf(BizException.class);

        // SignCount rollback (asserted 2 <= stored 2) fails
        var authOpts3 = passkeyService.beginAuthentication(new AuthenticationBeginRequest("platform", "admin", "localhost"));
        byte[] clientData3 = objectMapper.writeValueAsBytes(Map.of(
                "type", "webauthn.get",
                "challenge", authOpts3.challenge(),
                "origin", "https://localhost:9527"
        ));
        byte[] rollbackAuthData = buildAuthData("localhost", (byte) 0x05, 2);
        byte[] rollbackSig = signEs256(kp, rollbackAuthData, clientData3);
        assertThatThrownBy(() -> passkeyService.finishAuthentication(new AuthenticationFinishRequest(
                authOpts3.challengeId(), "platform", "localhost", "https://localhost:9527",
                credId, b64(clientData3), b64(rollbackAuthData), b64(rollbackSig)
        ), "127.0.0.1", "JUnit")).isInstanceOf(BizException.class);
    }

    private static byte[] buildAuthData(String rpId, byte flags, int signCount) throws Exception {
        byte[] rpHash = MessageDigest.getInstance("SHA-256").digest(rpId.getBytes(StandardCharsets.UTF_8));
        ByteBuffer buf = ByteBuffer.allocate(37);
        buf.put(rpHash);
        buf.put(flags);
        buf.putInt(signCount);
        return buf.array();
    }

    private static byte[] signEs256(KeyPair kp, byte[] authData, byte[] clientData) throws Exception {
        byte[] clientHash = MessageDigest.getInstance("SHA-256").digest(clientData);
        byte[] signed = new byte[authData.length + clientHash.length];
        System.arraycopy(authData, 0, signed, 0, authData.length);
        System.arraycopy(clientHash, 0, signed, authData.length, clientHash.length);
        Signature sig = Signature.getInstance("SHA256withECDSA");
        sig.initSign(kp.getPrivate());
        sig.update(signed);
        return sig.sign();
    }

    private static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
