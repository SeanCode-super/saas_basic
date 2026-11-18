package com.saasbasics.platform.modules.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.id.UuidV7Generator;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.modules.iam.dto.ScimDtos;
import com.saasbasics.platform.modules.iam.dto.ScimDtos.ScimEmail;
import com.saasbasics.platform.modules.iam.dto.ScimDtos.ScimListResponse;
import com.saasbasics.platform.modules.iam.dto.ScimDtos.ScimMeta;
import com.saasbasics.platform.modules.iam.dto.ScimDtos.ScimName;
import com.saasbasics.platform.modules.iam.dto.ScimDtos.ScimPatchOp;
import com.saasbasics.platform.modules.iam.dto.ScimDtos.ScimUserResource;
import com.saasbasics.platform.modules.iam.entity.UserEntity;
import com.saasbasics.platform.modules.iam.mapper.UserMapper;
import java.security.SecureRandom;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SCIM 2.0 (RFC 7643 / RFC 7644) 租户级成员自动同步与生命周期配置服务。
 */
@Service
public class ScimProvisioningService {

    private static final Pattern USERNAME_EQ_FILTER = Pattern.compile(
            "userName\\s+eq\\s+\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE
    );

    private final AuthService authService;
    private final PasswordSecurityService passwordSecurityService;
    private final ObjectProvider<UserMapper> userMapperProvider;
    private final UuidV7Generator uuidGenerator = new UuidV7Generator();
    private final SecureRandom secureRandom = new SecureRandom();

    public ScimProvisioningService(
            AuthService authService,
            PasswordSecurityService passwordSecurityService,
            ObjectProvider<UserMapper> userMapperProvider
    ) {
        this.authService = authService;
        this.passwordSecurityService = passwordSecurityService;
        this.userMapperProvider = userMapperProvider;
    }

    public Map<String, Object> serviceProviderConfig() {
        return Map.of(
                "schemas", List.of(ScimDtos.SPC_SCHEMA),
                "patch", Map.of("supported", true),
                "bulk", Map.of("supported", false, "maxOperations", 0, "maxPayloadSize", 0),
                "filter", Map.of("supported", true, "maxResults", 200),
                "changePassword", Map.of("supported", false),
                "sort", Map.of("supported", false),
                "etag", Map.of("supported", false),
                "authenticationSchemes", List.of(Map.of(
                        "type", "oauthbearertoken",
                        "name", "OAuth Bearer Token",
                        "description", "Authentication scheme using the OAuth Bearer Token Standard",
                        "primary", true
                ))
        );
    }

    public ScimListResponse<Map<String, Object>> schemas() {
        Map<String, Object> userSchema = Map.of(
                "id", ScimDtos.USER_SCHEMA,
                "name", "User",
                "description", "Tenant User Account",
                "attributes", List.of(
                        Map.of("name", "userName", "type", "string", "required", true, "uniqueness", "server"),
                        Map.of("name", "displayName", "type", "string", "required", false),
                        Map.of("name", "emails", "type", "complex", "multiValued", true),
                        Map.of("name", "active", "type", "boolean", "required", false)
                )
        );
        return new ScimListResponse<>(
                List.of(ScimDtos.LIST_SCHEMA),
                1,
                1,
                1,
                List.of(userSchema)
        );
    }

    public ScimListResponse<ScimUserResource> listUsers(String authorizationHeader, String filter, int startIndex, int count) {
        AuthPrincipal principal = authenticateScimBearer(authorizationHeader, "iam:user:query");
        int normalizedStart = Math.max(1, startIndex);
        int normalizedCount = Math.max(1, Math.min(200, count));

        try (TenantAccessContextHolder.Scope ignored = TenantAccessContextHolder.openAuthenticated(principal)) {
            UserMapper mapper = requiredUserMapper();
            LambdaQueryWrapper<UserEntity> query = new LambdaQueryWrapper<UserEntity>()
                    .eq(UserEntity::getTenantId, principal.tenantId())
                    .eq(UserEntity::getDeleted, 0)
                    .orderByAsc(UserEntity::getId);

            if (filter != null && !filter.isBlank()) {
                Matcher m = USERNAME_EQ_FILTER.matcher(filter.trim());
                if (m.find()) {
                    query.eq(UserEntity::getUsername, m.group(1).trim());
                }
            }

            List<UserEntity> allMatches = mapper.selectList(query);
            int total = allMatches.size();
            int fromIdx = Math.min(normalizedStart - 1, total);
            int toIdx = Math.min(fromIdx + normalizedCount, total);
            List<ScimUserResource> resources = allMatches.subList(fromIdx, toIdx)
                    .stream()
                    .map(this::toScimUser)
                    .toList();
            return new ScimListResponse<>(
                    List.of(ScimDtos.LIST_SCHEMA),
                    total,
                    normalizedStart,
                    resources.size(),
                    resources
            );
        }
    }

