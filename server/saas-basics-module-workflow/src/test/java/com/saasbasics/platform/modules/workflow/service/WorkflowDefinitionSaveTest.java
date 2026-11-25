package com.saasbasics.platform.modules.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.config.MybatisPlusConfig;
import com.saasbasics.platform.modules.workflow.dto.ProcessDefinitionResponse;
import com.saasbasics.platform.modules.workflow.dto.ProcessDefinitionSaveRequest;
import com.saasbasics.platform.modules.workflow.mapper.ProcessDefinitionMapper;
import com.saasbasics.platform.security.EnterpriseDataPermissionHandler;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

class WorkflowDefinitionSaveTest {

    private final FlowableProcessBridgeService engine = mock(FlowableProcessBridgeService.class);
    private WorkflowDefinitionService service;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE test_deployment (id VARCHAR PRIMARY KEY)");
        jdbc.execute("""
                CREATE TABLE wf_process_definition (
                    id BIGINT PRIMARY KEY, tenant_id BIGINT, process_code VARCHAR, process_name VARCHAR,
                    category VARCHAR, icon VARCHAR, color VARCHAR, description VARCHAR, form_schema_json VARCHAR,
                    flow_nodes_json VARCHAR, bpmn_xml VARCHAR, flowable_deployment_id VARCHAR, version INT,
                    status VARCHAR, sort_no INT, created_by BIGINT, updated_by BIGINT, created_at TIMESTAMP,
                    updated_at TIMESTAMP, deleted INT, deleted_at TIMESTAMP, remark VARCHAR)
                """);
        jdbc.update("""
                INSERT INTO wf_process_definition (id, tenant_id, process_code, process_name, category,
                    bpmn_xml, flowable_deployment_id, version, status, deleted)
                VALUES (7, 2, 'WF', 'Initial', 'GENERAL', '<old/>', 'previous', 3, 'PUBLISHED', 0)
                """);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(ds);
        factory.setPlugins(new MybatisPlusConfig().mybatisPlusInterceptor(new EnterpriseDataPermissionHandler(null)));
        factory.setMapperLocations(new ClassPathResource("mapper/ProcessDefinitionMapper.xml"));
        ProcessDefinitionMapper mapper = new SqlSessionTemplate(factory.getObject()).getMapper(ProcessDefinitionMapper.class);
        var proxy = new ProxyFactory(new WorkflowDefinitionService(mapper, mock(FlowableBpmnModelService.class), engine));
        var transactions = new TransactionInterceptor();
        transactions.setTransactionManager(new DataSourceTransactionManager(ds));
        transactions.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        proxy.addAdvice(transactions);
        service = (WorkflowDefinitionService) proxy.getProxy();
        when(engine.deployBpmnXml(any(), any(), any(), any(), any())).thenReturn("new-deployment");
    }

    private static ProcessDefinitionSaveRequest request(String name, Integer version) {
        return new ProcessDefinitionSaveRequest("WF", name, null, null, null, null,
                "[]", "{}", "<process/>", "PUBLISHED", 10, version);
    }

    private ProcessDefinitionResponse save(ProcessDefinitionSaveRequest request) {
        var editor = new AuthPrincipal(1L, 2L, "target", 7L, "editor", "Editor", "TENANT",
                "ACTIVE", null, List.of("workflow:definition:write"), List.of(), List.of(), List.of());
        AuthContext.set(editor);
        try (var scope = TenantAccessContextHolder.openAuthenticated(editor)) {
            return service.updateDefinition(7L, request);
        } finally {
            AuthContext.clear();
        }
    }

    @Test
    void currentDraftDeploysOnceAndAdvancesOneRevision() {
        ProcessDefinitionResponse saved = save(request("Updated", 3));
        assertThat(saved.version()).isEqualTo(4);
        assertThat(saved.flowableDeploymentId()).isEqualTo("new-deployment");
        assertThat(jdbc.queryForObject("SELECT process_name FROM wf_process_definition WHERE id=7", String.class))
                .isEqualTo("Updated");
        verify(engine).deployBpmnXml("WF", "Updated", "GENERAL", 2L, "<process/>");
    }

    @Test
    void staleDraftCannotDeployOrOverwriteTheAcceptedDraft() {
        save(request("Accepted", 3));
        assertThatExceptionOfType(BizException.class).isThrownBy(() -> save(request("Stale", 3)))
                .extracting(BizException::getCode).isEqualTo("CONCURRENT_CONFLICT");
        assertThat(jdbc.queryForObject("SELECT process_name FROM wf_process_definition WHERE id=7", String.class))
                .isEqualTo("Accepted");
        verify(engine, times(1)).deployBpmnXml(any(), any(), any(), any(), any());
    }

    @Test
    void legacyJsonWithoutRevisionStillUpdates() throws Exception {
        var request = new ObjectMapper().readValue("""
                {"processCode":"WF","processName":"Legacy","bpmnXml":"<legacy/>"}
                """, ProcessDefinitionSaveRequest.class);
        assertThat(request.expectedVersion()).isNull();
        assertThat(save(request).version()).isEqualTo(4);
        verify(engine).deployBpmnXml("WF", "Legacy", "GENERAL", 2L, "<legacy/>");
    }

    @Test
    void simultaneousEditorsCannotBothDeployTheSameRevision() throws Exception {
        CountDownLatch deploying = new CountDownLatch(1);
        CountDownLatch finishDeployment = new CountDownLatch(1);
        when(engine.deployBpmnXml(any(), any(), any(), any(), any())).thenAnswer(call -> {
            deploying.countDown();
            if (!finishDeployment.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Deployment wait timed out");
            return "winner";
        });
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> save(request("First", 3)));
            try {
                assertThat(deploying.await(5, TimeUnit.SECONDS)).isTrue();
                var second = workers.submit(() -> save(request("Second", 3)));
                await().atMost(5, TimeUnit.SECONDS).until(() -> jdbc.queryForObject(
                        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS WHERE BLOCKER_ID IS NOT NULL",
                        Integer.class) > 0);
                finishDeployment.countDown();
                assertThat(first.get(5, TimeUnit.SECONDS).version()).isEqualTo(4);
                assertThatThrownBy(() -> second.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(BizException.class);
                verify(engine, times(1)).deployBpmnXml(any(), any(), any(), any(), any());
            } finally {
                finishDeployment.countDown();
            }
        }
    }

    @Test
    void failedDeploymentLeavesThePublishedVersionIntact() {
        when(engine.deployBpmnXml(any(), any(), any(), any(), any())).thenAnswer(call -> {
            jdbc.update("INSERT INTO test_deployment VALUES ('failed')");
            return null;
        });
        assertThatExceptionOfType(BizException.class).isThrownBy(() -> save(request("Invalid", 3)))
                .extracting(BizException::getCode).isEqualTo("WORKFLOW_DEPLOYMENT_FAILED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM test_deployment", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT version FROM wf_process_definition WHERE id=7", Integer.class))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT flowable_deployment_id FROM wf_process_definition WHERE id=7", String.class))
                .isEqualTo("previous");
    }
}
