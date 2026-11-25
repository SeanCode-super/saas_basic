package com.saasbasics.platform.modules.ai.application.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Visual Agent DAG Orchestration DTOs.
 */
public final class AgentWorkflowDtos {

    private AgentWorkflowDtos() {}

    public record WorkflowNode(
            String id,
            String type,
            String label,
            double x,
            double y,
            Map<String, Object> config
    ) {}

    public record WorkflowEdge(
            String id,
            String source,
            String target,
            String sourceHandle,
            String label
    ) {}

    public record WorkflowGraph(
            List<WorkflowNode> nodes,
            List<WorkflowEdge> edges
    ) {}

    public record WorkflowVariableSpec(
            String name,
            String type,
            boolean required,
            String defaultValue
    ) {}

    public record WorkflowSaveRequest(
            Long id,
            String workflowCode,
            String workflowName,
            Long agentId,
            String triggerMode,
            WorkflowGraph graph,
            List<WorkflowVariableSpec> variablesSchema,
            String defaultModelId,
            List<String> fallbackModelIds,
            String status,
            String remark
    ) {}

    public record WorkflowDTO(
            Long id,
            String publicId,
            String workflowCode,
            String workflowName,
            Long agentId,
            String triggerMode,
            WorkflowGraph graph,
            List<WorkflowVariableSpec> variablesSchema,
            String defaultModelId,
            List<String> fallbackModelIds,
            String status,
            Integer publishedVersion,
            Integer runCount,
            String remark,
            LocalDateTime updatedAt
    ) {}

    public record WorkflowExecuteRequest(
            Map<String, Object> inputs,
            WorkflowGraph overrideGraph,
            String triggerSource,
            Boolean dryRun
    ) {}

    public record NodeTraceDTO(
            String nodeId,
            String nodeType,
            String nodeLabel,
            String modelId,
            String providerCode,
            Integer attemptNo,
            Boolean fallbackUsed,
            Map<String, Object> inputSnapshot,
            Map<String, Object> outputSnapshot,
            String status,
            Integer promptTokens,
            Integer completionTokens,
            Long latencyMs,
            String errorMessage,
            LocalDateTime startedAt,
            LocalDateTime finishedAt
    ) {}

    public record WorkflowRunDTO(
            Long id,
            String runNo,
            Long workflowId,
            String workflowCode,
            String triggerSource,
            Map<String, Object> inputs,
            Map<String, Object> outputs,
            String status,
            Integer totalTokens,
            Long totalLatencyMs,
            Integer executedNodes,
            String errorMessage,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            List<NodeTraceDTO> nodeTraces
    ) {}
}
