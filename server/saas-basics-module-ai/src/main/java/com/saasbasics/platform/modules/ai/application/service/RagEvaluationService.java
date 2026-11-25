package com.saasbasics.platform.modules.ai.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.id.UuidV7Generator;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.KnowledgeCitationDTO;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiRagEvalRunEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.AiRagEvalRunMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Offline RAG retrieval evaluation service computing Recall@K, MRR, NDCG@K, and Keyword Hit Rate
 * against persisted KnowledgeBase chunks.
 */
@Service
public class RagEvaluationService {

    public record EvalCase(
            String question,
            List<Long> expectedDocIds,
            List<String> expectedKeywords
    ) {}

    public record EvalRequest(
            Long kbId,
            String evalName,
            Integer topK,
            List<EvalCase> cases
    ) {}

    public record CaseResult(
            String question,
            int retrievedCount,
            double recall,
            double reciprocalRank,
            double ndcg,
            boolean hit
    ) {}

    public record EvalReport(
            UUID publicId,
            Long kbId,
            String evalName,
            int topK,
            int sampleCount,
            double recallAtK,
            double mrr,
            double ndcgAtK,
            double hitRate,
            long avgLatencyMs,
            String status,
            List<CaseResult> caseResults
    ) {}

    private final KnowledgeRagService ragService;
    private final ObjectProvider<AiRagEvalRunMapper> evalRunMapperProvider;
    private final ObjectMapper objectMapper;
    private final UuidV7Generator uuidGenerator = new UuidV7Generator();

