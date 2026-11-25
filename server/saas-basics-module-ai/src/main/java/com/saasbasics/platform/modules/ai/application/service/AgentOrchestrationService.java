package com.saasbasics.platform.modules.ai.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.modules.audit.api.AuditRecorder;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.id.UuidV7Generator;
import com.saasbasics.platform.common.security.SsrfProtectionValidator;
import com.saasbasics.platform.common.tenant.TenantAccessContext;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.modules.ai.api.AiGatewayFacade.ChatMessage;
import com.saasbasics.platform.modules.ai.api.AiToolExecutionResult;
import com.saasbasics.platform.modules.ai.application.dto.AgentWorkflowDtos.NodeTraceDTO;
import com.saasbasics.platform.modules.ai.application.dto.AgentWorkflowDtos.WorkflowDTO;
import com.saasbasics.platform.modules.ai.application.dto.AgentWorkflowDtos.WorkflowEdge;
import com.saasbasics.platform.modules.ai.application.dto.AgentWorkflowDtos.WorkflowExecuteRequest;
import com.saasbasics.platform.modules.ai.application.dto.AgentWorkflowDtos.WorkflowGraph;
import com.saasbasics.platform.modules.ai.application.dto.AgentWorkflowDtos.WorkflowNode;
import com.saasbasics.platform.modules.ai.application.dto.AgentWorkflowDtos.WorkflowRunDTO;
import com.saasbasics.platform.modules.ai.application.dto.AgentWorkflowDtos.WorkflowSaveRequest;
import com.saasbasics.platform.modules.ai.application.dto.AgentWorkflowDtos.WorkflowVariableSpec;
import com.saasbasics.platform.modules.ai.application.dto.AiCommandDtos.Nl2SqlQueryRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.AgentChatRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.KnowledgeCitationDTO;
import com.saasbasics.platform.modules.ai.application.service.AiChatGatewayService.FallbackCompletionResult;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiAgentWorkflowEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiAgentWorkflowNodeTraceEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiAgentWorkflowRunEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiToolEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.AiAgentWorkflowMapper;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.AiAgentWorkflowNodeTraceMapper;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.AiAgentWorkflowRunMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Multi-Model Visual DAG Agent Orchestration Engine.
 * Supports START, LLM (with multi-model fallback chain), CONDITION, PARALLEL, LOOP,
 * KNOWLEDGE_RAG, TOOL_MCP, SUB_AGENT, HTTP_REQUEST (SSRF-checked), CODE_TEMPLATE, and END.
 */
@Service
public class AgentOrchestrationService {

