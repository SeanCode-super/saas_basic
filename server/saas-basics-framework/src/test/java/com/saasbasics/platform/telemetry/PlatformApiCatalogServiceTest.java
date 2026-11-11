package com.saasbasics.platform.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

class PlatformApiCatalogServiceTest {

    @Test
    void computesSafeMinimalPrefixesWithoutCrossContamination() {
        StaticListableBeanFactory bf = new StaticListableBeanFactory();
        MockEnvironment env = new MockEnvironment().withProperty("spring.application.name", "saas-basics-server");
        PlatformApiCatalogService service = new PlatformApiCatalogService(
                bf.getBeanProvider(RequestMappingHandlerMapping.class),
                env
        );

        List<String> all = List.of(
                "/api/system/config",
                "/api/system/security/transport/overview",
                "/api/system/security/transport/policy",
                "/api/devops/services",
                "/api/devops/services/*",
                "/api/ai/models",
                "/api/ai/runs",
                "/api/auth/login"
        );

        // Selecting entire /api/devops module collapses cleanly to /api/devops/**
        List<String> devopsRules = service.computeSafeAntPatterns(
                List.of("/api/devops/services", "/api/devops/services/*"),
                all
        );
        assertThat(devopsRules).containsExactly("/api/devops/**");

        // Selecting /api/system/security/transport subpackage collapses to /api/system/security/** without colliding with /api/system/config
        List<String> securityRules = service.computeSafeAntPatterns(
                List.of("/api/system/security/transport/overview", "/api/system/security/transport/policy"),
                all
        );
        assertThat(securityRules).containsExactly("/api/system/security/**");
        assertThat(securityRules).doesNotContain("/api/system/**");

        // Selecting ALL collapses cleanly to /api/**
        assertThat(service.computeSafeAntPatterns(all, all)).containsExactly("/api/**");
    }

    @Test
    void normalizesTemplateVariablesAndTracksStatsThroughMockHandlerMapping() throws Exception {
        assertThat(PlatformApiCatalogService.normalizeAntPattern("/api/iam/users/{id}/roles/{roleId}"))
                .isEqualTo("/api/iam/users/*/roles/*");

        RequestMappingHandlerMapping mapping = new RequestMappingHandlerMapping();
        SampleSystemController controller = new SampleSystemController();
        // Register handler method via StaticApplicationContext
        org.springframework.web.context.support.StaticWebApplicationContext ctx =
                new org.springframework.web.context.support.StaticWebApplicationContext();
        ctx.getBeanFactory().registerSingleton("sampleSystemController", controller);
        mapping.setApplicationContext(ctx);
        mapping.afterPropertiesSet();

        StaticListableBeanFactory bf = new StaticListableBeanFactory();
        bf.addBean("requestMappingHandlerMapping", mapping);
        MockEnvironment env = new MockEnvironment().withProperty("spring.application.name", "saas-basics-test");

        PlatformApiCatalogService service = new PlatformApiCatalogService(
                bf.getBeanProvider(RequestMappingHandlerMapping.class),
                env
        );
        service.afterSingletonsInstantiated();

        PlatformApiCatalogService.ApiCatalogSnapshot initial = service.buildSnapshot(null);
        assertThat(initial.summary().totalEndpoints()).isEqualTo(2);
        assertThat(initial.packageTree()).hasSize(1);
        assertThat(initial.packageTree().get(0).nodeType()).isEqualTo("PROJECT");

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/system/demo/42");
        req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/system/demo/{id}");
        service.recordHttpCall(req, 200, 18L);
        service.recordTransportOutcome("GET", "/api/system/demo/42", "encrypted");
        service.recordTransportOutcome("GET", "/api/system/demo/42", "plaintext");

        PlatformApiCatalogService.ApiCatalogSnapshot updated = service.buildSnapshot(null);
        PlatformApiCatalogService.ApiEndpointStatView stat = updated.endpoints().stream()
                .filter(e -> e.pathPattern().equals("/api/system/demo/{id}"))
                .findFirst()
                .orElseThrow();
        assertThat(stat.totalCalls()).isEqualTo(1L);
        assertThat(stat.encryptedCalls()).isEqualTo(1L);
        assertThat(stat.plaintextCalls()).isEqualTo(1L);
        assertThat(stat.avgLatencyMs()).isEqualTo(18.0);
        assertThat(stat.antPattern()).isEqualTo("/api/system/demo/*");
    }

    @org.springframework.web.bind.annotation.RestController
    @org.springframework.web.bind.annotation.RequestMapping("/api/system/demo")
    static class SampleSystemController {

        @org.springframework.web.bind.annotation.GetMapping("/{id}")
        public String detail(@org.springframework.web.bind.annotation.PathVariable String id) {
            return id;
        }

        @org.springframework.web.bind.annotation.PostMapping("/create")
        public String create() {
            return "ok";
        }
    }
}