    public ScimUserResource getUser(String authorizationHeader, String id) {
        AuthPrincipal principal = authenticateScimBearer(authorizationHeader, "iam:user:query");
        try (TenantAccessContextHolder.Scope ignored = TenantAccessContextHolder.openAuthenticated(principal)) {
            UserEntity user = findUserInTenant(requiredUserMapper(), principal.tenantId(), id);
            if (user == null) {
                throw new BizException("SCIM_NOT_FOUND", "SCIM User not found: " + id);
            }
            return toScimUser(user);
        }
    }

    @Transactional
    public ScimUserResource createUser(String authorizationHeader, ScimUserResource request) {
        AuthPrincipal principal = authenticateScimBearer(authorizationHeader, "iam:user:write");
        if (request == null || request.userName() == null || request.userName().isBlank()) {
            throw new BizException("SCIM_BAD_REQUEST", "SCIM userName is required");
        }
        String username = request.userName().trim();
        try (TenantAccessContextHolder.Scope ignored = TenantAccessContextHolder.openAuthenticated(principal)) {
            UserMapper mapper = requiredUserMapper();
            UserEntity existing = mapper.selectByTenantAndUsername(principal.tenantId(), username);
            if (existing != null) {
                throw new BizException("SCIM_CONFLICT", "User with userName already exists: " + username);
            }

            UserEntity user = new UserEntity();
            user.setTenantId(principal.tenantId());
            user.setPublicId(uuidGenerator.generate().toString());
            user.setUserCode("USR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT));
            user.setUsername(username);
            user.setNickname(resolveDisplayName(request, username));
            user.setEmail(resolvePrimaryEmail(request));
            user.setUserType("TENANT_USER");
            user.setStatus(request.active() ? "ENABLED" : "DISABLED");
            user.setPasswordHash(passwordSecurityService.hash(generateRandomPassword()));
            user.setNeedResetPassword(true);
            user.setMfaEnabled(false);
            user.setDeleted(0);
            mapper.insert(user);
            return toScimUser(user);
        }
    }

    @Transactional
    public ScimUserResource patchUser(String authorizationHeader, String id, ScimPatchOp patchOp) {
        AuthPrincipal principal = authenticateScimBearer(authorizationHeader, "iam:user:write");
        try (TenantAccessContextHolder.Scope ignored = TenantAccessContextHolder.openAuthenticated(principal)) {
            UserMapper mapper = requiredUserMapper();
            UserEntity user = findUserInTenant(mapper, principal.tenantId(), id);
            if (user == null) {
                throw new BizException("SCIM_NOT_FOUND", "SCIM User not found: " + id);
            }
            if (patchOp != null && patchOp.Operations() != null) {
                for (Map<String, Object> op : patchOp.Operations()) {
                    applyPatchOperation(user, op);
                }
            }
            mapper.updateById(user);
            return toScimUser(user);
        }
    }

    @Transactional
    public void deactivateUser(String authorizationHeader, String id) {
        AuthPrincipal principal = authenticateScimBearer(authorizationHeader, "iam:user:write");
        try (TenantAccessContextHolder.Scope ignored = TenantAccessContextHolder.openAuthenticated(principal)) {
            UserMapper mapper = requiredUserMapper();
            UserEntity user = findUserInTenant(mapper, principal.tenantId(), id);
            if (user == null) {
                throw new BizException("SCIM_NOT_FOUND", "SCIM User not found: " + id);
            }
            user.setStatus("DISABLED");
            mapper.updateById(user);
        }
    }

    private AuthPrincipal authenticateScimBearer(String authorizationHeader, String requiredPermission) {
        String token = extractBearerToken(authorizationHeader);
        if (token == null) {
            throw new BizException("SCIM_UNAUTHORIZED", "Bearer token is required for SCIM provisioning");
        }
        AuthPrincipal principal = authService.resolvePrincipal(token);
        if (principal == null) {
            throw new BizException("SCIM_UNAUTHORIZED", "Invalid or expired SCIM Bearer token");
        }
        if (!principal.isSuperAdmin()) {
            List<String> perms = principal.permissions();
            if (perms == null || (!perms.contains("*") && !perms.contains(requiredPermission))) {
                throw new BizException("SCIM_FORBIDDEN", "Insufficient permission for SCIM provisioning: " + requiredPermission);
            }
        }
        return principal;
    }

    private String extractBearerToken(String header) {
        if (header != null && !header.isBlank()) {
            String trimmed = header.trim();
            if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
                return trimmed.substring(7).trim();
            }
            return trimmed;
        }
        return null;
    }

