package com.saasbasics.platform.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saasbasics.platform.SaasBasicsApplication;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.modules.ai.application.dto.AgentWorkflowDtos.WorkflowEdge;
import com.saasbasics.platform.modules.ai.application.dto.AgentWorkflowDtos.WorkflowExecuteRequest;
import com.saasbasics.platform.modules.ai.application.dto.AgentWorkflowDtos.WorkflowGraph;
import com.saasbasics.platform.modules.ai.application.dto.AgentWorkflowDtos.WorkflowNode;
import com.saasbasics.platform.modules.ai.application.dto.AgentWorkflowDtos.WorkflowSaveRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiModelConfigSaveRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiProviderSaveRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiTestConnectionRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.KnowledgeBaseSaveRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.KnowledgeTextIngestRequest;
import com.saasbasics.platform.modules.ai.application.service.AgentOrchestrationService;
import com.saasbasics.platform.modules.ai.application.service.AiChatGatewayService;
import com.saasbasics.platform.modules.ai.application.service.AiModelManageService;
import com.saasbasics.platform.modules.ai.application.service.KnowledgeRagService;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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
class AgentOrchestrationAndMultiModelIntegrationTest {

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

    @Autowired private AiModelManageService modelManageService;
    @Autowired private AiChatGatewayService chatGatewayService;
    @Autowired private KnowledgeRagService ragService;
    @Autowired private AgentOrchestrationService orchestrationService;

    @AfterEach
    void cleanup() {
        AuthContext.clear();
        TenantAccessContextHolder.clear();
    }

