package com.saasbasics.platform.security.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.security.vault.CredentialVaultService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.StreamUtils;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

class TransportCryptoEngineAndFilterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final SecureRandom RNG = new SecureRandom();

    private record TestFixture(
            TransportCryptoKeyRing keyRing,
            TransportCryptoService cryptoService,
            TransportCryptoPolicyService policyService,
            TransportCryptoFilter filter
    ) {
    }

    private TestFixture newFixture() {
        TransportCryptoProperties props = new TransportCryptoProperties();
        props.setMaxGraceKeys(2);
        StaticListableBeanFactory bf = new StaticListableBeanFactory();
        bf.addBean("meterRegistry", new SimpleMeterRegistry());

        TransportCryptoKeyRing keyRing = new TransportCryptoKeyRing(
                props,
                bf.getBeanProvider(StringRedisTemplate.class),
                bf.getBeanProvider(CredentialVaultService.class)
        );
        TransportCryptoService cryptoService = new TransportCryptoService(
                keyRing,
                bf.getBeanProvider(StringRedisTemplate.class)
        );
        TransportCryptoPolicyService policyService = new TransportCryptoPolicyService(
                props,
                bf.getBeanProvider(TransportCryptoPolicyRepository.class),
                bf.getBeanProvider(StringRedisTemplate.class),
                bf.getBeanProvider(ObjectMapper.class),
                bf.getBeanProvider(MeterRegistry.class),
                bf.getBeanProvider(RequestMappingHandlerMapping.class)
        );
        TransportCryptoFilter filter = new TransportCryptoFilter(policyService, cryptoService, MAPPER);
        return new TestFixture(keyRing, cryptoService, policyService, filter);
    }

    private record ClientSession(byte[] reqKey, byte[] respKey, String kid, long ts, String nonce, String epkB64) {
    }

    private ClientSession clientNegotiate(TransportCryptoKeyRing.ServerKeyEntry serverKey, String nonce) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("X25519");
        KeyPair clientKp = kpg.generateKeyPair();
        byte[] clientSpki = clientKp.getPublic().getEncoded();
        byte[] clientPubRaw32 = Arrays.copyOfRange(clientSpki, clientSpki.length - 32, clientSpki.length);

        KeyAgreement ka = KeyAgreement.getInstance("X25519");
        ka.init(clientKp.getPrivate());
        ka.doPhase(TransportCryptoKeyRing.decodeClientRawPublicKey(serverKey.publicKeyRaw32()), true);
        byte[] sharedSecret = ka.generateSecret();

        long ts = System.currentTimeMillis();
        byte[] salt = ("saas-transport-v1|" + ts + "|" + nonce).getBytes(StandardCharsets.UTF_8);
        byte[] ikm = new byte[96];
        System.arraycopy(sharedSecret, 0, ikm, 0, 32);
        System.arraycopy(clientPubRaw32, 0, ikm, 32, 32);
        System.arraycopy(serverKey.publicKeyRaw32(), 0, ikm, 64, 32);

        byte[] prk = TransportCryptoService.hkdfExtractSha256(salt, ikm);
        byte[] reqKey = TransportCryptoService.hkdfExpandSha256(prk, "saas-basics-req-v1".getBytes(StandardCharsets.UTF_8), 32);
        byte[] respKey = TransportCryptoService.hkdfExpandSha256(prk, "saas-basics-resp-v1".getBytes(StandardCharsets.UTF_8), 32);
        return new ClientSession(
                reqKey, respKey, serverKey.keyId(), ts, nonce, Base64.getEncoder().encodeToString(clientPubRaw32));
    }

    private String encryptGcm(byte[] key, byte[] iv, String plaintext, String aad) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
    }

    private String decryptGcm(byte[] key, String ivB64, String ctB64, String aad) throws Exception {
        byte[] iv = Base64.getDecoder().decode(ivB64);
        byte[] ct = Base64.getDecoder().decode(ctB64);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
        return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
    }

    @Test
    void decryptsRequestBodyAndQueryAndEncryptsResponseInRequiredMode() throws Exception {
        TestFixture f = newFixture();
        f.policyService().updatePolicy(new TransportCryptoPolicySnapshot(
                TransportCryptoMode.REQUIRED, TransportScopeMode.ALL_API,
                TransportCryptoPolicySnapshot.ALGORITHM_SUITE,
                true, true, true, true, 300,
                List.of("/api/**"), List.of("/api/security/transport/policy"), List.of("/api/auth/login"),
                2L, Instant.now(), "test"
        ));

        // 1. Plaintext request MUST be rejected with 428
        MockHttpServletRequest plainReq = new MockHttpServletRequest("POST", "/api/auth/login");
        plainReq.setContent("{\"username\":\"admin\"}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse plainResp = new MockHttpServletResponse();
        f.filter().doFilter(plainReq, plainResp, (req, res) -> {});
        assertThat(plainResp.getStatus()).isEqualTo(428);
        assertThat(plainResp.getContentAsString()).contains("TRANSPORT_ENCRYPTION_REQUIRED");

        // 2. Encrypted POST body + encrypted X-Enc-Query MUST roundtrip cleanly
        TransportCryptoKeyRing.ServerKeyEntry serverKey = f.keyRing().getActiveKey();
        ClientSession cs = clientNegotiate(serverKey, "nonce-unique-123456");

        byte[] bodyIv = new byte[12];
        byte[] qryIv = new byte[12];
        RNG.nextBytes(bodyIv);
        RNG.nextBytes(qryIv);

        String reqAad = TransportCryptoService.buildAad("REQ", "POST", "/api/auth/login", cs.kid(), cs.ts(), cs.nonce());
        String qryAad = TransportCryptoService.buildAad("QRY", "POST", "/api/auth/login", cs.kid(), cs.ts(), cs.nonce());
        String bodyCt = encryptGcm(cs.reqKey(), bodyIv, "{\"username\":\"admin\",\"secret\":\"pass123\"}", reqAad);
        String qryCt = encryptGcm(cs.reqKey(), qryIv, "tenantCode=platform&verbose=true", qryAad);

        TransportCryptoEnvelope.RequestEnvelope env = new TransportCryptoEnvelope.RequestEnvelope(
                TransportCryptoPolicySnapshot.ALGORITHM_SUITE,
                cs.kid(), cs.epkB64(), Base64.getEncoder().encodeToString(bodyIv), cs.ts(), cs.nonce(), bodyCt);

        MockHttpServletRequest encReq = new MockHttpServletRequest("POST", "/api/auth/login");
        encReq.addHeader(TransportCryptoFilter.HEADER_ENCRYPTED, "1");
        encReq.addHeader(TransportCryptoFilter.HEADER_ENC_QUERY, Base64.getEncoder().encodeToString(qryIv) + "." + qryCt);
        encReq.setContentType("application/json");
        encReq.setContent(MAPPER.writeValueAsBytes(env));

        MockHttpServletResponse encResp = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            byte[] seenBody = StreamUtils.copyToByteArray(req.getInputStream());
            String seenJson = new String(seenBody, StandardCharsets.UTF_8);
            assertThat(seenJson).contains("admin").contains("pass123");
            assertThat(req.getParameter("tenantCode")).isEqualTo("platform");
            assertThat(req.getParameter("verbose")).isEqualTo("true");

            res.setContentType("application/json;charset=UTF-8");
            ((HttpServletResponse) res).setStatus(200);
            res.getWriter().write("{\"success\":true,\"data\":{\"token\":\"jwt-secret-999\"}}");
        };

        f.filter().doFilter(encReq, encResp, chain);
        assertThat(encResp.getStatus()).isEqualTo(200);
        assertThat(encResp.getHeader(TransportCryptoFilter.HEADER_ENCRYPTED)).isEqualTo("1");
        assertThat(encResp.getContentAsString()).doesNotContain("jwt-secret-999");

        TransportCryptoEnvelope.ResponseEnvelope respEnv = MAPPER.readValue(
                encResp.getContentAsByteArray(), TransportCryptoEnvelope.ResponseEnvelope.class);
        assertThat(respEnv.encrypted()).isTrue();
        String respAad = TransportCryptoService.buildAad("RESP", "POST", "/api/auth/login", cs.kid(), respEnv.ts(), cs.nonce());
        String decryptedResp = decryptGcm(cs.respKey(), respEnv.iv(), respEnv.cipherText(), respAad);
        assertThat(decryptedResp).contains("jwt-secret-999");

        // 3. Replaying same nonce MUST be rejected
        MockHttpServletRequest replayReq = new MockHttpServletRequest("POST", "/api/auth/login");
        replayReq.addHeader(TransportCryptoFilter.HEADER_ENCRYPTED, "1");
        replayReq.setContentType("application/json");
        replayReq.setContent(MAPPER.writeValueAsBytes(env));
        MockHttpServletResponse replayResp = new MockHttpServletResponse();
        f.filter().doFilter(replayReq, replayResp, (req, res) -> {});
        assertThat(replayResp.getStatus()).isEqualTo(400);
        assertThat(replayResp.getContentAsString()).contains("TRANSPORT_REPLAY_DETECTED");
    }

    @Test
    void enforcesSensitiveScopeAndSupportsKeyRotationGraceWindow() throws Exception {
        TestFixture f = newFixture();
        f.policyService().updatePolicy(new TransportCryptoPolicySnapshot(
                TransportCryptoMode.REQUIRED, TransportScopeMode.SENSITIVE_ONLY,
                TransportCryptoPolicySnapshot.ALGORITHM_SUITE,
                true, true, true, true, 300,
                List.of("/api/**"), List.of("/api/security/transport/policy"), List.of("/api/auth/login", "/api/system/**"),
                3L, Instant.now(), "sensitive-only"
        ));

        // Open endpoint bypassed in SENSITIVE_ONLY mode
        MockHttpServletRequest openReq = new MockHttpServletRequest("GET", "/api/portal/entry");
        MockHttpServletResponse openResp = new MockHttpServletResponse();
        f.filter().doFilter(openReq, openResp, (req, res) -> res.getWriter().write("{\"ok\":true}"));
        assertThat(openResp.getStatus()).isEqualTo(200);
        assertThat(openResp.getContentAsString()).contains("ok");

        // Key rotation keeps old kid alive during grace window
        TransportCryptoKeyRing.ServerKeyEntry firstKey = f.keyRing().getActiveKey();
        TransportCryptoKeyRing.ServerKeyEntry secondKey = f.keyRing().rotateKeyPair();
        assertThat(secondKey.keyId()).isNotEqualTo(firstKey.keyId());

        ClientSession csOld = clientNegotiate(firstKey, "grace-nonce-abcdef123456");
        TransportCryptoService.DerivedKeySession established = f.cryptoService().establishSession(
                csOld.kid(), csOld.epkB64(), csOld.ts(), csOld.nonce(), true, 300);
        assertThat(established.requestKey()).isEqualTo(csOld.reqKey());

        // Evicting old key past maxGraceKeys rejects stale kid cleanly with 428
        f.keyRing().rotateKeyPair();
        f.keyRing().rotateKeyPair();
        assertThat(f.keyRing().findKey(firstKey.keyId())).isNull();

        ClientSession staleAttempt = clientNegotiate(firstKey, "stale-nonce-abcdef123456");
        assertThatThrownBy(() -> f.cryptoService().establishSession(
                staleAttempt.kid(), staleAttempt.epkB64(), staleAttempt.ts(), staleAttempt.nonce(), true, 300))
                .isInstanceOf(TransportCryptoException.class)
                .hasMessageContaining("轮换或过期");
    }
}