    public RagEvaluationService(
            KnowledgeRagService ragService,
            ObjectProvider<AiRagEvalRunMapper> evalRunMapperProvider,
            ObjectMapper objectMapper
    ) {
        this.ragService = ragService;
        this.evalRunMapperProvider = evalRunMapperProvider;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public EvalReport evaluate(EvalRequest request) {
        if (request == null || request.kbId() == null || request.cases() == null || request.cases().isEmpty()) {
            throw new BizException("RAG_EVAL_INVALID", "Knowledge base id and at least one evaluation case are required");
        }
        long tenantId = TenantAccessContextHolder.requiredTenantId();
        int k = request.topK() != null ? Math.min(Math.max(1, request.topK()), 20) : 5;
        String name = (request.evalName() == null || request.evalName().isBlank())
                ? "RAG Offline Eval KB#" + request.kbId() : request.evalName().trim();

        double totalRecall = 0;
        double totalMrr = 0;
        double totalNdcg = 0;
        int totalHits = 0;
        long totalLatency = 0;
        List<CaseResult> caseResults = new ArrayList<>(request.cases().size());

        for (EvalCase evalCase : request.cases()) {
            long start = System.currentTimeMillis();
            List<KnowledgeCitationDTO> hits = ragService.hybridSearch(List.of(request.kbId()), evalCase.question(), k);
            totalLatency += Math.max(1L, System.currentTimeMillis() - start);

            Set<Long> expectedDocs = evalCase.expectedDocIds() == null ? Set.of() : new HashSet<>(evalCase.expectedDocIds());
            List<String> expectedKw = evalCase.expectedKeywords() == null ? List.of()
                    : evalCase.expectedKeywords().stream()
                    .filter(s -> s != null && !s.isBlank())
                    .map(s -> s.toLowerCase(Locale.ROOT))
                    .toList();

            int firstRelevantRank = 0;
            Set<Long> matchedDocs = new HashSet<>();
            double dcg = 0.0;
            boolean kwHit = false;

            for (int i = 0; i < hits.size(); i++) {
                KnowledgeCitationDTO citation = hits.get(i);
                int rank = i + 1;
                boolean docMatch = expectedDocs.contains(citation.docId());
                if (docMatch) {
                    matchedDocs.add(citation.docId());
                }
                String excerpt = citation.excerpt() != null ? citation.excerpt().toLowerCase(Locale.ROOT) : "";
                boolean keywordMatch = !expectedKw.isEmpty() && expectedKw.stream().anyMatch(excerpt::contains);
                if (keywordMatch) {
                    kwHit = true;
                }
                boolean relevant = !expectedDocs.isEmpty() ? docMatch : keywordMatch;
                if (relevant) {
                    if (firstRelevantRank == 0) {
                        firstRelevantRank = rank;
                    }
                    dcg += 1.0 / (Math.log(rank + 1) / Math.log(2));
                }
            }

            double recall;
            int idealCount;
            if (!expectedDocs.isEmpty()) {
                recall = (double) matchedDocs.size() / expectedDocs.size();
                idealCount = Math.min(k, expectedDocs.size());
            } else if (!expectedKw.isEmpty()) {
                long matchedKwCount = expectedKw.stream().filter(kw -> hits.stream()
                        .anyMatch(h -> h.excerpt() != null && h.excerpt().toLowerCase(Locale.ROOT).contains(kw))).count();
                recall = (double) matchedKwCount / expectedKw.size();
                idealCount = Math.min(k, expectedKw.size());
            } else {
                recall = hits.isEmpty() ? 0.0 : 1.0;
                idealCount = hits.isEmpty() ? 1 : 1;
            }

            double idcg = 0.0;
            for (int i = 0; i < idealCount; i++) {
                idcg += 1.0 / (Math.log(i + 2) / Math.log(2));
            }
            double ndcg = idcg > 0 ? Math.min(1.0, dcg / idcg) : 0.0;
            double rr = firstRelevantRank > 0 ? 1.0 / firstRelevantRank : 0.0;
            boolean hit = firstRelevantRank > 0 || kwHit;

            totalRecall += recall;
            totalMrr += rr;
            totalNdcg += ndcg;
            if (hit) totalHits++;

            caseResults.add(new CaseResult(evalCase.question(), hits.size(), round4(recall), round4(rr), round4(ndcg), hit));
        }

        int n = request.cases().size();
        double avgRecall = round4(totalRecall / n);
        double avgMrr = round4(totalMrr / n);
        double avgNdcg = round4(totalNdcg / n);
        double hitRate = round4((double) totalHits / n);
        long avgLatency = Math.max(1L, totalLatency / n);

        UUID publicId = uuidGenerator.generate();
        AiRagEvalRunMapper mapper = evalRunMapperProvider.getIfAvailable();
        if (mapper != null) {
            AiRagEvalRunEntity entity = new AiRagEvalRunEntity();
            entity.setTenantId(tenantId);
            entity.setPublicId(publicId.toString());
            entity.setKbId(request.kbId());
            entity.setEvalName(name);
            entity.setTopK(k);
            entity.setSampleCount(n);
            entity.setRecallAtK(BigDecimal.valueOf(avgRecall));
            entity.setMrr(BigDecimal.valueOf(avgMrr));
            entity.setNdcgAtK(BigDecimal.valueOf(avgNdcg));
            entity.setHitRate(BigDecimal.valueOf(hitRate));
            entity.setAvgLatencyMs(avgLatency);
            entity.setDetailsJson(serializeSafe(caseResults));
            entity.setStatus("COMPLETED");
            entity.setVersion(0);
            entity.setDeleted(0);
            mapper.insert(entity);
        }

        return new EvalReport(publicId, request.kbId(), name, k, n, avgRecall, avgMrr, avgNdcg, hitRate,
                avgLatency, "COMPLETED", caseResults);
    }

    public List<EvalReport> listRuns(Long kbId) {
        AiRagEvalRunMapper mapper = evalRunMapperProvider.getIfAvailable();
        if (mapper == null) return List.of();
        LambdaQueryWrapper<AiRagEvalRunEntity> qw = new LambdaQueryWrapper<AiRagEvalRunEntity>()
                .eq(AiRagEvalRunEntity::getDeleted, 0)
                .orderByDesc(AiRagEvalRunEntity::getId);
        if (kbId != null) {
            qw.eq(AiRagEvalRunEntity::getKbId, kbId);
        }
        return mapper.selectList(qw).stream()
                .map(e -> new EvalReport(
                        UUID.fromString(e.getPublicId()), e.getKbId(), e.getEvalName(),
                        e.getTopK(), e.getSampleCount(),
                        e.getRecallAtK().doubleValue(), e.getMrr().doubleValue(),
                        e.getNdcgAtK().doubleValue(), e.getHitRate().doubleValue(),
                        e.getAvgLatencyMs(), e.getStatus(), List.of()
                ))
                .toList();
    }

    private String serializeSafe(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception ignored) {
            return "[]";
        }
    }

    private static double round4(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }
}
