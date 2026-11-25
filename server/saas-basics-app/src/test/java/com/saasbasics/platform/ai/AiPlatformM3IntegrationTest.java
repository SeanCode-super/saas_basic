package com.saasbasics.platform.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saasbasics.platform.SaasBasicsApplication;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.FinetuneDatasetSaveRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.FinetuneJobCreateRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.FinetuneSampleAddRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.KnowledgeBaseSaveRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.KnowledgeTextIngestRequest;
import com.saasbasics.platform.modules.ai.application.service.AiBillingReconciliationService;
import com.saasbasics.platform.modules.ai.application.service.AiMeteringQuotaService;
import com.saasbasics.platform.modules.ai.application.service.FinetuneStudioService;
import com.saasbasics.platform.modules.ai.application.service.KnowledgeRagService;
import com.saasbasics.platform.modules.ai.application.service.RagEvaluationService;
import com.saasbasics.platform.modules.ai.application.service.RagEvaluationService.EvalCase;
import com.saasbasics.platform.modules.ai.application.service.RagEvaluationService.EvalRequest;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiRunEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.AiRunMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = SaasBasicsApplication.class)
class AiPlatformM3IntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.3.0")
            .withDatabaseName("saas_basics")
            .withUsername("root")
            .withPassword("root");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired private KnowledgeRagService ragService;
    @Autowired private RagEvaluationService ragEvaluationService;
    @Autowired private AiRunMapper aiRunMapper;
    @Autowired private AiMeteringQuotaService quotaService;
    @Autowired private AiBillingReconciliationService reconciliationService;
    @Autowired private FinetuneStudioService finetuneStudioService;

    @AfterEach
    void cleanup() {
        AuthContext.clear();
        TenantAccessContextHolder.clear();
        finetuneStudioService.setTrainingEndpoint("");
        finetuneStudioService.setAllowPrivateTrainingEndpoint(false);
    }

    @Test
    void runsOfflineRagEvaluationBillingReconciliationAndExternalFinetuneDispatch() throws Exception {
        var principal = new AuthPrincipal(1L, 1L, "platform", 1L, "admin", "Admin",
                "PLATFORM", "ONLINE", null, List.of("*"), List.of(), List.of(1L), List.of());
        AuthContext.set(principal);
        try (var ignored = TenantAccessContextHolder.openAuthenticated(principal)) {
            // 1. Offline RAG Evaluation
            var kb = ragService.saveKnowledgeBase(new KnowledgeBaseSaveRequest(
                    null, "kb_eval_test", "M3 Eval KB", "GENERAL", "BGE", 256, 32, null));
            var doc1 = ragService.ingestText(new KnowledgeTextIngestRequest(
                    kb.id(), "Refund Policy",
                    "Enterprise SaaS annual subscriptions qualify for full refund within 14 days of purchase.", "TEXT"));
            var doc2 = ragService.ingestText(new KnowledgeTextIngestRequest(
                    kb.id(), "SLA Policy",
                    "Production availability SLA guarantees 99.95% monthly uptime and 15-minute P1 response.", "TEXT"));

            var evalReport = ragEvaluationService.evaluate(new EvalRequest(
                    kb.id(), "M3 Benchmark", 3,
                    List.of(
                            new EvalCase("refund window days", List.of(doc1.id()), List.of("refund")),
                            new EvalCase("monthly uptime SLA", List.of(doc2.id()), List.of("99.95%"))
                    )
            ));
            assertThat(evalReport.sampleCount()).isEqualTo(2);
            assertThat(evalReport.recallAtK()).isGreaterThanOrEqualTo(0.5);
            assertThat(evalReport.mrr()).isGreaterThan(0.0);
            assertThat(evalReport.hitRate()).isGreaterThanOrEqualTo(0.5);
            assertThat(ragEvaluationService.listRuns(kb.id())).hasSize(1);

            // 2. Billing Reconciliation (detect discrepancy then autoRepair -> matched)
            AiRunEntity run = new AiRunEntity();
            run.setRunNo("RUN-M3-001");
            run.setUserId(1L);
            run.setUsername("admin");
            run.setModelId("gpt-4o-mini");
            run.setProviderCode("openai");
            run.setStatus("COMPLETED");
            run.setPromptTokens(1000);
            run.setCompletionTokens(500);
            run.setTotalTokens(1500);
            run.setEstimatedCost(new BigDecimal("0.012000"));
            run.setLatencyMs(120L);
            run.setStartedAt(LocalDateTime.now());
            run.setFinishedAt(LocalDateTime.now());
            aiRunMapper.insert(run);

            quotaService.getCurrentQuota(); // ensure quota row exists with 0 used
            String period = YearMonth.now().toString();
            var mismatch = reconciliationService.reconcile(period, false);
            assertThat(mismatch.status()).isEqualTo("DISCREPANCY_DETECTED");
            assertThat(mismatch.tokenDiscrepancy()).isEqualTo(1500L);

            var repaired = reconciliationService.reconcile(period, true);
            assertThat(repaired.status()).isEqualTo("REPAIRED");
            assertThat(repaired.repaired()).isTrue();

            var matched = reconciliationService.reconcile(period, false);
            assertThat(matched.status()).isEqualTo("MATCHED");
            assertThat(matched.tokenDiscrepancy()).isZero();

            // 3. External Fine-Tune Trainer Gateway (fail-closed, SSRF, and live HTTP submit/sync)
            var ds = finetuneStudioService.saveDataset(new FinetuneDatasetSaveRequest(
                    null, "ds_m3_test", "M3 Dataset", "NL2SQL", "ALPACA", null));
            var sample = finetuneStudioService.addSample(new FinetuneSampleAddRequest(
                    ds.id(), "Convert to SQL", "Count customers", "SELECT COUNT(*) FROM crm_customer WHERE deleted=0",
                    "MANUAL", Boolean.TRUE));
            finetuneStudioService.reviewSample(sample.id(), "APPROVED");

            // Unconfigured -> fail closed
            assertThatThrownBy(() -> finetuneStudioService.startTrainingJob(new FinetuneJobCreateRequest(
                    "job-1", ds.id(), "Qwen2.5-7B-Instruct", "LORA", 2, "2e-4", 16)))
                    .isInstanceOf(BizException.class);

            // Cloud metadata IP -> SSRF blocked
            finetuneStudioService.setTrainingEndpoint("http://169.254.169.254/latest/meta-data");
            assertThatThrownBy(() -> finetuneStudioService.startTrainingJob(new FinetuneJobCreateRequest(
                    "job-ssrf", ds.id(), "Qwen2.5-7B-Instruct", "LORA", 2, "2e-4", 16)))
                    .isInstanceOf(BizException.class)
                    .extracting("code").isEqualTo("SSRF_BLOCKED");

            // Live external HTTP trainer server
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/jobs", exchange -> {
                String resp = "POST".equalsIgnoreCase(exchange.getRequestMethod())
                        ? "{\"jobId\":\"ft-ext-9001\",\"status\":\"RUNNING\",\"progressPercent\":10}"
                        : "{\"jobId\":\"ft-ext-9001\",\"status\":\"SUCCEEDED\",\"progressPercent\":100,\"trainLoss\":0.142,\"evalAccuracy\":0.945,\"outputModelId\":\"qwen2.5-nl2sql-v1\"}";
                byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            });
            server.start();
            try {
                int port = server.getAddress().getPort();
                finetuneStudioService.setAllowPrivateTrainingEndpoint(true);
                finetuneStudioService.setTrainingEndpoint("http://127.0.0.1:" + port + "/jobs");

                var started = finetuneStudioService.startTrainingJob(new FinetuneJobCreateRequest(
                        "LoRA-M3", ds.id(), "Qwen2.5-7B-Instruct", "LORA", 2, "2e-4", 16));
                assertThat(started.jobCode()).isEqualTo("ft-ext-9001");
                assertThat(started.status()).isEqualTo("RUNNING");

                var synced = finetuneStudioService.syncTrainingJobStatus(started.id());
                assertThat(synced.status()).isEqualTo("SUCCEEDED");
                assertThat(synced.progressPercent()).isEqualTo(100);
                assertThat(synced.outputModelId()).isEqualTo("qwen2.5-nl2sql-v1");
            } finally {
                server.stop(0);
            }
        }
    }
}
