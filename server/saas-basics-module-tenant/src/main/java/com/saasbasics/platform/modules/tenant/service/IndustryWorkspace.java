package com.saasbasics.platform.modules.tenant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.tenant.entity.IndustryServiceEntity;
import com.saasbasics.platform.modules.tenant.mapper.IndustryServiceMapper;
import com.saasbasics.platform.security.vault.CredentialVaultService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IndustryWorkspace {
    public record Entry(String code, String name, String kind, int revision, String url,
                        List<IndustryManifest.Field> columns, List<IndustryManifest.Action> actions, List<IndustryManifest.Page> pages) {
        Entry(String code, String name, String kind, int revision, String url, List<IndustryManifest.Field> columns,
                List<IndustryManifest.Action> actions) {
            this(code, name, kind, revision, url, columns, actions, List.of());
        }
    }
    public record Command(UUID requestId, Map<String, String> input) {}
    public static class ServiceUnavailable extends RuntimeException {}
    private final IndustryServiceMapper mapper;
    private final IndustryRegistry registry;
    private final ExtensionInstallations extensions;
    private final CredentialVaultService vault;
    private final ObjectMapper json;
    private final String gateway;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final Map<String, Semaphore> limits = new ConcurrentHashMap<>();
    private final Semaphore total = new Semaphore(24);
    public IndustryWorkspace(IndustryServiceMapper mapper, IndustryRegistry registry, ExtensionInstallations extensions,
            CredentialVaultService vault, ObjectMapper json,
            @Value("${saas.industry.gateway:http://127.0.0.1:18086}") String gateway) {
        this.mapper = mapper;
        this.registry = registry;
        this.extensions = extensions;
        this.vault = vault;
        this.json = json;
        this.gateway = gateway.replaceAll("/+$", "");
    }

    private com.saasbasics.platform.modules.tenant.api.GeneratedApplicationExecutor projectExecutor;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setProjectExecutor(com.saasbasics.platform.modules.tenant.api.GeneratedApplicationExecutor executor) {
        this.projectExecutor = executor;
    }

    @Transactional
    public void publish(IndustryManifest app, String credential) {
        saveRevision(app, credential, null);
    }

    @Transactional
    public void publishGenerated(IndustryManifest app, String design) {
        if (design == null || !"NATIVE".equals(app.kind())) throw invalid();
        saveRevision(app, null, design);
    }

    private void saveRevision(IndustryManifest app, String credential, String design) {
        if (app == null) throw invalid();
        app.validate();
        var previous = registry.latest(app.code());
        if (previous == null && (extensions.find(app.code()) != null || TenantAppService.SYSTEM_APPS.stream()
                .anyMatch(m -> m.appCode().equals(app.code())))) {
            throw invalid();
        }
        int next = previous == null ? 1 : previous.revision() + 1;
        if (app.revision() != next || previous != null && !previous.kind().equals(app.kind())) {
            throw new BizException("APP_REVISION_INVALID", "请顺序发布同一接入类型的新版本");
        }

        String encrypted = null;
        if (previous != null && "NATIVE".equals(app.kind())
                && (secret(app.code(), previous.revision()) == null) != (design != null)) {
            throw new BizException("APP_REVISION_INVALID","应用接入方式与已发布版本不一致");
        }
        if ("NATIVE".equals(app.kind()) && design == null) {
            if (credential == null && previous != null) {
                encrypted = secret(app.code(), previous.revision());
            } else if (credential != null && credential.matches("[A-Za-z0-9_+/=-]{32,256}")) {
                encrypted = vault.encrypt(credential);
            } else {
                throw new BizException("INVALID_PARAM", "请提供至少 32 位随机服务密钥");
            }
            if (!vault.isEncrypted(encrypted)) {
                throw new BizException("VAULT_REQUIRED", "请先启用平台凭据加密");
            }
        }
        try {
            var revision = new IndustryServiceEntity(app.code(), app.revision(), json.writeValueAsString(app), encrypted);
            revision.setProjectDesignJson(design);
            mapper.insert(revision);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw invalid();
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BizException("APP_REVISION_INVALID", "该版本已登记，请刷新应用目录");
        }
    }

    public List<Entry> entries(String terminal) {
        boolean mobile = mobile(terminal);
        var user = principal();
        var services = mapper.selectInstalledEntries(user.tenantId()).stream()
                .map(entity -> registry.decode(entity.getManifestJson()))
                .filter(app -> (mobile ? app.mobile() : app.pc()) && permitted(user, app.code(), "query"))
                .map(app -> entry(app, user, mobile))
                .filter(java.util.Objects::nonNull)
                .toList();
        return java.util.stream.Stream.concat(services.stream(), extensions.workspaceEntries(user).stream())
                .sorted(java.util.Comparator.comparing(Entry::code)).toList();
    }

    private Entry entry(IndustryManifest app, AuthPrincipal user, boolean mobile) {
        var pages = app.pages().stream().filter(p -> permitted(user, app.code(), p.query()))
                .map(p -> new IndustryManifest.Page(p.key(), p.label(), p.columns(), p.query(),
                        p.actions().stream().filter(a -> permitted(user, app.code(), a)).toList())).toList();
        if (!app.pages().isEmpty() && pages.isEmpty()) return null;
        var actions = app.actions().stream().filter(a -> permitted(user, app.code(), a.key()))
                .filter(a -> app.pages().isEmpty() || pages.stream().anyMatch(p ->
                        p.query().equals(a.key()) || p.actions().contains(a.key()))).toList();
        return new Entry(app.code(), app.name(), app.kind(), app.revision(), mobile ? app.mobileUrl() : app.pcUrl(),
                app.columns(), actions, pages);
    }

    public JsonNode invoke(String code, String action, String terminal, Command command) {
        var user = principal();
        boolean mobile = mobile(terminal);
        IndustryServiceEntity installed = mapper.selectInstalledApp(user.tenantId(), code);
        if (installed == null) throw denied();
        IndustryManifest app = registry.decode(installed.getManifestJson());
        if (!"NATIVE".equals(app.kind()) || !(mobile ? app.mobile() : app.pc())
                || !permitted(user, code, "query") || !permitted(user, code, action)) throw denied();
        var page = app.owner(action);
        if (!app.pages().isEmpty() && (page == null || !permitted(user, code, page.query()))) throw denied();
        var definition = app.actions().stream().filter(a -> a.key().equals(action)).findFirst()
                .orElseThrow(IndustryWorkspace::denied);
        validateInput(definition, command);

        var slots = limits.computeIfAbsent(code, ignored -> new Semaphore(4));
        if (!slots.tryAcquire()) throw new ServiceUnavailable();
        try {
            if (!total.tryAcquire()) throw new ServiceUnavailable();
            try {
                if (installed.getProjectDesignJson() != null) {
                    if (projectExecutor == null) throw new ServiceUnavailable();
                    return projectExecutor.execute(installed.getProjectDesignJson(), action, command.input(), command.requestId(), user);
                }
                return forward(app, action, command, user);
            } finally {
                total.release();
            }
        } finally {
            slots.release();
        }
    }

    private static void validateInput(IndustryManifest.Action action, Command command) {
        if (command == null || command.requestId() == null || command.input() == null) throw invalid();
        var input = command.input();
        var fieldKeys = action.fields().stream().map(IndustryManifest.Field::key).toList();
        if (input.size() > fieldKeys.size() || !fieldKeys.containsAll(input.keySet())) throw invalid();
        for (var field : action.fields()) {
            String value = input.get(field.key());
            if (value == null || value.isBlank()) {
                if (field.required()) throw invalid();
                continue;
            }
            if (value.length() > 2048) throw invalid();
            try {
                if (field.type().equals("number")) new java.math.BigDecimal(value);
                if (field.type().equals("date")) java.time.LocalDate.parse(value);
            } catch (IllegalArgumentException | java.time.DateTimeException e) {
                throw invalid();
            }
        }
    }

    private JsonNode forward(IndustryManifest app, String action, Command command, AuthPrincipal user) {
        try {
            byte[] body = json.writeValueAsBytes(Map.of(
                    "app", app.code(), "revision", app.revision(), "action", action,
                    "tenantId", user.tenantId(), "userId", user.userId(), "issuedAt", Instant.now().getEpochSecond(),
                    "requestId", command.requestId(), "input", command.input()));
            Mac mac = Mac.getInstance("HmacSHA256");
            String credential = vault.decrypt(secret(app.code(), app.revision()));
            mac.init(new SecretKeySpec(credential.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String upstream = gateway.replace("{app}", app.code().replace('_', '-'));
            var uri = URI.create(upstream + "/apps/" + app.code() + "/actions/" + action);
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || !List.of("http", "https").contains(uri.getScheme())) throw new ServiceUnavailable();
            var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(4))
                    .header("Content-Type", "application/json")
                    .header("X-Tianshu-Signature", HexFormat.of().formatHex(mac.doFinal(body)))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            var result = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (result.statusCode() == 403) throw denied();
            if (result.statusCode() == 409 || result.statusCode() == 422) {
                throw new BizException("APP_ACTION_REJECTED", "业务状态已变化或输入无效，请刷新后重试");
            }
            if (result.statusCode() != 200) throw new ServiceUnavailable();
            JsonNode data = json.readTree(result.body());
            if (data == null || ((action.equals("query") || app.pages().stream().anyMatch(p -> p.query().equals(action))) && !data.isArray())) throw new ServiceUnavailable();
            return data;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceUnavailable();
        } catch (java.io.IOException | java.security.GeneralSecurityException | IllegalArgumentException e) {
            throw new ServiceUnavailable();
        }
    }

    private String secret(String code, int revision) {
        IndustryServiceEntity entity = mapper.selectOne(new LambdaQueryWrapper<IndustryServiceEntity>()
                .eq(IndustryServiceEntity::getAppCode, code)
                .eq(IndustryServiceEntity::getRevision, revision));
        return entity != null ? entity.getCredentialCipher() : null;
    }

    static boolean permitted(AuthPrincipal p,String code,String action) {
        return p.isSuperAdmin() || (p.permissions()!=null && p.permissions().contains("biz_"+code+":"+action));
    }

    private static boolean mobile(String terminal) {
        if (!"pc".equals(terminal) && !"mobile".equals(terminal)) throw invalid();
        return "mobile".equals(terminal);
    }
    private static AuthPrincipal principal() {
        var p=AuthContext.get();
        if (p==null || p.userId()==null || p.tenantId()==null) throw denied();
        return p;
    }
    private static BizException invalid() { return new BizException("INVALID_PARAM","无效的应用配置或操作参数"); }
    private static BizException denied() { return new BizException("AUTH_FORBIDDEN","当前用户无权使用此应用或操作"); }
}