    private UserEntity findUserInTenant(UserMapper mapper, Long tenantId, String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String cleanId = id.trim();
        UserEntity byPublicId = mapper.selectByTenantAndPublicId(tenantId, cleanId);
        if (byPublicId != null) {
            return byPublicId;
        }
        if (cleanId.chars().allMatch(Character::isDigit)) {
            try {
                Long numericId = Long.parseLong(cleanId);
                return mapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                        .eq(UserEntity::getTenantId, tenantId)
                        .eq(UserEntity::getId, numericId)
                        .eq(UserEntity::getDeleted, 0)
                        .last("LIMIT 1"));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void applyPatchOperation(UserEntity user, Map<String, Object> op) {
        if (op == null) {
            return;
        }
        String path = op.get("path") != null ? String.valueOf(op.get("path")).trim() : "";
        Object value = op.get("value");

        if ("active".equalsIgnoreCase(path)) {
            user.setStatus(asBoolean(value) ? "ENABLED" : "DISABLED");
        } else if ("displayName".equalsIgnoreCase(path) && value != null) {
            user.setNickname(String.valueOf(value).trim());
        } else if (path.toLowerCase(Locale.ROOT).startsWith("emails") && value instanceof String s) {
            user.setEmail(s.trim());
        } else if (path.isEmpty() && value instanceof Map<?, ?> map) {
            if (map.containsKey("active")) {
                user.setStatus(asBoolean(map.get("active")) ? "ENABLED" : "DISABLED");
            }
            if (map.get("displayName") != null) {
                user.setNickname(String.valueOf(map.get("displayName")).trim());
            }
            if (map.get("emails") instanceof List<?> emails && !emails.isEmpty() && emails.get(0) instanceof Map<?, ?> emailObj) {
                Object val = emailObj.get("value");
                if (val != null) {
                    user.setEmail(String.valueOf(val).trim());
                }
            }
        }
    }

    private boolean asBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return "true".equalsIgnoreCase(String.valueOf(value).trim());
    }

    private String resolveDisplayName(ScimUserResource req, String fallbackUsername) {
        if (req.displayName() != null && !req.displayName().isBlank()) {
            return req.displayName().trim();
        }
        if (req.name() != null && req.name().formatted() != null && !req.name().formatted().isBlank()) {
            return req.name().formatted().trim();
        }
        return fallbackUsername;
    }

    private String resolvePrimaryEmail(ScimUserResource req) {
        if (req.emails() == null || req.emails().isEmpty()) {
            return null;
        }
        for (ScimEmail email : req.emails()) {
            if (email != null && email.primary() && email.value() != null && !email.value().isBlank()) {
                return email.value().trim();
            }
        }
        ScimEmail first = req.emails().get(0);
        return (first != null && first.value() != null && !first.value().isBlank()) ? first.value().trim() : null;
    }

    ScimUserResource toScimUser(UserEntity user) {
        String resourceId = (user.getPublicId() != null && !user.getPublicId().isBlank())
                ? user.getPublicId()
                : String.valueOf(user.getId());
        String display = (user.getNickname() != null && !user.getNickname().isBlank())
                ? user.getNickname()
                : user.getUsername();
        List<ScimEmail> emails = (user.getEmail() != null && !user.getEmail().isBlank())
                ? List.of(new ScimEmail(user.getEmail(), "work", true))
                : List.of();
        String createdIso = user.getCreatedAt() != null
                ? user.getCreatedAt().atOffset(ZoneOffset.UTC).toString()
                : null;
        String modifiedIso = user.getUpdatedAt() != null
                ? user.getUpdatedAt().atOffset(ZoneOffset.UTC).toString()
                : createdIso;

        return new ScimUserResource(
                List.of(ScimDtos.USER_SCHEMA),
                resourceId,
                user.getUserCode(),
                user.getUsername(),
                display,
                new ScimName(display, null, null),
                emails,
                "ENABLED".equalsIgnoreCase(user.getStatus()),
                new ScimMeta("User", createdIso, modifiedIso, "/scim/v2/Users/" + resourceId)
        );
    }

    private String generateRandomPassword() {
        byte[] bytes = new byte[12];
        secureRandom.nextBytes(bytes);
        return "Scim!" + HexFormat.of().formatHex(bytes);
    }

    private UserMapper requiredUserMapper() {
        UserMapper mapper = userMapperProvider != null ? userMapperProvider.getIfAvailable() : null;
        if (mapper == null) {
            throw new BizException("MAPPER_UNAVAILABLE", "UserMapper unavailable");
        }
        return mapper;
    }
}
