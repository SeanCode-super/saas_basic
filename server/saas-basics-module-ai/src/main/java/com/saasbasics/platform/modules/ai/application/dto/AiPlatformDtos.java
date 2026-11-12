package com.saasbasics.platform.modules.ai.application.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Enterprise Knowledge Base RAG, Agent Studio & Fine-Tuning Workbench DTOs.
 */
public final class AiPlatformDtos {

    private AiPlatformDtos() {
    }

    // --- Knowledge Base & RAG ---
    public record KnowledgeBaseDTO(
            Long id,
            String kbCode,
            String kbName,
            String category,
            String embeddingModel,
            int chunkSize,
            int chunkOverlap,
            int docCount,
            int chunkCount,
            String status,
            String remark,
            LocalDateTime updatedAt
    ) {}

    public record KnowledgeBaseSaveRequest(
            Long id,
            String kbCode,
            String kbName,
            String category,
            String embeddingModel,
            Integer chunkSize,
            Integer chunkOverlap,
            String remark
    ) {}

    public record KnowledgeDocDTO(
            Long id,
            Long kbId,
            String docName,
            String fileType,
            long fileSize,
            String parseStatus,
            int chunkCount,
            int charCount,
            String summary,
            LocalDateTime createdAt
    ) {}

    public record KnowledgeChunkDTO(
            Long id,
            Long docId,
            int chunkIndex,
            int pageNo,
            String sectionTitle,
            String content,
            String keywords
    ) {}

    public record KnowledgeTextIngestRequest(
            Long kbId,
            String docName,
            String content,
            String fileType
    ) {}

    public record KnowledgeQueryRequest(
            Long kbId,
            String question,
            Integer topK,
            String modelId
    ) {}

    public record KnowledgeCitationDTO(
            int citationNo,
            Long chunkId,
            Long docId,
            String docName,
            int pageNo,
            String sectionTitle,
            String excerpt,
            double score
    ) {}

    public record KnowledgeAskResponse(
            String question,
            String answer,
            String engine,
            long latencyMs,
            List<KnowledgeCitationDTO> citations
    ) {}

    public record DocumentReviewRequest(
            Long kbId,
            String documentTitle,
            String documentText,
            String modelId
    ) {}

    public record DocumentRiskItemDTO(
            String riskLevel,
            String category,
            String clauseExcerpt,
            String issueSummary,
            String policyCitation,
            String suggestedRevision
    ) {}

    public record DocumentReviewResponse(
            String documentTitle,
            int complianceScore,
            int highRiskCount,
            int mediumRiskCount,
            String summary,
            List<DocumentRiskItemDTO> risks,
            List<KnowledgeCitationDTO> references
    ) {}

    // --- Agent Studio ---
    public record AiAgentDTO(
            Long id,
            String agentCode,
            String agentName,
            String avatar,
            String roleTitle,
            String systemPrompt,
            String modelId,
            List<Long> kbIds,
            List<String> toolCodes,
            String channelTypes,
            double temperature,
            String welcomeMessage,
            String status,
            int runCount,
            String remark
    ) {}

    public record AiAgentSaveRequest(
            Long id,
            String agentCode,
            String agentName,
            String avatar,
            String roleTitle,
            String systemPrompt,
            String modelId,
            List<Long> kbIds,
            List<String> toolCodes,
            String channelTypes,
            Double temperature,
            String welcomeMessage,
            String status,
            String remark
    ) {}

    public record AiToolOptionDTO(
            String toolCode,
            String toolName,
            String category,
            String riskLevel,
            String description
    ) {}

    public record AgentChatRequest(
            Long agentId,
            Long sessionId,
            String message
    ) {}

    public record AgentChatResponse(
            Long agentId,
            Long sessionId,
            String agentName,
            String reply,
            String engine,
            List<String> toolTraces,
            List<KnowledgeCitationDTO> citations,
            long latencyMs
    ) {}

    // --- Fine-tuning Workbench ---
    public record FinetuneDatasetDTO(
            Long id,
            String datasetCode,
            String datasetName,
            String taskType,
            int sampleCount,
            int approvedCount,
            String formatType,
            String status,
            String remark
    ) {}

    public record FinetuneDatasetSaveRequest(
            Long id,
            String datasetCode,
            String datasetName,
            String taskType,
            String formatType,
            String remark
    ) {}

    public record FinetuneSampleDTO(
            Long id,
            Long datasetId,
            String instruction,
            String inputText,
            String outputText,
            String sourceType,
            double qualityScore,
            String reviewStatus,
            boolean desensitized,
            LocalDateTime createdAt
    ) {}

    public record FinetuneSampleAddRequest(
            Long datasetId,
            String instruction,
            String inputText,
            String outputText,
            String sourceType,
            Boolean desensitize
    ) {}

    public record FinetuneSampleReviewRequest(
            String reviewStatus
    ) {}

    public record FinetuneJobDTO(
            Long id,
            String jobCode,
            String jobName,
            Long datasetId,
            String baseModel,
            String tuningMethod,
            int epochs,
            String learningRate,
            int loraRank,
            String status,
            int progressPercent,
            double trainLoss,
            double evalAccuracy,
            double baselineAccuracy,
            String outputModelId,
            Map<String, Object> metrics,
            LocalDateTime createdAt
    ) {}

    public record FinetuneJobCreateRequest(
            String jobName,
            Long datasetId,
            String baseModel,
            String tuningMethod,
            Integer epochs,
            String learningRate,
            Integer loraRank
    ) {}
}
