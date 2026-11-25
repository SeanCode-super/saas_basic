package com.saasbasics.platform.modules.tenant.service;

import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.config.MybatisPlusConfig;
import com.saasbasics.platform.common.auth.DataPermissionProvider;
import com.saasbasics.platform.modules.tenant.mapper.IndustryServiceMapper;
import com.saasbasics.platform.modules.tenant.mapper.TenantAppMapper;
import com.saasbasics.platform.modules.tenant.mapper.TenantMapper;
import com.saasbasics.platform.security.EnterpriseDataPermissionHandler;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.io.ClassPathResource;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.security.vault.CredentialVaultService;
import com.saasbasics.platform.security.vault.VaultCryptoProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.*;

class IndustryWorkspaceTest {
    private final ObjectMapper json = new ObjectMapper();
    private final String key = UUID.randomUUID().toString();
    private final JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1"));
    private final AtomicBoolean signatureAccepted = new AtomicBoolean();
    private HttpServer upstream;
    private IndustryWorkspace service;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.execute("CREATE TABLE plat_industry_service(app_code VARCHAR,revision INT,manifest_json VARCHAR,project_design_json VARCHAR,credential_cipher VARCHAR, created_at TIMESTAMP, PRIMARY KEY(app_code,revision))");
        jdbc.execute("CREATE TABLE plat_tenant_app(tenant_id BIGINT,app_code VARCHAR,installed_revision INT,deleted INT,status VARCHAR,expired_at TIMESTAMP)");
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/apps/inspection/actions/query", exchange -> {
            try (exchange) {
                var payload = exchange.getRequestBody().readAllBytes();
                var signed = json.readTree(payload);
                Mac hmac = Mac.getInstance("HmacSHA256");
                hmac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                boolean valid = HexFormat.of().formatHex(hmac.doFinal(payload)).equals(exchange.getRequestHeaders().getFirst("X-Tianshu-Signature"))
                        && signed.get("tenantId").asLong() == 8 && signed.get("userId").asLong() == 12
                        && signed.get("app").asText().equals("inspection") && signed.get("action").asText().equals("query")
                        && signed.get("revision").asInt() == 1 && exchange.getRequestHeaders().getFirst("Authorization") == null;
                signatureAccepted.set(valid);
                byte[] response = "[]".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(valid ? 200 : 403, response.length);
                exchange.getResponseBody().write(response);
            } catch (java.security.GeneralSecurityException e) {
                throw new IllegalStateException(e);
            }
        });
        upstream.start();
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(jdbc.getDataSource());
        factory.setMapperLocations(new ClassPathResource("mapper/IndustryServiceMapper.xml"),
                new ClassPathResource("mapper/TenantAppMapper.xml"), new ClassPathResource("mapper/TenantMapper.xml"));
        factory.setPlugins(new MybatisPlusConfig().mybatisPlusInterceptor(new EnterpriseDataPermissionHandler(
                new StaticListableBeanFactory().getBeanProvider(DataPermissionProvider.class))));
        var session = new SqlSessionTemplate(factory.getObject());
        var mapper = session.getMapper(IndustryServiceMapper.class);
        var registry = new IndustryRegistry(mapper, json);
        var vault = new CredentialVaultService(new VaultCryptoProperties());
        service = new IndustryWorkspace(mapper, registry, new ExtensionInstallations(List.of(), registry,
                        session.getMapper(TenantAppMapper.class), session.getMapper(TenantMapper.class)),
                vault, json, "http://127.0.0.1:" + upstream.getAddress().getPort());
        service.publish(manifest(1, true), key);
        jdbc.update("INSERT INTO plat_tenant_app VALUES(8,'inspection',1,0,'ENABLED',NULL)");
        login(8, "biz_inspection:query");
    }

    private static IndustryManifest manifest(int revision, boolean mobile) {
        var query = new IndustryManifest.Action("query", "查看", List.of(), null);
        var create = new IndustryManifest.Action("create", "新建", List.of(), null);
        return new IndustryManifest("inspection", "设备点检", "", revision, "NATIVE", true, mobile,
                null, null, List.of(), List.of(query, create));
    }

    private static void login(long tenant, String... permissions) {
        AuthContext.set(new AuthPrincipal(1L, tenant, "example", 12L, "employee", "员工", "STAFF", "ACTIVE",
                LocalDateTime.now().plusHours(1), List.of(permissions), List.of(), List.of(), List.of()));
    }

    private IndustryWorkspace.Command command() {
        return new IndustryWorkspace.Command(UUID.randomUUID(), Map.of());
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
        if (upstream != null) upstream.stop(0);
        jdbc.execute("SHUTDOWN");
    }

    @Test
    void signsTenantAndActorAndChecksWritePermissionBeforeForwarding() {
        assertThat(service.entries("mobile").get(0).actions()).extracting(IndustryManifest.Action::key).containsExactly("query");
        assertThatThrownBy(() -> service.invoke("inspection", "create", "mobile", command())).isInstanceOf(BizException.class);
        assertThat(service.invoke("inspection", "query", "mobile", command()).isArray()).isTrue();
        assertThat(signatureAccepted).isTrue();
        login(9, "biz_inspection:query");
        assertThat(service.entries("pc")).isEmpty();
        assertThatThrownBy(() -> service.invoke("inspection", "query", "pc", command())).isInstanceOf(BizException.class);
    }

    @Test
    void keepsInstalledRevisionAndDisablesOnlyTheSelectedTenant() {
        service.publish(manifest(2, false), null);
        assertThat(service.entries("mobile")).extracting(IndustryWorkspace.Entry::revision).containsExactly(1);
        jdbc.update("UPDATE plat_tenant_app SET installed_revision=2 WHERE tenant_id=8");
        assertThat(service.entries("mobile")).isEmpty();
        assertThat(service.entries("pc")).extracting(IndustryWorkspace.Entry::revision).containsExactly(2);
        jdbc.update("UPDATE plat_tenant_app SET status='DISABLED' WHERE tenant_id=8");
        assertThat(service.entries("pc")).isEmpty();
        assertThatThrownBy(() -> service.invoke("inspection", "query", "pc", command())).isInstanceOf(BizException.class);
    }

    @Test
    void installedGeneratedRevisionUsesPlatformExecutionAndTenantPermissions() {
        var app = new IndustryManifest("generated","工坊应用","本地管理",1,"NATIVE",true,true,null,null,
                List.of(),List.of(new IndustryManifest.Action("query","查看",List.of(),null)));
        service.publishGenerated(app,"{}");
        jdbc.update("INSERT INTO plat_tenant_app VALUES(8,'generated',1,0,'ENABLED',NULL)");
        service.setProjectExecutor((design,action,input,id,principal) -> {
            assertThat(principal.tenantId()).isEqualTo(8L);
            assertThat(design).isEqualTo("{}");
            return json.createArrayNode().add("from source");
        });
        login(8, "biz_generated:query");
        assertThat(service.invoke("generated","query","pc",command()).get(0).asText()).isEqualTo("from source");
        assertThatThrownBy(() -> service.publish(new IndustryManifest("generated","工坊应用","本地管理",2,
                "NATIVE",true,true,null,null,List.of(),app.actions()),key)).isInstanceOf(BizException.class);
        login(9, "biz_generated:query");
        assertThatThrownBy(() -> service.invoke("generated","query","mobile",command())).isInstanceOf(BizException.class);
    }

    @Test
    void multiPageActionsRequireAccessToTheirOwnPage() {
        var field = new IndustryManifest.Field("title","标题","text",true);
        var query = new IndustryManifest.Action("query","进入",List.of(),null);
        var tasks = new IndustryManifest.Action("tasks_list","任务",List.of(),null);
        var results = new IndustryManifest.Action("results_list","结果",List.of(),null);
        var create = new IndustryManifest.Action("create_task","创建",List.of(field),null);
        var app = new IndustryManifest("matrix","矩阵","业务工作台",1,"NATIVE",true,true,null,null,List.of(),
                List.of(query,tasks,results,create),List.of(
                new IndustryManifest.Page("tasks","任务",List.of(field),"tasks_list",List.of("create_task")),
                new IndustryManifest.Page("results","结果",List.of(field),"results_list",List.of())));
        service.publish(app,key);
        jdbc.update("INSERT INTO plat_tenant_app VALUES(8,'matrix',1,0,'ENABLED',NULL)");
        login(8,"biz_matrix:query","biz_matrix:results_list","biz_matrix:create_task");
        assertThat(service.entries("mobile").getFirst().pages()).extracting(IndustryManifest.Page::key).containsExactly("results");
        assertThatThrownBy(() -> service.invoke("matrix","create_task","pc",command())).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.invoke("matrix","query","pc",command())).isInstanceOf(BizException.class);
        login(8,"biz_matrix:query");
        assertThat(service.entries("pc")).isEmpty();
    }

    @Test
    void directoryRemainsAvailableWhenServiceStops() {
        upstream.stop(0);
        upstream = null;
        assertThatThrownBy(() -> service.invoke("inspection", "query", "pc", command()))
                .isInstanceOf(IndustryWorkspace.ServiceUnavailable.class);
        assertThat(service.entries("pc")).hasSize(1);
    }
}
