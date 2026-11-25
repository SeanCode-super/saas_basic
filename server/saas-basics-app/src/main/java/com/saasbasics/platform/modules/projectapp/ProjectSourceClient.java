package com.saasbasics.platform.modules.projectapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.modules.tenant.service.IndustryWorkspace;
import com.saasbasics.platform.common.exception.BizException;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ProjectSourceClient {
    private static final int RESPONSE_LIMIT = 2 * 1024 * 1024;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    ProjectSourceClient(ObjectMapper json) { this.json = json; }

    static JsonNode at(JsonNode node, String path) {
        if (path == null || path.isEmpty()) return node;
        for (String key : path.split("\\.")) node = node.path(key);
        return node;
    }

    String authenticate(ProjectConnection.Profile config, ProjectConnection.Member member) {
        var auth = config.auth();
        var credentials = json.valueToTree(Map.of(auth.usernameField(),member.username(),auth.passwordField(),member.password()));
        var result = send(config,"POST",auth.loginPath(),null,credentials,null);
        String token = at(result,auth.tokenAt()).asText("");
        if (token.isBlank()) throw ProjectConnection.denied();
        var who = send(config,"GET",auth.identityPath(),token,null,null);
        if (!member.tenant().equals(at(who,auth.tenantAt()).asText()) || !member.user().equals(at(who,auth.userAt()).asText())
            || !member.role().equals(at(who,auth.roleAt()).asText()) || auth.statusAt() != null
            && !auth.allowedStatuses().contains(at(who,auth.statusAt()).asText())) throw ProjectConnection.denied();
        return token;
    }

    JsonNode send(ProjectConnection.Profile config,String method,String path,String token,JsonNode data,UUID requestId) {
        if (!path.startsWith("/") || path.startsWith("//")) throw ProjectConnection.unconfigured();
        try {
            var request = HttpRequest.newBuilder(URI.create(config.origin()+path)).timeout(Duration.ofSeconds(5)).header("Accept","application/json");
            if (token != null) request.header("Authorization","Bearer "+token);
            if (requestId != null) request.header("Idempotency-Key",requestId.toString());
            if (data != null) request.header("Content-Type","application/json");
            request.method(method,data == null ? HttpRequest.BodyPublishers.noBody() :
                    HttpRequest.BodyPublishers.ofString(json.writeValueAsString(data),StandardCharsets.UTF_8));
            var response = http.send(request.build(),HttpResponse.BodyHandlers.ofInputStream());
            try (var stream = response.body()) {
                if (response.statusCode() == 401 || response.statusCode() == 403) throw ProjectConnection.denied();
                if (response.statusCode() >= 400 && response.statusCode() < 500) throw rejected();
                if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IndustryWorkspace.ServiceUnavailable();
                byte[] raw = stream.readNBytes(RESPONSE_LIMIT+1);
                if (raw.length > RESPONSE_LIMIT) throw new IndustryWorkspace.ServiceUnavailable();
                JsonNode result = raw.length == 0 ? json.nullNode() : json.readTree(raw);
                if (config.codeAt() != null && !config.successCode().equals(at(result, config.codeAt()))) throw rejected();
                return result;
            }
        } catch (IOException e) { throw new IndustryWorkspace.ServiceUnavailable(); }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IndustryWorkspace.ServiceUnavailable();
        }
    }
    static BizException rejected() { return new BizException("APP_ACTION_REJECTED","请核对输入或业务状态后重试"); }
}