    @Test
    void supportsAllMajorModelProtocolsFallbackAndMultiNodeDagExecution() throws Exception {
        AtomicInteger flakyCalls = new AtomicInteger();
        HttpServer mockServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // Anthropic native /v1/messages
        mockServer.createContext("/anthropic/v1/messages", ex -> {
            assertThat(ex.getRequestHeaders().getFirst("x-api-key")).isEqualTo("ant-key-123");
            assertThat(ex.getRequestHeaders().getFirst("anthropic-version")).isEqualTo("2023-06-01");
            writeJson(ex, 200, """
                    {"id":"msg_01","content":[{"type":"text","text":"[CLAUDE_OK] Reviewed contract clauses"}],"usage":{"input_tokens":18,"output_tokens":9}}
                    """);
        });
        mockServer.createContext("/anthropic/v1/models", ex -> writeJson(ex, 200, """
                {"data":[{"id":"claude-sonnet-4-20250514","display_name":"Claude Sonnet 4"}]}
                """));
        // Gemini native /v1beta/models/...:generateContent
        mockServer.createContext("/gemini/v1beta/models/test-gemini-2.5-flash:generateContent", ex -> {
            assertThat(ex.getRequestHeaders().getFirst("x-goog-api-key")).isEqualTo("gem-key-456");
            writeJson(ex, 200, """
                    {"candidates":[{"content":{"parts":[{"text":"[GEMINI_OK] Risk check complete"}]}}],"usageMetadata":{"promptTokenCount":14,"candidatesTokenCount":7}}
                    """);
        });
        // Azure OpenAI /openai/deployments/.../chat/completions
        mockServer.createContext("/azure/openai/deployments/test-az-gpt4o/chat/completions", ex -> {
            assertThat(ex.getRequestHeaders().getFirst("api-key")).isEqualTo("az-key-789");
            writeJson(ex, 200, """
                    {"id":"az_01","choices":[{"message":{"content":"[AZURE_OK] Deployed response"}}],"usage":{"prompt_tokens":12,"completion_tokens":6}}
                    """);
        });
        // Ollama keyless native /api/chat & /api/tags
        mockServer.createContext("/ollama/api/chat", ex -> writeJson(ex, 200, """
                {"message":{"role":"assistant","content":"[OLLAMA_OK] Local inference"},"prompt_eval_count":10,"eval_count":5}
                """));
        mockServer.createContext("/ollama/api/tags", ex -> writeJson(ex, 200, """
                {"models":[{"name":"llama3.1:8b"}]}
                """));
        // Flaky primary OpenAI endpoint to verify automatic fallback failover
        mockServer.createContext("/flaky/v1/chat/completions", ex -> {
            flakyCalls.incrementAndGet();
            writeJson(ex, 503, "{\"error\":\"upstream overloaded\"}");
        });
        // Webhook/HTTP tool node endpoint
        mockServer.createContext("/hook/ping", ex -> writeJson(ex, 200, "{\"ok\":true,\"node\":\"http\"}"));
        mockServer.start();

        String baseUrl = "http://127.0.0.1:" + mockServer.getAddress().getPort();
        var principal = new AuthPrincipal(1L, 1L, "platform", 1L, "admin", "Admin",
                "PLATFORM", "ONLINE", null, List.of("*"), List.of(), List.of(1L), List.of());
        AuthContext.set(principal);
        try (var ignored = TenantAccessContextHolder.openAuthenticated(principal)) {
            // 1. Verify vendor presets & register multi-protocol providers + models
            assertThat(modelManageService.listVendorPresets()).hasSizeGreaterThanOrEqualTo(14);

            Long antProvId = saveProvider("ant_test", "Anthropic Test", "ANTHROPIC", baseUrl + "/anthropic/v1", "ant-key-123");
            Long gemProvId = saveProvider("gem_test", "Gemini Test", "GEMINI", baseUrl + "/gemini/v1beta", "gem-key-456");
            Long azProvId = saveProvider("az_test", "Azure Test", "AZURE_OPENAI", baseUrl + "/azure", "az-key-789");
            Long ollProvId = saveProvider("oll_test", "Ollama Test", "OLLAMA_NATIVE", baseUrl + "/ollama", "");
            Long flakyProvId = saveProvider("flaky_test", "Flaky OpenAI", "OPENAI_COMPATIBLE", baseUrl + "/flaky/v1", "sk-flaky");

            saveModel(antProvId, "test-claude-4", "Claude Test");
            saveModel(gemProvId, "test-gemini-2.5-flash", "Gemini Test");
            saveModel(azProvId, "test-az-gpt4o", "Azure Test");
            saveModel(ollProvId, "test-llama3.1:8b", "Ollama Local Test");
            saveModel(flakyProvId, "flaky-primary", "Flaky Model");

            // 2. Protocol-aware connection tests & model discovery
            assertThat(modelManageService.testConnection(req(antProvId, "test-claude-4")).isSuccess()).isTrue();
            assertThat(modelManageService.testConnection(req(gemProvId, "test-gemini-2.5-flash")).isSuccess()).isTrue();
            assertThat(modelManageService.testConnection(req(azProvId, "test-az-gpt4o")).isSuccess()).isTrue();
            assertThat(modelManageService.testConnection(req(ollProvId, "test-llama3.1:8b")).isSuccess()).isTrue();
            assertThat(modelManageService.discoverModels(ollProvId)).extracting("modelId").contains("llama3.1:8b");
            assertThat(modelManageService.discoverModels(antProvId)).extracting("modelId").contains("claude-sonnet-4-20250514");

            // 3. Reject cyclic DAG cleanly
            WorkflowGraph cyclic = new WorkflowGraph(
                    List.of(
                            new WorkflowNode("a", "START", "A", 0, 0, Map.of()),
                            new WorkflowNode("b", "END", "B", 100, 0, Map.of())
                    ),
                    List.of(
                            new WorkflowEdge("e1", "a", "b", null, null),
                            new WorkflowEdge("e2", "b", "a", null, null)
                    )
            );
            assertThatThrownBy(() -> orchestrationService.validateGraph(cyclic))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("环路");

            // 4. Create KB for RAG node and run full multi-protocol + fallback + parallel + loop + condition + HTTP DAG
            var kb = ragService.saveKnowledgeBase(new KnowledgeBaseSaveRequest(
                    null, "kb_wf_test", "Workflow RAG KB", "GENERAL", "BGE", 256, 32, null));
            ragService.ingestText(new KnowledgeTextIngestRequest(
                    kb.id(), "Contract Rule", "Minimum down payment is 30 percent and acceptance within 45 days.", "TEXT"));

            WorkflowGraph fullGraph = new WorkflowGraph(
                    List.of(
                            new WorkflowNode("start_1", "START", "输入", 40, 140, Map.of()),
                            new WorkflowNode("cond_1", "CONDITION", "条件路由", 220, 140, Map.of(
                                    "variable", "input.bizType",
                                    "branches", List.of(
                                            Map.of("id", "branch_contract", "label", "合同", "operator", "EQUALS", "value", "CONTRACT"),
                                            Map.of("id", "default", "label", "默认", "operator", "DEFAULT", "value", "")
                                    )
                            )),
                            new WorkflowNode("rag_1", "KNOWLEDGE_RAG", "知识检索", 420, 80, Map.of(
                                    "kbIds", List.of(kb.id()), "queryTemplate", "{{input.query}}", "topK", 2)),
                            new WorkflowNode("skip_tpl", "CODE_TEMPLATE", "未选中分支", 420, 240, Map.of("template", "SKIPPED")),
                            new WorkflowNode("par_1", "PARALLEL", "并行分发", 620, 80, Map.of()),
                            new WorkflowNode("llm_ant", "LLM", "Claude 审校", 820, 40, Map.of(
                                    "modelId", "test-claude-4", "userPromptTemplate", "Review: {{rag_1.context}}")),
                            new WorkflowNode("llm_fb", "LLM", "降级路由测试", 820, 160, Map.of(
                                    "modelId", "flaky-primary",
                                    "fallbackModelIds", List.of("test-gemini-2.5-flash"),
                                    "userPromptTemplate", "Fallback check: {{input.query}}")),
                            new WorkflowNode("llm_ol", "LLM", "Ollama 本地", 1020, 80, Map.of(
                                    "modelId", "test-llama3.1:8b", "userPromptTemplate", "{{llm_ant.text}} | {{llm_fb.text}}")),
                            new WorkflowNode("loop_1", "LOOP", "循环摘要", 1200, 80, Map.of(
                                    "listVariable", "input.items", "itemTemplate", "[{{index}}]={{item}}")),
                            new WorkflowNode("http_1", "HTTP_REQUEST", "外部回调", 1380, 80, Map.of(
                                    "method", "GET", "url", baseUrl + "/hook/ping")),
                            new WorkflowNode("end_1", "END", "输出", 1560, 80, Map.of(
                                    "outputTemplate", "{{llm_ol.text}} || {{loop_1.text}} || {{http_1.statusCode}}"))
                    ),
                    List.of(
                            new WorkflowEdge("e1", "start_1", "cond_1", null, null),
                            new WorkflowEdge("e2", "cond_1", "rag_1", "branch_contract", "合同"),
                            new WorkflowEdge("e3", "cond_1", "skip_tpl", "default", "默认"),
                            new WorkflowEdge("e4", "rag_1", "par_1", null, null),
                            new WorkflowEdge("e5", "par_1", "llm_ant", null, null),
                            new WorkflowEdge("e6", "par_1", "llm_fb", null, null),
                            new WorkflowEdge("e7", "llm_ant", "llm_ol", null, null),
                            new WorkflowEdge("e8", "llm_fb", "llm_ol", null, null),
                            new WorkflowEdge("e9", "llm_ol", "loop_1", null, null),
                            new WorkflowEdge("e10", "loop_1", "http_1", null, null),
                            new WorkflowEdge("e11", "http_1", "end_1", null, null)
                    )
            );

            var savedWf = orchestrationService.saveWorkflow(new WorkflowSaveRequest(
                    null, "wf_all_models_test", "Full Protocol DAG Test", null, "MANUAL",
                    fullGraph, List.of(), "test-claude-4", List.of("test-gemini-2.5-flash"), "DRAFT", "integration test"
            ));
            var published = orchestrationService.publishWorkflow(savedWf.id());
            assertThat(published.status()).isEqualTo("PUBLISHED");
            assertThat(published.publishedVersion()).isEqualTo(1);

            var run = orchestrationService.executeWorkflow(published.id(), new WorkflowExecuteRequest(
                    Map.of("bizType", "CONTRACT", "query", "down payment 30", "items", List.of("A", "B")),
                    null, "API_TEST", false
            ));
            assertThat(run.status()).isEqualTo("SUCCEEDED");
            assertThat(run.totalTokens()).isGreaterThan(0);
            assertThat(flakyCalls.get()).isEqualTo(1);
            assertThat(run.nodeTraces()).extracting("nodeId").doesNotContain("skip_tpl");
            assertThat(run.nodeTraces().stream().filter(t -> "llm_fb".equals(t.nodeId())).findFirst())
                    .get()
                    .satisfies(tr -> {
                        assertThat(tr.fallbackUsed()).isTrue();
                        assertThat(tr.modelId()).isEqualTo("test-gemini-2.5-flash");
                    });
            assertThat(String.valueOf(run.outputs().get("result")))
                    .contains("[OLLAMA_OK]")
                    .contains("[0]=A")
                    .contains("200");

            // Verify persisted run history & detailed node trace
            assertThat(orchestrationService.getRunDetail(run.runNo()).nodeTraces()).hasSize(10);
            assertThat(chatGatewayService.getModels()).isNotEmpty();
        } finally {
            mockServer.stop(0);
        }
    }

    private Long saveProvider(String code, String name, String type, String url, String key) {
        AiProviderSaveRequest req = new AiProviderSaveRequest();
        req.setProviderCode(code);
        req.setProviderName(name);
        req.setProviderType(type);
        req.setBaseUrl(url);
        req.setApiKey(key);
        req.setStatus("ENABLED");
        return modelManageService.saveProvider(req);
    }

    private void saveModel(Long providerId, String modelId, String modelName) {
        AiModelConfigSaveRequest req = new AiModelConfigSaveRequest();
        req.setProviderId(providerId);
        req.setModelId(modelId);
        req.setModelName(modelName);
        req.setCategory("CHAT");
        req.setContextWindow("128k");
        req.setMaxTokens(2048);
        req.setTemperature(new BigDecimal("0.3"));
        req.setStatus("ENABLED");
        modelManageService.saveModel(req);
    }

    private AiTestConnectionRequest req(Long providerId, String modelId) {
        AiTestConnectionRequest r = new AiTestConnectionRequest();
        r.setProviderId(providerId);
        r.setTestModel(modelId);
        return r;
    }

    private static void writeJson(com.sun.net.httpserver.HttpExchange ex, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
