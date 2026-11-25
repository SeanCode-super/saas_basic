package com.saasbasics.platform.workflow;

import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.modules.audit.service.AuditService;
import com.saasbasics.platform.modules.iam.filter.AuthContextFilter;
import com.saasbasics.platform.modules.iam.service.AuthService;
import com.saasbasics.platform.modules.tenant.service.TenantFeatureService;
import com.saasbasics.platform.modules.workflow.service.WorkflowTaskService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = WorkflowApplication.class, properties = {
        "flowable.process.enabled=false", "flowable.idm.enabled=false", "flowable.eventregistry.enabled=false",
        "spring.flyway.enabled=false", "saas-basics.rate-limit.enabled=false"
})
class WorkflowSessionTest {
    private static final String PENDING = "/api/workflow/tasks/pending";
    @Autowired private WebApplicationContext context;
    @Autowired private AuthContextFilter filter;
    @MockitoBean private AuthService sessions;
    @MockitoBean private WorkflowTaskService tasks;
    @MockitoBean private TenantFeatureService features;
    @MockitoBean private AuditService audit;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(filter).build();
    }

    @Test
    void validSessionSuppliesTenantAndActorToPendingTasks() throws Exception {
        var employee = employee(List.of("workflow:task:query"));
        when(sessions.resolvePrincipal("valid")).thenReturn(employee);
        when(features.hasFeature(7L, "workflow")).thenReturn(true);
        when(tasks.listMyPendingTasks()).thenAnswer(invocation -> {
            assertEquals(employee, AuthContext.get());
            assertEquals(7L, TenantAccessContextHolder.requiredTenantId());
            return List.of();
        });
        mvc.perform(get(PENDING).header("Authorization", "Bearer valid")).andExpect(status().isOk());
        org.mockito.Mockito.verify(tasks).listMyPendingTasks();
        assertNull(AuthContext.get());
        assertNull(TenantAccessContextHolder.current());
    }

    @Test
    void workflowPermissionIsRequiredEvenWithAnActiveSession() throws Exception {
        when(sessions.resolvePrincipal("limited")).thenReturn(employee(List.of()));
        when(features.hasFeature(7L, "workflow")).thenReturn(true);
        mvc.perform(get(PENDING).header("Authorization", "Bearer limited")).andExpect(status().isForbidden());
        verifyNoInteractions(tasks);
    }

    @Test
    void businessRouteRequiresSessionAndHealthProbeRemainsAvailable() throws Exception {
        mvc.perform(get(PENDING)).andExpect(status().isUnauthorized());
        mvc.perform(get(PENDING).header("Authorization", "Bearer revoked")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/workflow/health")).andExpect(status().isOk());
        verifyNoInteractions(tasks);
    }

    private AuthPrincipal employee(List<String> permissions) {
        return new AuthPrincipal(3L, 7L, "example", 9L, "worker", "Worker", "EMPLOYEE",
                "ONLINE", null, permissions, List.of("workflow"), List.of(2L), List.of());
    }
}
