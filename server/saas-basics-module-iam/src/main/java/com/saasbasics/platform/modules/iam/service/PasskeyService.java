package com.saasbasics.platform.modules.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.id.UuidV7Generator;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.modules.audit.api.AuditRecorder;
import com.saasbasics.platform.modules.iam.dto.AuthLoginResponse;
import com.saasbasics.platform.modules.iam.entity.UserEntity;
import com.saasbasics.platform.modules.iam.entity.WebAuthnCredentialEntity;
import com.saasbasics.platform.modules.iam.mapper.UserMapper;
import com.saasbasics.platform.modules.iam.mapper.WebAuthnCredentialMapper;
import com.saasbasics.platform.modules.tenant.entity.TenantEntity;
import com.saasbasics.platform.modules.tenant.mapper.TenantMapper;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FIDO2 / WebAuthn Passkey registration and cryptographic assertion verification service.
 * Verifies challenge single-use, rpIdHash, origin, UP flags, monotonic signCount, and ES256/EdDSA/RS256 signatures.
 */
@Service
public class PasskeyService {

    private static final int COSE_ES256 = -7;
    private static final int COSE_EDDSA = -8;
    private static final int COSE_RS256 = -257;
    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);
    private static final int MAX_LOCAL_CHALLENGES = 2048;

    private final ObjectProvider<WebAuthnCredentialMapper> credentialMapperProvider;
    private final ObjectProvider<UserMapper> userMapperProvider;
    private final ObjectProvider<TenantMapper> tenantMapperProvider;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final AuthService authService;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();
    private final UuidV7Generator uuidGenerator = new UuidV7Generator();
    private final ConcurrentHashMap<String, StoredChallenge> localChallenges = new ConcurrentHashMap<>();

    private record StoredChallenge(String challenge, String rpId, long tenantId, Long userId, String mode, Instant expiresAt) {}

    public record RegistrationBeginRequest(String rpId, String rpName) {}

    public record RegistrationOptions(
            String challengeId,
            String challenge,
            Map<String, String> rp,
            Map<String, String> user,
            List<Map<String, Object>> pubKeyCredParams,
            List<Map<String, String>> excludeCredentials,
            long timeoutMs
    ) {}

    public record RegistrationFinishRequest(
            String challengeId,
            String rpId,
            String origin,
            String credentialId,
            String clientDataJSON,
            String authenticatorData,
            String publicKeySpki,
            Integer algorithm,
            String aaguid,
            String transports,
            String deviceName
    ) {}

    public record AuthenticationBeginRequest(String tenantCode, String username, String rpId) {}

    public record AuthenticationOptions(
            String challengeId,
            String challenge,
            String rpId,
            List<Map<String, Object>> allowCredentials,
            long timeoutMs
    ) {}

    public record AuthenticationFinishRequest(
            String challengeId,
            String tenantCode,
            String rpId,
            String origin,
            String credentialId,
            String clientDataJSON,
            String authenticatorData,
            String signature
    ) {}

    public record PasskeySummary(
            UUID publicId,
            String credentialId,
            Integer algorithm,
            Long signCount,
            String deviceName,
            String transports,
            LocalDateTime lastUsedAt,
            String status
    ) {}

    public PasskeyService(
            ObjectProvider<WebAuthnCredentialMapper> credentialMapperProvider,
            ObjectProvider<UserMapper> userMapperProvider,
            ObjectProvider<TenantMapper> tenantMapperProvider,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            AuthService authService,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper
    ) {
        this.credentialMapperProvider = credentialMapperProvider;
        this.userMapperProvider = userMapperProvider;
        this.tenantMapperProvider = tenantMapperProvider;
        this.redisTemplateProvider = redisTemplateProvider;
        this.authService = authService;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
    }

    public RegistrationOptions beginRegistration(RegistrationBeginRequest request) {
        AuthPrincipal principal = AuthContext.get();
        if (principal == null || principal.userId() == null) {
            throw new BizException("AUTH_REQUIRED", "Authentication required to register a passkey");
        }
        String rpId = normalizeRpId(request != null ? request.rpId() : null);
        String rpName = (request != null && request.rpName() != null && !request.rpName().isBlank())
                ? request.rpName().trim() : "Tianshu Enterprise SaaS";
        byte[] raw = new byte[32];
        secureRandom.nextBytes(raw);
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        String challengeId = uuidGenerator.generate().toString();
        saveChallenge(challengeId, new StoredChallenge(
                challenge, rpId, principal.tenantId(), principal.userId(), "REGISTER", Instant.now().plus(CHALLENGE_TTL)));

        List<Map<String, String>> exclude = requireMapper(credentialMapperProvider).selectList(
                        new LambdaQueryWrapper<WebAuthnCredentialEntity>()
                                .eq(WebAuthnCredentialEntity::getUserId, principal.userId())
                                .eq(WebAuthnCredentialEntity::getDeleted, 0))
                .stream()
                .map(c -> Map.of("type", "public-key", "id", c.getCredentialId()))
                .toList();

        return new RegistrationOptions(
                challengeId,
                challenge,
                Map.of("id", rpId, "name", rpName),
                Map.of(
                        "id", Base64.getUrlEncoder().withoutPadding().encodeToString(
                                String.valueOf(principal.userId()).getBytes(StandardCharsets.UTF_8)),
                        "name", principal.username(),
                        "displayName", principal.nickname() != null ? principal.nickname() : principal.username()
                ),
                List.of(
                        Map.of("type", "public-key", "alg", COSE_ES256),
                        Map.of("type", "public-key", "alg", COSE_EDDSA),
                        Map.of("type", "public-key", "alg", COSE_RS256)
                ),
                exclude,
                300_000L
        );
    }

    @Transactional
    public PasskeySummary finishRegistration(RegistrationFinishRequest request) {
        AuthPrincipal principal = AuthContext.get();
        if (principal == null || principal.userId() == null) {
            throw new BizException("AUTH_REQUIRED", "Authentication required to complete passkey registration");
        }
        if (request == null || isBlank(request.challengeId()) || isBlank(request.credentialId())
                || isBlank(request.clientDataJSON()) || isBlank(request.authenticatorData())
                || isBlank(request.publicKeySpki())) {
            throw new BizException("WEBAUTHN_INVALID_REQUEST", "Incomplete WebAuthn registration attestation payload");
        }
        String rpId = normalizeRpId(request.rpId());
        StoredChallenge stored = consumeChallenge(request.challengeId(), "REGISTER");
        if (!rpId.equals(stored.rpId()) || !principal.userId().equals(stored.userId())) {
            throw new BizException("WEBAUTHN_CHALLENGE_MISMATCH", "Registration challenge does not match user or RP");
        }

        verifyClientData(request.clientDataJSON(), "webauthn.create", request.challengeId(), stored, request.origin(), rpId);
        byte[] authData = decodeBase64Url(request.authenticatorData(), "authenticatorData");
        long signCount = verifyAuthenticatorData(authData, rpId);

        int alg = request.algorithm() != null ? request.algorithm() : COSE_ES256;
        decodePublicKey(request.publicKeySpki(), alg);

        WebAuthnCredentialMapper mapper = requireMapper(credentialMapperProvider);
        boolean duplicate = mapper.exists(new LambdaQueryWrapper<WebAuthnCredentialEntity>()
                .eq(WebAuthnCredentialEntity::getCredentialId, request.credentialId())
                .eq(WebAuthnCredentialEntity::getDeleted, 0));
        if (duplicate) {
            throw new BizException("WEBAUTHN_CREDENTIAL_EXISTS", "Passkey credential is already registered");
        }

        WebAuthnCredentialEntity entity = new WebAuthnCredentialEntity();
        entity.setTenantId(principal.tenantId());
        entity.setPublicId(uuidGenerator.generate().toString());
        entity.setUserId(principal.userId());
        entity.setCredentialId(request.credentialId().trim());
        entity.setPublicKeySpki(request.publicKeySpki().trim());
        entity.setAlgorithm(alg);
        entity.setSignCount(signCount);
        entity.setAaguid(request.aaguid());
        entity.setTransports(request.transports());
        entity.setDeviceName(isBlank(request.deviceName()) ? "Security Key / Passkey" : request.deviceName().trim());
        entity.setStatus("ENABLED");
        entity.setVersion(0);
        entity.setDeleted(0);
        mapper.insert(entity);

        auditRecorder.record("iam", "passkey", entity.getPublicId(), "REGISTER", null, entity.getDeviceName(), true);
        return toSummary(entity);
    }

    public AuthenticationOptions beginAuthentication(AuthenticationBeginRequest request) {
        String tenantCode = (request != null && !isBlank(request.tenantCode())) ? request.tenantCode().trim() : "platform";
        TenantEntity tenant = findTenantByCode(tenantCode);
        String rpId = normalizeRpId(request != null ? request.rpId() : null);

        Long userId = null;
        List<Map<String, Object>> allowCredentials = List.of();
        if (request != null && !isBlank(request.username())) {
            try (var ignored = TenantAccessContextHolder.openTenant(tenant.getId(), tenant.getTenantCode(), null)) {
                UserEntity user = requireMapper(userMapperProvider).selectByTenantAndUsername(
                        tenant.getId(), request.username().trim());
                if (user != null) {
                    userId = user.getId();
                    allowCredentials = requireMapper(credentialMapperProvider).selectList(
                                    new LambdaQueryWrapper<WebAuthnCredentialEntity>()
                                            .eq(WebAuthnCredentialEntity::getUserId, userId)
                                            .eq(WebAuthnCredentialEntity::getStatus, "ENABLED")
                                            .eq(WebAuthnCredentialEntity::getDeleted, 0))
                            .stream()
                            .map(c -> Map.<String, Object>of("type", "public-key", "id", c.getCredentialId()))
                            .toList();
                }
            }
        }

        byte[] raw = new byte[32];
        secureRandom.nextBytes(raw);
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        String challengeId = uuidGenerator.generate().toString();
        saveChallenge(challengeId, new StoredChallenge(
                challenge, rpId, tenant.getId(), userId, "AUTHENTICATE", Instant.now().plus(CHALLENGE_TTL)));

        return new AuthenticationOptions(challengeId, challenge, rpId, allowCredentials, 300_000L);
    }

    @Transactional
    public AuthLoginResponse finishAuthentication(AuthenticationFinishRequest request, String ip, String userAgent) {
        if (request == null || isBlank(request.challengeId()) || isBlank(request.credentialId())
                || isBlank(request.clientDataJSON()) || isBlank(request.authenticatorData())
                || isBlank(request.signature())) {
            throw new BizException("WEBAUTHN_INVALID_REQUEST", "Incomplete WebAuthn assertion payload");
        }
        String tenantCode = !isBlank(request.tenantCode()) ? request.tenantCode().trim() : "platform";
        TenantEntity tenant = findTenantByCode(tenantCode);
        String rpId = normalizeRpId(request.rpId());
        StoredChallenge stored = consumeChallenge(request.challengeId(), "AUTHENTICATE");
        if (!rpId.equals(stored.rpId()) || tenant.getId() != stored.tenantId()) {
            throw new BizException("WEBAUTHN_CHALLENGE_MISMATCH", "Assertion challenge does not match tenant or RP");
        }

        byte[] clientDataBytes = verifyClientData(
                request.clientDataJSON(), "webauthn.get", request.challengeId(), stored, request.origin(), rpId);
        byte[] authData = decodeBase64Url(request.authenticatorData(), "authenticatorData");
        long assertedSignCount = verifyAuthenticatorData(authData, rpId);

        try (var ignored = TenantAccessContextHolder.openTenant(tenant.getId(), tenant.getTenantCode(), null)) {
            WebAuthnCredentialMapper mapper = requireMapper(credentialMapperProvider);
            WebAuthnCredentialEntity credential = mapper.selectOne(new LambdaQueryWrapper<WebAuthnCredentialEntity>()
                    .eq(WebAuthnCredentialEntity::getCredentialId, request.credentialId().trim())
                    .eq(WebAuthnCredentialEntity::getStatus, "ENABLED")
                    .eq(WebAuthnCredentialEntity::getDeleted, 0)
                    .last("LIMIT 1"));
            if (credential == null) {
                throw new BizException("WEBAUTHN_CREDENTIAL_NOT_FOUND", "Passkey credential not found or disabled");
            }
            if (stored.userId() != null && !stored.userId().equals(credential.getUserId())) {
                throw new BizException("WEBAUTHN_USER_MISMATCH", "Passkey credential does not belong to target user");
            }

            long previousCount = credential.getSignCount() == null ? 0L : credential.getSignCount();
            if ((previousCount > 0 || assertedSignCount > 0) && assertedSignCount <= previousCount) {
                throw new BizException("WEBAUTHN_SIGN_COUNT_REPLAY", "Passkey signCount did not advance (possible cloned authenticator)");
            }

            verifySignature(authData, clientDataBytes, request.signature(), credential.getPublicKeySpki(),
                    credential.getAlgorithm() != null ? credential.getAlgorithm() : COSE_ES256);

            credential.setSignCount(assertedSignCount);
            credential.setLastUsedAt(LocalDateTime.now());
            mapper.updateById(credential);

            auditRecorder.record("iam", "passkey", credential.getPublicId(), "AUTHENTICATE", null,
                    String.valueOf(assertedSignCount), true);
            return authService.issueSessionForVerifiedUser(tenant.getId(), credential.getUserId(), "PASSKEY", ip, userAgent);
        }
    }

    public List<PasskeySummary> listMyPasskeys() {
        AuthPrincipal principal = AuthContext.get();
        if (principal == null || principal.userId() == null) {
            throw new BizException("AUTH_REQUIRED", "Authentication required");
        }
        return requireMapper(credentialMapperProvider).selectList(new LambdaQueryWrapper<WebAuthnCredentialEntity>()
                        .eq(WebAuthnCredentialEntity::getUserId, principal.userId())
                        .eq(WebAuthnCredentialEntity::getDeleted, 0)
                        .orderByDesc(WebAuthnCredentialEntity::getId))
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public void revokePasskey(UUID publicId) {
        AuthPrincipal principal = AuthContext.get();
        if (principal == null || principal.userId() == null) {
            throw new BizException("AUTH_REQUIRED", "Authentication required");
        }
        WebAuthnCredentialMapper mapper = requireMapper(credentialMapperProvider);
        WebAuthnCredentialEntity entity = mapper.selectOne(new LambdaQueryWrapper<WebAuthnCredentialEntity>()
                .eq(WebAuthnCredentialEntity::getPublicId, publicId.toString())
                .eq(WebAuthnCredentialEntity::getUserId, principal.userId())
                .eq(WebAuthnCredentialEntity::getDeleted, 0)
                .last("LIMIT 1"));
        if (entity == null) {
            throw new BizException("WEBAUTHN_CREDENTIAL_NOT_FOUND", "Passkey not found");
        }
        entity.setStatus("REVOKED");
        entity.setDeleted(1);
        entity.setDeletedAt(LocalDateTime.now());
        mapper.updateById(entity);
        auditRecorder.record("iam", "passkey", entity.getPublicId(), "REVOKE", "ENABLED", "REVOKED", true);
    }

    private void saveChallenge(String challengeId, StoredChallenge meta) {
        String payload = meta.challenge() + "|" + meta.rpId() + "|" + meta.tenantId() + "|"
                + (meta.userId() == null ? "" : meta.userId()) + "|" + meta.mode();
        StringRedisTemplate redis = redisTemplateProvider != null ? redisTemplateProvider.getIfAvailable() : null;
        if (redis != null) {
            try {
                redis.opsForValue().set("passkey:challenge:" + challengeId, payload, CHALLENGE_TTL);
                return;
            } catch (Exception ignored) {
                // Fall back to bounded local cache when Redis is unavailable
            }
        }
        if (localChallenges.size() >= MAX_LOCAL_CHALLENGES) {
            localChallenges.clear();
        }
        localChallenges.put(challengeId, meta);
    }

    private StoredChallenge consumeChallenge(String challengeId, String expectedMode) {
        StringRedisTemplate redis = redisTemplateProvider != null ? redisTemplateProvider.getIfAvailable() : null;
        if (redis != null) {
            try {
                String raw = redis.opsForValue().getAndDelete("passkey:challenge:" + challengeId);
                if (raw != null) {
                    String[] parts = raw.split("\\|", -1);
                    if (parts.length >= 5 && expectedMode.equals(parts[4])) {
                        Long uid = parts[3].isBlank() ? null : Long.parseLong(parts[3]);
                        return new StoredChallenge(parts[0], parts[1], Long.parseLong(parts[2]), uid,
                                parts[4], Instant.now().plus(CHALLENGE_TTL));
                    }
                    throw new BizException("WEBAUTHN_CHALLENGE_INVALID", "Challenge mode mismatch");
                }
            } catch (BizException ex) {
                throw ex;
            } catch (Exception ignored) {
            }
        }
        StoredChallenge local = localChallenges.remove(challengeId);
        if (local == null || local.expiresAt().isBefore(Instant.now()) || !expectedMode.equals(local.mode())) {
            throw new BizException("WEBAUTHN_CHALLENGE_EXPIRED", "Passkey challenge expired or already used");
        }
        return local;
    }

    private byte[] verifyClientData(
            String clientDataBase64Url,
            String expectedType,
            String challengeId,
            StoredChallenge stored,
            String expectedOrigin,
            String rpId
    ) {
        byte[] bytes = decodeBase64Url(clientDataBase64Url, "clientDataJSON");
        String expectedChallenge = stored.challenge();
        if (!rpId.equals(stored.rpId())) {
            throw new BizException("WEBAUTHN_RP_MISMATCH", "RP ID mismatch");
        }
        try {
            JsonNode node = objectMapper.readTree(bytes);
            if (!expectedType.equals(node.path("type").asText())) {
                throw new BizException("WEBAUTHN_TYPE_MISMATCH", "Unexpected clientDataJSON type");
            }
            if (!expectedChallenge.equals(node.path("challenge").asText())) {
                throw new BizException("WEBAUTHN_CHALLENGE_MISMATCH", "clientDataJSON challenge mismatch");
            }
            String actualOrigin = node.path("origin").asText("");
            if (isBlank(actualOrigin)) {
                throw new BizException("WEBAUTHN_ORIGIN_INVALID", "Missing origin in clientDataJSON");
            }
            if (!isBlank(expectedOrigin) && !expectedOrigin.equals(actualOrigin)) {
                throw new BizException("WEBAUTHN_ORIGIN_MISMATCH", "Origin does not match expected origin");
            }
            URI originUri = URI.create(actualOrigin);
            String host = originUri.getHost();
            if (host == null || (!host.equalsIgnoreCase(rpId) && !host.toLowerCase().endsWith("." + rpId.toLowerCase()))) {
                throw new BizException("WEBAUTHN_ORIGIN_MISMATCH", "Origin host does not match rpId");
            }
            return bytes;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException("WEBAUTHN_CLIENT_DATA_INVALID", "Malformed clientDataJSON");
        }
    }

    private long verifyAuthenticatorData(byte[] authData, String rpId) {
        if (authData.length < 37) {
            throw new BizException("WEBAUTHN_AUTH_DATA_INVALID", "authenticatorData too short");
        }
        try {
            byte[] expectedRpHash = MessageDigest.getInstance("SHA-256")
                    .digest(rpId.getBytes(StandardCharsets.UTF_8));
            for (int i = 0; i < 32; i++) {
                if (authData[i] != expectedRpHash[i]) {
                    throw new BizException("WEBAUTHN_RP_HASH_MISMATCH", "authenticatorData rpIdHash mismatch");
                }
            }
            byte flags = authData[32];
            if ((flags & 0x01) == 0) {
                throw new BizException("WEBAUTHN_USER_NOT_PRESENT", "User Presence (UP) flag is not set");
            }
            return Integer.toUnsignedLong(ByteBuffer.wrap(authData, 33, 4).getInt());
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException("WEBAUTHN_AUTH_DATA_INVALID", "Failed to verify authenticatorData");
        }
    }

    private void verifySignature(byte[] authData, byte[] clientDataBytes, String sigBase64Url, String spkiBase64Url, int alg) {
        try {
            byte[] clientHash = MessageDigest.getInstance("SHA-256").digest(clientDataBytes);
            byte[] signedData = new byte[authData.length + clientHash.length];
            System.arraycopy(authData, 0, signedData, 0, authData.length);
            System.arraycopy(clientHash, 0, signedData, authData.length, clientHash.length);

            PublicKey publicKey = decodePublicKey(spkiBase64Url, alg);
            String sigAlg = switch (alg) {
                case COSE_ES256 -> "SHA256withECDSA";
                case COSE_EDDSA -> "Ed25519";
                case COSE_RS256 -> "SHA256withRSA";
                default -> throw new BizException("WEBAUTHN_ALG_UNSUPPORTED", "Unsupported COSE algorithm: " + alg);
            };
            Signature verifier = Signature.getInstance(sigAlg);
            verifier.initVerify(publicKey);
            verifier.update(signedData);
            if (!verifier.verify(decodeBase64Url(sigBase64Url, "signature"))) {
                throw new BizException("WEBAUTHN_SIGNATURE_INVALID", "Passkey cryptographic signature verification failed");
            }
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException("WEBAUTHN_SIGNATURE_INVALID", "Invalid passkey signature payload");
        }
    }

    private PublicKey decodePublicKey(String spkiBase64Url, int alg) {
        try {
            byte[] keyBytes = decodeBase64Url(spkiBase64Url, "publicKeySpki");
            String keyAlg = switch (alg) {
                case COSE_ES256 -> "EC";
                case COSE_EDDSA -> "Ed25519";
                case COSE_RS256 -> "RSA";
                default -> throw new BizException("WEBAUTHN_ALG_UNSUPPORTED", "Unsupported COSE algorithm: " + alg);
            };
            return KeyFactory.getInstance(keyAlg).generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException("WEBAUTHN_PUBLIC_KEY_INVALID", "Invalid SPKI public key");
        }
    }

    private TenantEntity findTenantByCode(String tenantCode) {
        TenantEntity tenant = requireMapper(tenantMapperProvider).selectOne(new LambdaQueryWrapper<TenantEntity>()
                .eq(TenantEntity::getTenantCode, tenantCode)
                .eq(TenantEntity::getDeleted, 0)
                .last("LIMIT 1"));
        if (tenant == null) {
            throw new BizException("TENANT_NOT_FOUND", "Tenant not found: " + tenantCode);
        }
        return tenant;
    }

    private PasskeySummary toSummary(WebAuthnCredentialEntity e) {
        return new PasskeySummary(
                UUID.fromString(e.getPublicId()), e.getCredentialId(), e.getAlgorithm(),
                e.getSignCount(), e.getDeviceName(), e.getTransports(), e.getLastUsedAt(), e.getStatus()
        );
    }

    private static byte[] decodeBase64Url(String value, String field) {
        try {
            return Base64.getUrlDecoder().decode(value.trim());
        } catch (Exception ex) {
            throw new BizException("WEBAUTHN_BASE64_INVALID", "Invalid Base64Url encoding for " + field);
        }
    }

    private static String normalizeRpId(String rpId) {
        return isBlank(rpId) ? "localhost" : rpId.trim().toLowerCase();
    }

    private static boolean isBlank(String v) {
        return v == null || v.isBlank();
    }

    private <T> T requireMapper(ObjectProvider<T> provider) {
        T mapper = provider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "Passkey operations require the db profile");
        }
        return mapper;
    }
}
