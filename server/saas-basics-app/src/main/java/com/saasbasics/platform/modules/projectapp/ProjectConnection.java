package com.saasbasics.platform.modules.projectapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.codegen.project.ProjectDesign;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Operations and identity mappings approved by the owner of the business API. */
@Component
public class ProjectConnection {
    record Login(String loginPath, String identityPath, String usernameField, String passwordField, String tokenAt,
                 String tenantAt, String userAt, String roleAt, String statusAt, List<String> allowedStatuses) {}
    record Member(String username, String password, String tenant, String user, String role, List<String> actions) {
        @Override public String toString() { return "Project member binding"; }
    }
    record Ownership(String field, String page, String idAt, String tenantAt, String userAt, String labelAt) {}
    record Profile(String origin, String contract, Login auth, String codeAt, JsonNode successCode,
                   Map<String,Member> members, Map<String,List<Ownership>> inputScopes,
                   Map<String,Map<String,Map<String,String>>> displayValues) {}
    private final ObjectMapper json;
    private final Path directory;
    public ProjectConnection(ObjectMapper json, @Value("${saas.project-connections-directory:}") String directory) {
        this.json = json;
        this.directory = directory.isBlank() ? null : Path.of(directory);
    }

    Profile load(ProjectDesign design) {
        if (directory == null || !design.code().matches("[a-z][a-z0-9_]{0,25}")) throw unconfigured();
        try {
            var path = directory.resolve(design.code()).resolve(Integer.toString(design.revision()));
            var profile = json.readValue(path.resolve("connection.json").toFile(), Profile.class);
            var uri = URI.create(profile.origin());
            boolean loopback = List.of("127.0.0.1","localhost","[::1]").contains(uri.getHost());
            if (!("https".equals(uri.getScheme()) || loopback && "http".equals(uri.getScheme())) || uri.getUserInfo() != null
                    || !uri.getPath().isEmpty() || uri.getQuery() != null || uri.getFragment() != null) throw unconfigured();
            ProjectDesign allowed = json.readValue(path.resolve(profile.contract()).toFile(), ProjectDesign.class);
            if (!design.code().equals(allowed.code()) || design.revision() != allowed.revision()) throw unconfigured();
            // Names, ordering and visible columns may be edited. A draft cannot redirect credentials, widen
            // a command payload or expose an unapproved response property.
            for (var page : design.pages()) {
                var source = allowed.pages().stream().filter(p -> p.key().equals(page.key())).findFirst().orElseThrow(ProjectConnection::unconfigured);
                if (!source.path().equals(page.path()) || !source.rowsAt().equals(page.rowsAt())
                        || page.columns().stream().anyMatch(c -> source.columns().stream().noneMatch(
                            f -> f.key().equals(c.key()) && f.type().equals(c.type()) && f.source().equals(c.source())))
                        || !source.actions().containsAll(page.actions())) throw unconfigured();
            }
            return profile;
        } catch (IOException | IllegalArgumentException e) { throw unconfigured(); }
    }

    static BizException unconfigured() { return new BizException("PROJECT_CONNECTION_REQUIRED","请为该应用修订配置并核对业务连接"); }
    static BizException denied() { return new BizException("AUTH_FORBIDDEN","当前成员未获准访问该业务数据或操作"); }
}