    private static final int MAX_NODES = 60;
    private static final int MAX_LOOP_ITERATIONS = 50;
    private static final Pattern TEMPLATE_VAR = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*}}");

    private final AiAgentWorkflowMapper workflowMapper;
    private final AiAgentWorkflowRunMapper runMapper;
    private final AiAgentWorkflowNodeTraceMapper nodeTraceMapper;
    private final AiChatGatewayService chatGatewayService;
    private final KnowledgeRagService knowledgeRagService;
    private final Nl2SqlService nl2SqlService;
    private final AiMcpGovernanceService mcpGovernanceService;
    private final AgentStudioService agentStudioService;
    private final ObjectProvider<AuditRecorder> auditRecorderProvider;
    private final ObjectProvider<Environment> environmentProvider;
    private final ObjectMapper objectMapper;
    private final UuidV7Generator uuidGenerator = new UuidV7Generator();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final ExecutorService parallelPool = Executors.newVirtualThreadPerTaskExecutor();

    public AgentOrchestrationService(
            AiAgentWorkflowMapper workflowMapper,
            AiAgentWorkflowRunMapper runMapper,
            AiAgentWorkflowNodeTraceMapper nodeTraceMapper,
            AiChatGatewayService chatGatewayService,
            KnowledgeRagService knowledgeRagService,
            Nl2SqlService nl2SqlService,
            AiMcpGovernanceService mcpGovernanceService,
            AgentStudioService agentStudioService,
            ObjectProvider<AuditRecorder> auditRecorderProvider,
            ObjectProvider<Environment> environmentProvider,
            ObjectMapper objectMapper
    ) {
        this.workflowMapper = workflowMapper;
        this.runMapper = runMapper;
        this.nodeTraceMapper = nodeTraceMapper;
        this.chatGatewayService = chatGatewayService;
        this.knowledgeRagService = knowledgeRagService;
        this.nl2SqlService = nl2SqlService;
        this.mcpGovernanceService = mcpGovernanceService;
        this.agentStudioService = agentStudioService;
        this.auditRecorderProvider = auditRecorderProvider;
        this.environmentProvider = environmentProvider;
        this.objectMapper = objectMapper;
    }

    public List<WorkflowDTO> listWorkflows() {
        return workflowMapper.selectList(new LambdaQueryWrapper<AiAgentWorkflowEntity>()
                        .eq(AiAgentWorkflowEntity::getTenantId, currentTenantId())
                        .eq(AiAgentWorkflowEntity::getDeleted, 0)
                        .orderByDesc(AiAgentWorkflowEntity::getId))
                .stream()
                .map(this::toWorkflowDto)
                .toList();
    }

    public WorkflowDTO getWorkflow(Long id) {
        return toWorkflowDto(requireWorkflow(id));
    }

    @Transactional
    public WorkflowDTO saveWorkflow(WorkflowSaveRequest request) {
        if (request == null || request.workflowName() == null || request.workflowName().isBlank()) {
            throw new BizException("WF_INVALID_NAME", "编排工作流名称不能为空");
        }
        WorkflowGraph graph = request.graph();
        validateGraph(graph);

        Long tenantId = currentTenantId();
        AiAgentWorkflowEntity entity = request.id() != null
                ? workflowMapper.selectOne(new LambdaQueryWrapper<AiAgentWorkflowEntity>()
                        .eq(AiAgentWorkflowEntity::getId, request.id())
                        .eq(AiAgentWorkflowEntity::getTenantId, tenantId)
                        .eq(AiAgentWorkflowEntity::getDeleted, 0))
                : null;

        boolean created = entity == null;
        if (entity == null) {
            entity = new AiAgentWorkflowEntity();
            entity.setTenantId(tenantId);
            entity.setPublicId(uuidGenerator.generate().toString());
            entity.setWorkflowCode(request.workflowCode() != null && !request.workflowCode().isBlank()
                    ? request.workflowCode().trim()
                    : "wf_" + uuidGenerator.generate().toString().substring(0, 8));
            entity.setPublishedVersion(0);
            entity.setRunCount(0);
        }
        entity.setWorkflowName(request.workflowName().trim());
        entity.setAgentId(request.agentId());
        entity.setTriggerMode(request.triggerMode() != null && !request.triggerMode().isBlank()
                ? request.triggerMode().trim().toUpperCase(Locale.ROOT) : "MANUAL");
        entity.setGraphJson(writeJson(graph));
        entity.setVariablesSchemaJson(request.variablesSchema() != null ? writeJson(request.variablesSchema()) : "[]");
        entity.setDefaultModelId(request.defaultModelId());
        entity.setFallbackModelIdsJson(request.fallbackModelIds() != null ? writeJson(request.fallbackModelIds()) : "[]");
        entity.setStatus(request.status() != null && !request.status().isBlank()
                ? request.status().trim().toUpperCase(Locale.ROOT) : (created ? "DRAFT" : entity.getStatus()));
        entity.setRemark(request.remark());

        if (created) {
            workflowMapper.insert(entity);
        } else {
            workflowMapper.updateById(entity);
        }
        recordAudit("SAVE", entity.getPublicId(), entity.getWorkflowName(), true);
        return toWorkflowDto(entity);
    }

    @Transactional
    public WorkflowDTO publishWorkflow(Long id) {
        AiAgentWorkflowEntity entity = requireWorkflow(id);
        validateGraph(parseGraph(entity.getGraphJson()));
        entity.setStatus("PUBLISHED");
        entity.setPublishedVersion((entity.getPublishedVersion() != null ? entity.getPublishedVersion() : 0) + 1);
        workflowMapper.updateById(entity);
        recordAudit("PUBLISH", entity.getPublicId(), "v" + entity.getPublishedVersion(), true);
        return toWorkflowDto(entity);
    }

    @Transactional
    public void deleteWorkflow(Long id) {
        AiAgentWorkflowEntity entity = requireWorkflow(id);
        entity.setDeleted(1);
        entity.setDeletedAt(LocalDateTime.now());
        workflowMapper.updateById(entity);
        recordAudit("DELETE", entity.getPublicId(), entity.getWorkflowName(), true);
    }

    public List<WorkflowRunDTO> listRuns(Long workflowId) {
        LambdaQueryWrapper<AiAgentWorkflowRunEntity> qw = new LambdaQueryWrapper<AiAgentWorkflowRunEntity>()
                .eq(AiAgentWorkflowRunEntity::getTenantId, currentTenantId())
                .eq(AiAgentWorkflowRunEntity::getDeleted, 0)
                .orderByDesc(AiAgentWorkflowRunEntity::getId)
                .last("LIMIT 30");
        if (workflowId != null) {
            qw.eq(AiAgentWorkflowRunEntity::getWorkflowId, workflowId);
        }
        return runMapper.selectList(qw).stream().map(r -> toRunDto(r, List.of())).toList();
    }

    public WorkflowRunDTO getRunDetail(String runNo) {
        AiAgentWorkflowRunEntity run = runMapper.selectOne(new LambdaQueryWrapper<AiAgentWorkflowRunEntity>()
                .eq(AiAgentWorkflowRunEntity::getTenantId, currentTenantId())
                .eq(AiAgentWorkflowRunEntity::getRunNo, runNo)
                .eq(AiAgentWorkflowRunEntity::getDeleted, 0)
                .last("LIMIT 1"));
        if (run == null) {
            throw new BizException("WF_RUN_NOT_FOUND", "未找到编排运行记录: " + runNo);
        }
        List<NodeTraceDTO> traces = nodeTraceMapper.selectList(new LambdaQueryWrapper<AiAgentWorkflowNodeTraceEntity>()
                        .eq(AiAgentWorkflowNodeTraceEntity::getTenantId, currentTenantId())
                        .eq(AiAgentWorkflowNodeTraceEntity::getRunId, run.getId())
                        .eq(AiAgentWorkflowNodeTraceEntity::getDeleted, 0)
                        .orderByAsc(AiAgentWorkflowNodeTraceEntity::getId))
                .stream()
                .map(this::toTraceDto)
                .toList();
        return toRunDto(run, traces);
    }

    public WorkflowRunDTO executeByCode(String workflowCode, WorkflowExecuteRequest request) {
        AiAgentWorkflowEntity entity = workflowMapper.selectOne(new LambdaQueryWrapper<AiAgentWorkflowEntity>()
                .eq(AiAgentWorkflowEntity::getTenantId, currentTenantId())
                .eq(AiAgentWorkflowEntity::getWorkflowCode, workflowCode)
                .eq(AiAgentWorkflowEntity::getDeleted, 0)
                .last("LIMIT 1"));
        if (entity == null) {
            throw new BizException("WF_NOT_FOUND", "未找到编排工作流: " + workflowCode);
        }
        return executeWorkflow(entity.getId(), request);
    }

    public WorkflowRunDTO executeWorkflow(Long workflowId, WorkflowExecuteRequest request) {
        AiAgentWorkflowEntity workflow = requireWorkflow(workflowId);
        WorkflowGraph graph = (request != null && request.overrideGraph() != null)
                ? request.overrideGraph()
                : parseGraph(workflow.getGraphJson());
        List<WorkflowNode> topoOrder = validateAndSort(graph);

        Long tenantId = currentTenantId();
        Map<String, Object> inputs = (request != null && request.inputs() != null) ? request.inputs() : Map.of();
        boolean dryRun = request != null && Boolean.TRUE.equals(request.dryRun());
        String triggerSource = (request != null && request.triggerSource() != null && !request.triggerSource().isBlank())
                ? request.triggerSource().trim().toUpperCase(Locale.ROOT) : "STUDIO";

        AiAgentWorkflowRunEntity run = new AiAgentWorkflowRunEntity();
        run.setTenantId(tenantId);
        run.setPublicId(uuidGenerator.generate().toString());
        run.setRunNo("WFR-" + uuidGenerator.generate().toString().substring(0, 12).toUpperCase(Locale.ROOT));
        run.setWorkflowId(workflow.getId());
        run.setWorkflowCode(workflow.getWorkflowCode());
        run.setTriggerSource(triggerSource);
        run.setInputJson(writeJson(inputs));
        run.setStatus("RUNNING");
        run.setTotalTokens(0);
        run.setTotalLatencyMs(0L);
        run.setExecutedNodes(0);
        run.setStartedAt(LocalDateTime.now());
        runMapper.insert(run);

        long startMs = System.currentTimeMillis();
        Map<String, Object> ctx = new ConcurrentHashMap<>();
        ctx.put("input", new LinkedHashMap<>(inputs));

        Map<String, List<WorkflowEdge>> outgoingMap = new HashMap<>();
        if (graph.edges() != null) {
            for (WorkflowEdge e : graph.edges()) {
                outgoingMap.computeIfAbsent(e.source(), k -> new ArrayList<>()).add(e);
            }
        }
        Map<String, WorkflowNode> nodeById = new LinkedHashMap<>();
        for (WorkflowNode n : topoOrder) {
            nodeById.put(n.id(), n);
        }

        Set<String> activeNodes = ConcurrentHashMap.newKeySet();
        topoOrder.stream().filter(n -> "START".equalsIgnoreCase(n.type())).findFirst()
                .ifPresent(n -> activeNodes.add(n.id()));
        if (activeNodes.isEmpty() && !topoOrder.isEmpty()) {
            activeNodes.add(topoOrder.get(0).id());
        }

        Set<String> executedIds = ConcurrentHashMap.newKeySet();
        List<NodeTraceDTO> collectedTraces = Collections.synchronizedList(new ArrayList<>());
        Map<String, Object> finalOutputs = new LinkedHashMap<>();
        List<String> defaultFallbacks = parseStringList(workflow.getFallbackModelIdsJson());

        try {
            for (WorkflowNode node : topoOrder) {
                if (executedIds.contains(node.id())) {
                    continue;
                }
                if (!activeNodes.contains(node.id())) {
                    continue;
                }

                NodeOutcome outcome = runSingleNode(
                        run.getId(), tenantId, node, ctx, outgoingMap.getOrDefault(node.id(), List.of()),
                        workflow.getDefaultModelId(), defaultFallbacks, dryRun
                );
                executedIds.add(node.id());
                collectedTraces.add(outcome.trace());

                if ("END".equalsIgnoreCase(node.type())) {
                    finalOutputs.putAll(outcome.outputs());
                } else {
                    for (String nextId : outcome.activatedTargets()) {
                        activeNodes.add(nextId);
                    }
                }

                // True concurrent execution when PARALLEL activates multiple sibling nodes whose deps are met
                if ("PARALLEL".equalsIgnoreCase(node.type()) && outcome.activatedTargets().size() > 1) {
                    executeParallelChildren(
                            run.getId(), tenantId, outcome.activatedTargets(), nodeById, outgoingMap,
                            ctx, activeNodes, executedIds, collectedTraces,
                            workflow.getDefaultModelId(), defaultFallbacks, dryRun
                    );
                }
            }

            if (finalOutputs.isEmpty() && !collectedTraces.isEmpty()) {
                NodeTraceDTO last = collectedTraces.get(collectedTraces.size() - 1);
                if (last.outputSnapshot() != null) {
                    finalOutputs.putAll(last.outputSnapshot());
                }
            }

            int totalTokens = collectedTraces.stream()
                    .mapToInt(t -> (t.promptTokens() != null ? t.promptTokens() : 0)
                            + (t.completionTokens() != null ? t.completionTokens() : 0))
                    .sum();
            long elapsed = Math.max(1L, System.currentTimeMillis() - startMs);

            run.setOutputJson(writeJson(finalOutputs));
            run.setContextVarsJson(writeJson(ctx));
            run.setStatus("SUCCEEDED");
            run.setTotalTokens(totalTokens);
            run.setTotalLatencyMs(elapsed);
            run.setExecutedNodes(collectedTraces.size());
            run.setFinishedAt(LocalDateTime.now());
            runMapper.updateById(run);

            workflow.setRunCount((workflow.getRunCount() != null ? workflow.getRunCount() : 0) + 1);
            workflowMapper.updateById(workflow);
            recordAudit("EXECUTE", workflow.getPublicId(), run.getRunNo() + " SUCCEEDED", true);

            return toRunDto(run, collectedTraces);
        } catch (Exception ex) {
            long elapsed = Math.max(1L, System.currentTimeMillis() - startMs);
            run.setStatus("FAILED");
            run.setErrorMessage(truncate(ex.getMessage(), 900));
            run.setTotalLatencyMs(elapsed);
            run.setExecutedNodes(collectedTraces.size());
            run.setFinishedAt(LocalDateTime.now());
            runMapper.updateById(run);
            recordAudit("EXECUTE", workflow.getPublicId(), run.getRunNo() + " FAILED: " + ex.getMessage(), false);
            return toRunDto(run, collectedTraces);
        }
    }

    private void executeParallelChildren(
            Long runId,
            Long tenantId,
            List<String> targetIds,
            Map<String, WorkflowNode> nodeById,
            Map<String, List<WorkflowEdge>> outgoingMap,
            Map<String, Object> ctx,
            Set<String> activeNodes,
            Set<String> executedIds,
            List<NodeTraceDTO> collectedTraces,
            String defaultModelId,
            List<String> defaultFallbacks,
            boolean dryRun
    ) {
        AuthPrincipal principal = AuthContext.get();
        TenantAccessContext tenantCtx = TenantAccessContextHolder.current();
        List<CompletableFuture<NodeOutcome>> futures = new ArrayList<>();
        for (String targetId : targetIds) {
            WorkflowNode child = nodeById.get(targetId);
            if (child == null || "END".equalsIgnoreCase(child.type()) || executedIds.contains(targetId)) {
                continue;
            }
            futures.add(CompletableFuture.supplyAsync(() -> {
                if (principal != null) {
                    AuthContext.set(principal);
                }
                TenantAccessContextHolder.Scope scope = tenantCtx != null
                        ? TenantAccessContextHolder.openTenant(tenantId, tenantCtx.tenantCode(), tenantCtx.actorId())
                        : TenantAccessContextHolder.openTenant(tenantId, "tenant_" + tenantId, null);
                try (scope) {
                    return runSingleNode(
                            runId, tenantId, child, ctx, outgoingMap.getOrDefault(child.id(), List.of()),
                            defaultModelId, defaultFallbacks, dryRun
                    );
                } finally {
                    AuthContext.clear();
                }
            }, parallelPool));
        }

        for (CompletableFuture<NodeOutcome> f : futures) {
            NodeOutcome childOutcome = f.join();
            executedIds.add(childOutcome.trace().nodeId());
            collectedTraces.add(childOutcome.trace());
            activeNodes.addAll(childOutcome.activatedTargets());
        }
    }

    private record NodeOutcome(
            NodeTraceDTO trace,
            Map<String, Object> outputs,
            List<String> activatedTargets
    ) {}

    private NodeOutcome runSingleNode(
            Long runId,
            Long tenantId,
            WorkflowNode node,
            Map<String, Object> ctx,
            List<WorkflowEdge> outgoingEdges,
            String defaultModelId,
            List<String> defaultFallbacks,
            boolean dryRun
    ) {
        LocalDateTime started = LocalDateTime.now();
        long startMs = System.currentTimeMillis();
        Map<String, Object> cfg = node.config() != null ? node.config() : Map.of();
        String type = node.type() != null ? node.type().trim().toUpperCase(Locale.ROOT) : "LLM";

        Map<String, Object> inputSnapshot = new LinkedHashMap<>(cfg);
        Map<String, Object> outputs = new LinkedHashMap<>();
        List<String> nextTargets = new ArrayList<>();
        String usedModelId = null;
        String providerCode = null;
        int attempts = 1;
        boolean fallbackUsed = false;
        int pTokens = 0;
        int cTokens = 0;

        try {
            switch (type) {
                case "START" -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> inputMap = (Map<String, Object>) ctx.getOrDefault("input", Map.of());
                    outputs.putAll(inputMap);
                    outgoingEdges.forEach(e -> nextTargets.add(e.target()));
                }
                case "LLM" -> {
                    String primaryModel = str(cfg.get("modelId"));
                    if (primaryModel == null || primaryModel.isBlank()) {
                        primaryModel = defaultModelId != null && !defaultModelId.isBlank() ? defaultModelId : "deepseek-chat";
                    }
                    List<String> chain = new ArrayList<>();
                    chain.add(primaryModel);
                    chain.addAll(toStringList(cfg.get("fallbackModelIds")));
                    chain.addAll(defaultFallbacks);

                    String sysPrompt = renderTemplate(str(cfg.get("systemPrompt")), ctx);
                    String userPrompt = renderTemplate(str(cfg.get("userPromptTemplate")), ctx);
                    if (userPrompt.isBlank()) {
                        userPrompt = renderTemplate("{{input.query}}", ctx);
                    }
                    Double temp = cfg.get("temperature") instanceof Number n ? n.doubleValue() : 0.3;
                    inputSnapshot.put("renderedPrompt", userPrompt);
                    inputSnapshot.put("modelChain", chain);

                    if (dryRun) {
                        usedModelId = primaryModel;
                        providerCode = "DRY_RUN";
                        outputs.put("text", "[DRY-RUN " + primaryModel + "] " + userPrompt);
                    } else {
                        List<ChatMessage> msgs = new ArrayList<>();
                        if (!sysPrompt.isBlank()) {
                            msgs.add(new ChatMessage("system", sysPrompt));
                        }
                        msgs.add(new ChatMessage("user", userPrompt));
                        FallbackCompletionResult res = chatGatewayService.completeWithFallback(chain, null, msgs, temp);
                        usedModelId = res.usedModelId();
                        providerCode = res.providerCode();
                        fallbackUsed = res.fallbackUsed();
                        attempts = res.attempts();
                        pTokens = res.output().promptTokens();
                        cTokens = res.output().completionTokens();
                        outputs.put("text", res.output().reply());
                        outputs.put("modelId", usedModelId);
                    }
                    outgoingEdges.forEach(e -> nextTargets.add(e.target()));
                }
                case "CONDITION" -> {
                    String varPath = str(cfg.get("variable"));
                    String actualVal = String.valueOf(readVar(ctx, varPath) == null ? "" : readVar(ctx, varPath));
                    String matchedHandle = "default";
                    if (cfg.get("branches") instanceof List<?> branches) {
                        for (Object bObj : branches) {
                            if (!(bObj instanceof Map<?, ?> bm)) continue;
                            String branchId = str(bm.get("id"));
                            String op = str(bm.get("operator"));
                            String expected = str(bm.get("value"));
                            if ("DEFAULT".equalsIgnoreCase(op)) {
                                continue;
                            }
                            if (evaluateCondition(actualVal, op, expected)) {
                                matchedHandle = branchId;
                                break;
                            }
                        }
                    }
                    outputs.put("matchedBranch", matchedHandle);
                    outputs.put("evaluatedValue", actualVal);
                    for (WorkflowEdge e : outgoingEdges) {
                        if (matchedHandle.equalsIgnoreCase(e.sourceHandle())
                                || (e.sourceHandle() == null && "default".equalsIgnoreCase(matchedHandle))) {
                            nextTargets.add(e.target());
                        }
                    }
                    if (nextTargets.isEmpty() && !outgoingEdges.isEmpty()) {
                        for (WorkflowEdge e : outgoingEdges) {
                            if ("default".equalsIgnoreCase(e.sourceHandle())) {
                                nextTargets.add(e.target());
                            }
                        }
                    }
                }
                case "KNOWLEDGE_RAG" -> {
                    List<Long> kbIds = toLongList(cfg.get("kbIds"));
                    String query = renderTemplate(str(cfg.get("queryTemplate")), ctx);
                    int topK = cfg.get("topK") instanceof Number n ? Math.min(10, Math.max(1, n.intValue())) : 3;
                    List<KnowledgeCitationDTO> citations = knowledgeRagService.hybridSearch(kbIds, query, topK);
                    StringBuilder sb = new StringBuilder();
                    for (KnowledgeCitationDTO c : citations) {
                        sb.append("[").append(c.citationNo()).append("] 《").append(c.docName())
                                .append("》: ").append(c.excerpt()).append("\n");
                    }
                    outputs.put("context", sb.toString().trim());
                    outputs.put("citations", citations);
                    outputs.put("hitCount", citations.size());
                    outgoingEdges.forEach(e -> nextTargets.add(e.target()));
                }
                case "TOOL_MCP" -> {
                    String toolCode = str(cfg.get("toolCode"));
                    String queryArg = renderTemplate(str(cfg.get("queryTemplate")), ctx);
                    if ("nl2sql_query".equalsIgnoreCase(toolCode)) {
                        var sqlRes = nl2SqlService.executeNaturalQuery(new Nl2SqlQueryRequest(queryArg, defaultModelId, 10));
                        outputs.put("output", "SQL=" + sqlRes.sql() + " rows=" + sqlRes.rowCount());
                        outputs.put("rows", sqlRes.rows());
                    } else if ("knowledge_search".equalsIgnoreCase(toolCode)) {
                        var cits = knowledgeRagService.hybridSearch(List.of(), queryArg, 3);
                        outputs.put("output", cits);
                    } else {
                        AiToolEntity toolEntity = mcpGovernanceService.findToolByCode(toolCode);
                        if (toolEntity != null) {
                            AuthPrincipal p = AuthContext.get();
                            AiToolExecutionResult execRes = mcpGovernanceService.executeAuthorizedTool(
                                    toolEntity, tenantId, p != null ? p.userId() : 1L, Map.of("query", queryArg));
                            outputs.put("output", execRes.summary());
                            outputs.put("payload", parseMap(execRes.payloadJson()));
                            outputs.put("success", execRes.success());
                        } else {
                            outputs.put("output", "Tool [" + toolCode + "] executed with arg: " + queryArg);
                        }
                    }
                    outgoingEdges.forEach(e -> nextTargets.add(e.target()));
                }
                case "SUB_AGENT" -> {
                    Long targetAgentId = cfg.get("agentId") instanceof Number n ? n.longValue() : null;
                    String msg = renderTemplate(str(cfg.get("messageTemplate")), ctx);
                    if (targetAgentId != null && !dryRun) {
                        var resp = agentStudioService.chatWithAgent(new AgentChatRequest(targetAgentId, null, msg));
                        outputs.put("text", resp.reply());
                        outputs.put("agentName", resp.agentName());
                    } else {
                        outputs.put("text", "[SUB_AGENT " + str(cfg.get("agentId")) + "] " + msg);
                    }
                    outgoingEdges.forEach(e -> nextTargets.add(e.target()));
                }
                case "HTTP_REQUEST" -> {
                    String rawUrl = renderTemplate(str(cfg.get("url")), ctx);
                    String method = cfg.get("method") != null ? str(cfg.get("method")).toUpperCase(Locale.ROOT) : "GET";
                    String body = renderTemplate(str(cfg.get("bodyTemplate")), ctx);
                    Environment env = environmentProvider.getIfAvailable();
                    boolean allowPrivate = env == null || !env.matchesProfiles("prod");
                    URI uri = SsrfProtectionValidator.validateOutboundUri(rawUrl, allowPrivate);
                    if (dryRun) {
                        outputs.put("statusCode", 200);
                        outputs.put("body", "[DRY-RUN HTTP " + method + "] " + uri);
                    } else {
                        try {
                            HttpRequest.Builder rb = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
                                    .header("Content-Type", "application/json");
                            if ("POST".equals(method) || "PUT".equals(method)) {
                                rb.method(method, HttpRequest.BodyPublishers.ofString(body));
                            } else {
                                rb.GET();
                            }
                            HttpResponse<String> httpResp = httpClient.send(rb.build(), HttpResponse.BodyHandlers.ofString());
                            outputs.put("statusCode", httpResp.statusCode());
                            outputs.put("body", httpResp.body());
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new BizException("WF_HTTP_INTERRUPTED", "HTTP 请求节点被中断");
                        } catch (Exception ex) {
                            throw new BizException("WF_HTTP_ERROR", "HTTP 请求节点失败: " + ex.getMessage());
                        }
                    }
                    outgoingEdges.forEach(e -> nextTargets.add(e.target()));
                }
                case "LOOP" -> {
                    String listVar = str(cfg.get("listVariable"));
                    Object rawItems = readVar(ctx, listVar);
                    List<?> items = rawItems instanceof Collection<?> col ? new ArrayList<>(col) : List.of();
                    int limit = Math.min(MAX_LOOP_ITERATIONS, items.size());
                    String itemTemplate = str(cfg.get("itemTemplate"));
                    List<String> results = new ArrayList<>();
                    for (int i = 0; i < limit; i++) {
                        ctx.put("item", items.get(i));
                        ctx.put("index", i);
                        results.add(renderTemplate(itemTemplate, ctx));
                    }
                    ctx.remove("item");
                    ctx.remove("index");
                    outputs.put("items", results);
                    outputs.put("text", String.join("\n", results));
                    outputs.put("count", results.size());
                    outgoingEdges.forEach(e -> nextTargets.add(e.target()));
                }
                case "PARALLEL" -> {
                    outputs.put("dispatchedCount", outgoingEdges.size());
                    outgoingEdges.forEach(e -> nextTargets.add(e.target()));
                }
                case "CODE_TEMPLATE" -> {
                    String rendered = renderTemplate(str(cfg.get("template")), ctx);
                    outputs.put("text", rendered);
                    outgoingEdges.forEach(e -> nextTargets.add(e.target()));
                }
                case "END" -> {
                    String outTpl = str(cfg.get("outputTemplate"));
                    String rendered = renderTemplate(outTpl, ctx);
                    outputs.put("result", rendered);
                }
                default -> {
                    outputs.put("text", renderTemplate(str(cfg.get("template")), ctx));
                    outgoingEdges.forEach(e -> nextTargets.add(e.target()));
                }
            }

            ctx.put(node.id(), outputs);
            long latencyMs = Math.max(1L, System.currentTimeMillis() - startMs);
            AiAgentWorkflowNodeTraceEntity traceEntity = persistTrace(
                    runId, tenantId, node, usedModelId, providerCode, attempts, fallbackUsed,
                    inputSnapshot, outputs, "SUCCEEDED", pTokens, cTokens, latencyMs, null, started);
            return new NodeOutcome(toTraceDto(traceEntity), outputs, nextTargets);
        } catch (Exception ex) {
            long latencyMs = Math.max(1L, System.currentTimeMillis() - startMs);
            persistTrace(runId, tenantId, node, usedModelId, providerCode, attempts, fallbackUsed,
                    inputSnapshot, outputs, "FAILED", pTokens, cTokens, latencyMs, ex.getMessage(), started);
            throw ex;
        }
    }

    private AiAgentWorkflowNodeTraceEntity persistTrace(
            Long runId,
            Long tenantId,
            WorkflowNode node,
            String modelId,
            String providerCode,
            int attemptNo,
            boolean fallbackUsed,
            Map<String, Object> inputSnap,
            Map<String, Object> outputSnap,
            String status,
            int pTokens,
            int cTokens,
            long latencyMs,
            String errorMsg,
            LocalDateTime started
    ) {
        AiAgentWorkflowNodeTraceEntity e = new AiAgentWorkflowNodeTraceEntity();
        e.setTenantId(tenantId);
        e.setPublicId(uuidGenerator.generate().toString());
        e.setRunId(runId);
        e.setNodeId(node.id());
        e.setNodeType(node.type());
        e.setNodeLabel(node.label() != null ? node.label() : node.type());
        e.setModelId(modelId);
        e.setProviderCode(providerCode);
        e.setAttemptNo(attemptNo);
        e.setFallbackUsed(fallbackUsed ? 1 : 0);
        e.setInputSnapshotJson(writeJson(inputSnap));
        e.setOutputSnapshotJson(writeJson(outputSnap));
        e.setStatus(status);
        e.setPromptTokens(pTokens);
        e.setCompletionTokens(cTokens);
        e.setLatencyMs(latencyMs);
        e.setErrorMessage(truncate(errorMsg, 900));
        e.setStartedAt(started);
        e.setFinishedAt(LocalDateTime.now());
        nodeTraceMapper.insert(e);
        return e;
    }

    public void validateGraph(WorkflowGraph graph) {
        validateAndSort(graph);
    }

    /**
     * Validates DAG invariants via Kahn's topological sort and rejects cyclic graphs.
     */
    private List<WorkflowNode> validateAndSort(WorkflowGraph graph) {
        if (graph == null || graph.nodes() == null || graph.nodes().isEmpty()) {
            throw new BizException("WF_EMPTY_GRAPH", "编排画布不能为空");
        }
        if (graph.nodes().size() > MAX_NODES) {
            throw new BizException("WF_TOO_MANY_NODES", "单编排流程节点数不能超过 " + MAX_NODES);
        }
        Map<String, WorkflowNode> map = new LinkedHashMap<>();
        for (WorkflowNode n : graph.nodes()) {
            if (n.id() == null || n.id().isBlank()) {
                throw new BizException("WF_INVALID_NODE", "节点 ID 不能为空");
            }
            if (map.putIfAbsent(n.id(), n) != null) {
                throw new BizException("WF_DUPLICATE_NODE", "存在重复节点 ID: " + n.id());
            }
        }
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> adj = new HashMap<>();
        map.keySet().forEach(id -> {
            inDegree.put(id, 0);
            adj.put(id, new ArrayList<>());
        });
        if (graph.edges() != null) {
            for (WorkflowEdge e : graph.edges()) {
                if (!map.containsKey(e.source()) || !map.containsKey(e.target())) {
                    throw new BizException("WF_INVALID_EDGE", "连线引用了不存在的节点: " + e.source() + " -> " + e.target());
                }
                adj.get(e.source()).add(e.target());
                inDegree.compute(e.target(), (k, v) -> v + 1);
            }
        }
        Deque<String> queue = new ArrayDeque<>();
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }
        List<WorkflowNode> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            sorted.add(map.get(cur));
            for (String nxt : adj.get(cur)) {
                int deg = inDegree.compute(nxt, (k, v) -> v - 1);
                if (deg == 0) {
                    queue.add(nxt);
                }
            }
        }
        if (sorted.size() != map.size()) {
            throw new BizException("WF_CYCLE_DETECTED", "编排图存在有向环路，请改用 LOOP 节点处理循环");
        }
        return sorted;
    }

    private String renderTemplate(String template, Map<String, Object> ctx) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        Matcher m = TEMPLATE_VAR.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            Object val = readVar(ctx, m.group(1));
            String rep = val == null ? "" : (val instanceof String s ? s : writeJson(val));
            m.appendReplacement(out, Matcher.quoteReplacement(rep));
        }
        m.appendTail(out);
        return out.toString();
    }

    private Object readVar(Map<String, Object> ctx, String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String[] parts = path.trim().split("\\.");
        Object cur = ctx;
        for (String p : parts) {
            if (cur instanceof Map<?, ?> map) {
                cur = map.get(p);
            } else {
                return null;
            }
        }
        return cur;
    }

    private boolean evaluateCondition(String actual, String operator, String expected) {
        String op = operator != null ? operator.trim().toUpperCase(Locale.ROOT) : "EQUALS";
        String exp = expected != null ? expected : "";
        return switch (op) {
            case "EQUALS" -> actual.equalsIgnoreCase(exp);
            case "NOT_EQUALS" -> !actual.equalsIgnoreCase(exp);
            case "CONTAINS" -> actual.toLowerCase(Locale.ROOT).contains(exp.toLowerCase(Locale.ROOT));
            case "NOT_EMPTY" -> !actual.isBlank();
            case "REGEX" -> {
                try {
                    yield Pattern.compile(exp).matcher(actual).find();
                } catch (Exception ex) {
                    yield false;
                }
            }
            case "GT", "LT" -> {
                try {
                    double a = Double.parseDouble(actual.trim());
                    double b = Double.parseDouble(exp.trim());
                    yield "GT".equals(op) ? a > b : a < b;
                } catch (NumberFormatException ex) {
                    yield false;
                }
            }
            default -> false;
        };
    }

    private AiAgentWorkflowEntity requireWorkflow(Long id) {
        AiAgentWorkflowEntity e = workflowMapper.selectOne(new LambdaQueryWrapper<AiAgentWorkflowEntity>()
                .eq(AiAgentWorkflowEntity::getId, id)
                .eq(AiAgentWorkflowEntity::getTenantId, currentTenantId())
                .eq(AiAgentWorkflowEntity::getDeleted, 0)
                .last("LIMIT 1"));
        if (e == null) {
            throw new BizException("WF_NOT_FOUND", "未找到编排工作流: " + id);
        }
        return e;
    }

    private WorkflowDTO toWorkflowDto(AiAgentWorkflowEntity e) {
        return new WorkflowDTO(
                e.getId(), e.getPublicId(), e.getWorkflowCode(), e.getWorkflowName(), e.getAgentId(),
                e.getTriggerMode(), parseGraph(e.getGraphJson()), parseVariables(e.getVariablesSchemaJson()),
                e.getDefaultModelId(), parseStringList(e.getFallbackModelIdsJson()), e.getStatus(),
                e.getPublishedVersion(), e.getRunCount(), e.getRemark(), e.getUpdatedAt());
    }

    private WorkflowRunDTO toRunDto(AiAgentWorkflowRunEntity r, List<NodeTraceDTO> traces) {
        return new WorkflowRunDTO(
                r.getId(), r.getRunNo(), r.getWorkflowId(), r.getWorkflowCode(), r.getTriggerSource(),
                parseMap(r.getInputJson()), parseMap(r.getOutputJson()), r.getStatus(),
                r.getTotalTokens(), r.getTotalLatencyMs(), r.getExecutedNodes(), r.getErrorMessage(),
                r.getStartedAt(), r.getFinishedAt(), traces);
    }

    private NodeTraceDTO toTraceDto(AiAgentWorkflowNodeTraceEntity t) {
        return new NodeTraceDTO(
                t.getNodeId(), t.getNodeType(), t.getNodeLabel(), t.getModelId(), t.getProviderCode(),
                t.getAttemptNo(), Integer.valueOf(1).equals(t.getFallbackUsed()),
                parseMap(t.getInputSnapshotJson()), parseMap(t.getOutputSnapshotJson()),
                t.getStatus(), t.getPromptTokens(), t.getCompletionTokens(), t.getLatencyMs(),
                t.getErrorMessage(), t.getStartedAt(), t.getFinishedAt());
    }

    private WorkflowGraph parseGraph(String json) {
        try {
            return objectMapper.readValue(json, WorkflowGraph.class);
        } catch (Exception e) {
            return new WorkflowGraph(List.of(), List.of());
        }
    }

    private List<WorkflowVariableSpec> parseVariables(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static List<String> toStringList(Object raw) {
        if (raw instanceof Collection<?> col) {
            return col.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private static List<Long> toLongList(Object raw) {
        if (raw instanceof Collection<?> col) {
            return col.stream().filter(Number.class::isInstance).map(n -> ((Number) n).longValue()).toList();
        }
        return List.of();
    }

    private static String truncate(String s, int max) {
        return s == null ? null : (s.length() <= max ? s : s.substring(0, max));
    }

    private void recordAudit(String action, String targetId, String detail, boolean success) {
        AuditRecorder rec = auditRecorderProvider.getIfAvailable();
        if (rec != null) {
            rec.record("ai", "agent_workflow", targetId, action, null, detail, success);
        }
    }

    private Long currentTenantId() {
        AuthPrincipal principal = AuthContext.get();
        return principal != null && principal.tenantId() != null ? principal.tenantId() : 1L;
    }
}
