package com.saasbasics.platform.modules.ai.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.modules.ai.api.AiGatewayFacade;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.DocumentReviewRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.DocumentReviewResponse;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.DocumentRiskItemDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.KnowledgeAskResponse;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.KnowledgeBaseDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.KnowledgeBaseSaveRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.KnowledgeChunkDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.KnowledgeCitationDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.KnowledgeDocDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.KnowledgeQueryRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.KnowledgeTextIngestRequest;
import com.saasbasics.platform.modules.ai.application.service.DocumentParserService.ParsedDocument;
import com.saasbasics.platform.modules.ai.domain.HybridRagScoring;
import com.saasbasics.platform.modules.ai.domain.HybridRagScoring.ChunkCandidate;
import com.saasbasics.platform.modules.ai.domain.HybridRagScoring.RankedChunk;
import com.saasbasics.platform.modules.ai.domain.HybridRagScoring.TextChunk;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.KnowledgeBaseEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.KnowledgeChunkEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.KnowledgeDocEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.KnowledgeBaseMapper;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.KnowledgeChunkMapper;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.KnowledgeDocMapper;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Enterprise Knowledge Base & Hybrid RAG Engine:
 * manages document ingestion, sliding-window chunking, BM25 + dense vector RRF hybrid retrieval,
 * grounded Q&A with exact source citations, and Contract/Tender compliance review.
 */
@Service
public class KnowledgeRagService {

    private static final Pattern NUMBER_PERCENT = Pattern.compile("(\\d{1,3})\\s*%");
    private static final Pattern NUMBER_DAYS = Pattern.compile("(\\d{1,4})\\s*(?:天|个工作日|日)");

    private final ObjectProvider<KnowledgeBaseMapper> kbMapperProvider;
    private final ObjectProvider<KnowledgeDocMapper> docMapperProvider;
    private final ObjectProvider<KnowledgeChunkMapper> chunkMapperProvider;
    private final DocumentParserService documentParserService;
    private final AiChatGatewayService chatGatewayService;
    private final ObjectMapper objectMapper;

    public KnowledgeRagService(
            ObjectProvider<KnowledgeBaseMapper> kbMapperProvider,
            ObjectProvider<KnowledgeDocMapper> docMapperProvider,
            ObjectProvider<KnowledgeChunkMapper> chunkMapperProvider,
            DocumentParserService documentParserService,
            AiChatGatewayService chatGatewayService,
            ObjectMapper objectMapper
    ) {
        this.kbMapperProvider = kbMapperProvider;
        this.docMapperProvider = docMapperProvider;
        this.chunkMapperProvider = chunkMapperProvider;
        this.documentParserService = documentParserService;
        this.chatGatewayService = chatGatewayService;
        this.objectMapper = objectMapper;
    }

    public List<KnowledgeBaseDTO> listKnowledgeBases() {
        KnowledgeBaseMapper mapper = kbMapperProvider.getIfAvailable();
        if (mapper == null) return List.of();
        return mapper.selectList(new LambdaQueryWrapper<KnowledgeBaseEntity>()
                        .eq(KnowledgeBaseEntity::getTenantId, currentTenantId())
                        .eq(KnowledgeBaseEntity::getDeleted, 0)
                        .orderByDesc(KnowledgeBaseEntity::getId))
                .stream()
                .map(this::toKbDto)
                .toList();
    }

    @Transactional
    public KnowledgeBaseDTO saveKnowledgeBase(KnowledgeBaseSaveRequest req) {
        KnowledgeBaseMapper mapper = kbMapperProvider.getIfAvailable();
        if (mapper == null) throw new IllegalStateException("知识库服务不可用");
        Long tenantId = currentTenantId();
        Long userId = currentUserId();
        KnowledgeBaseEntity entity = req.id() != null ? mapper.selectOne(new LambdaQueryWrapper<KnowledgeBaseEntity>()
                .eq(KnowledgeBaseEntity::getId, req.id())
                .eq(KnowledgeBaseEntity::getTenantId, tenantId)
                .eq(KnowledgeBaseEntity::getDeleted, 0)) : null;

        if (entity == null) {
            entity = new KnowledgeBaseEntity();
            entity.setTenantId(tenantId);
            entity.setKbCode(req.kbCode() != null && !req.kbCode().isBlank()
                    ? req.kbCode().trim() : "kb_" + UUID.randomUUID().toString().substring(0, 8));
            entity.setDocCount(0);
            entity.setChunkCount(0);
            entity.setStatus("ENABLED");
            entity.setCreatedBy(userId);
            entity.setCreatedAt(LocalDateTime.now());
            entity.setDeleted(0);
        }
        entity.setKbName(req.kbName() != null ? req.kbName().trim() : "未命名知识库");
        entity.setCategory(req.category() != null && !req.category().isBlank() ? req.category().trim() : "GENERAL");
        entity.setEmbeddingModel(req.embeddingModel() != null && !req.embeddingModel().isBlank()
                ? req.embeddingModel().trim() : "hybrid-bm25-bge");
        entity.setChunkSize(req.chunkSize() != null ? req.chunkSize() : 480);
        entity.setChunkOverlap(req.chunkOverlap() != null ? req.chunkOverlap() : 80);
        entity.setRemark(req.remark());
        entity.setUpdatedBy(userId);
        entity.setUpdatedAt(LocalDateTime.now());

        if (entity.getId() == null) mapper.insert(entity);
        else mapper.updateById(entity);
        return toKbDto(entity);
    }

    public List<KnowledgeDocDTO> listDocs(Long kbId) {
        KnowledgeDocMapper mapper = docMapperProvider.getIfAvailable();
        if (mapper == null) return List.of();
        LambdaQueryWrapper<KnowledgeDocEntity> qw = new LambdaQueryWrapper<KnowledgeDocEntity>()
                .eq(KnowledgeDocEntity::getTenantId, currentTenantId())
                .eq(KnowledgeDocEntity::getDeleted, 0);
        if (kbId != null) {
            qw.eq(KnowledgeDocEntity::getKbId, kbId);
        }
        return mapper.selectList(qw.orderByDesc(KnowledgeDocEntity::getId))
                .stream()
                .map(this::toDocDto)
                .toList();
    }

    public List<KnowledgeChunkDTO> listChunks(Long docId) {
        KnowledgeChunkMapper mapper = chunkMapperProvider.getIfAvailable();
        if (mapper == null) return List.of();
        return mapper.selectList(new LambdaQueryWrapper<KnowledgeChunkEntity>()
                        .eq(KnowledgeChunkEntity::getTenantId, currentTenantId())
                        .eq(KnowledgeChunkEntity::getDocId, docId)
                        .eq(KnowledgeChunkEntity::getDeleted, 0)
                        .orderByAsc(KnowledgeChunkEntity::getChunkIndex))
                .stream()
                .map(c -> new KnowledgeChunkDTO(
                        c.getId(), c.getDocId(),
                        c.getChunkIndex() != null ? c.getChunkIndex() : 0,
                        c.getPageNo() != null ? c.getPageNo() : 1,
                        c.getSectionTitle(), c.getContent(), c.getKeywords()
                ))
                .toList();
    }

    @Transactional
    public KnowledgeDocDTO uploadDocument(Long kbId, MultipartFile file) throws IOException {
        ParsedDocument parsed = documentParserService.parseUpload(file);
        return persistIngestedDocument(kbId, parsed.fileName(), parsed.fileType(), parsed.fileSize(),
                parsed.summary(), parsed.textContent());
    }

    @Transactional
    public KnowledgeDocDTO ingestText(KnowledgeTextIngestRequest req) {
        if (req.kbId() == null || req.docName() == null || req.docName().isBlank()
                || req.content() == null || req.content().isBlank()) {
            throw new IllegalArgumentException("知识库、文档标题和正文内容不能为空");
        }
        String content = req.content().trim();
        String summary = content.replaceAll("\\s+", " ");
        if (summary.length() > 160) summary = summary.substring(0, 160) + "…";
        String type = req.fileType() != null && !req.fileType().isBlank() ? req.fileType().trim().toUpperCase(Locale.ROOT) : "MD";
        return persistIngestedDocument(req.kbId(), req.docName().trim(), type, content.length(), summary, content);
    }

    @Transactional
    public void deleteDoc(Long docId) {
        KnowledgeDocMapper docMapper = docMapperProvider.getIfAvailable();
        KnowledgeChunkMapper chunkMapper = chunkMapperProvider.getIfAvailable();
        if (docMapper == null) return;
        Long tenantId = currentTenantId();
        KnowledgeDocEntity doc = docMapper.selectOne(new LambdaQueryWrapper<KnowledgeDocEntity>()
                .eq(KnowledgeDocEntity::getId, docId)
                .eq(KnowledgeDocEntity::getTenantId, tenantId)
                .eq(KnowledgeDocEntity::getDeleted, 0));
        if (doc == null) return;

        doc.setDeleted(1);
        doc.setDeletedAt(LocalDateTime.now());
        docMapper.updateById(doc);

        if (chunkMapper != null) {
            List<KnowledgeChunkEntity> chunks = chunkMapper.selectList(new LambdaQueryWrapper<KnowledgeChunkEntity>()
                    .eq(KnowledgeChunkEntity::getTenantId, tenantId)
                    .eq(KnowledgeChunkEntity::getDocId, docId)
                    .eq(KnowledgeChunkEntity::getDeleted, 0));
            for (KnowledgeChunkEntity c : chunks) {
                c.setDeleted(1);
                c.setDeletedAt(LocalDateTime.now());
                chunkMapper.updateById(c);
            }
        }
        recalcKbCounters(tenantId, doc.getKbId());
    }

    public List<KnowledgeCitationDTO> hybridSearch(List<Long> kbIds, String query, int topK) {
        KnowledgeChunkMapper chunkMapper = chunkMapperProvider.getIfAvailable();
        KnowledgeDocMapper docMapper = docMapperProvider.getIfAvailable();
        if (chunkMapper == null || docMapper == null || query == null || query.isBlank()) {
            return List.of();
        }
        Long tenantId = currentTenantId();
        LambdaQueryWrapper<KnowledgeChunkEntity> qw = new LambdaQueryWrapper<KnowledgeChunkEntity>()
                .eq(KnowledgeChunkEntity::getTenantId, tenantId)
                .eq(KnowledgeChunkEntity::getDeleted, 0);
        if (kbIds != null && !kbIds.isEmpty()) {
            qw.in(KnowledgeChunkEntity::getKbId, kbIds);
        }
        List<KnowledgeChunkEntity> chunkRows = chunkMapper.selectList(qw.last("LIMIT 500"));
        if (chunkRows.isEmpty()) {
            return List.of();
        }

        Map<Long, String> docNames = new HashMap<>();
        docMapper.selectList(new LambdaQueryWrapper<KnowledgeDocEntity>()
                        .eq(KnowledgeDocEntity::getTenantId, tenantId)
                        .eq(KnowledgeDocEntity::getDeleted, 0))
                .forEach(d -> docNames.put(d.getId(), d.getDocName()));

        List<ChunkCandidate> candidates = chunkRows.stream()
                .map(c -> new ChunkCandidate(
                        c.getId(),
                        c.getDocId(),
                        docNames.getOrDefault(c.getDocId(), "企业文档#" + c.getDocId()),
                        c.getChunkIndex() != null ? c.getChunkIndex() : 0,
                        c.getPageNo() != null ? c.getPageNo() : 1,
                        c.getSectionTitle() != null ? c.getSectionTitle() : "正文",
                        c.getContent(),
                        parseVector(c.getEmbeddingJson())
                ))
                .toList();

        List<RankedChunk> ranked = HybridRagScoring.rankHybrid(query, candidates, topK);
        List<KnowledgeCitationDTO> citations = new ArrayList<>(ranked.size());
        for (int i = 0; i < ranked.size(); i++) {
            RankedChunk r = ranked.get(i);
            String excerpt = r.chunk().content();
            if (excerpt.length() > 260) {
                excerpt = excerpt.substring(0, 260) + "…";
            }
            citations.add(new KnowledgeCitationDTO(
                    i + 1,
                    r.chunk().chunkId(),
                    r.chunk().docId(),
                    r.chunk().docName(),
                    r.chunk().pageNo(),
                    r.chunk().sectionTitle(),
                    excerpt,
                    Math.round(r.rrfScore() * 1000.0) / 1000.0
            ));
        }
        return citations;
    }

    public KnowledgeAskResponse askKnowledgeBase(KnowledgeQueryRequest req) {
        long start = System.currentTimeMillis();
        String question = req.question() != null ? req.question().trim() : "";
        if (question.isEmpty()) {
            throw new IllegalArgumentException("检索提问不能为空");
        }
        int topK = req.topK() != null ? Math.min(Math.max(1, req.topK()), 10) : 4;
        List<Long> kbIds = req.kbId() != null ? List.of(req.kbId()) : List.of();
        List<KnowledgeCitationDTO> citations = hybridSearch(kbIds, question, topK);

        String answer = null;
        String engine = "HYBRID_EXTRACTIVE_RAG";
        boolean hasLiveKey = chatGatewayService.getModels().stream().anyMatch(m -> Boolean.TRUE.equals(m.get("hasApiKey")));

        if (hasLiveKey && !citations.isEmpty()) {
            try {
                StringBuilder ctx = new StringBuilder();
                for (KnowledgeCitationDTO c : citations) {
                    ctx.append("[").append(c.citationNo()).append("] 《").append(c.docName())
                            .append("》 P").append(c.pageNo()).append(" ").append(c.sectionTitle())
                            .append("\n").append(c.excerpt()).append("\n\n");
                }
                AiGatewayFacade.ChatCompletionOutput out = chatGatewayService.complete(
                        new AiGatewayFacade.ChatCompletionCommand(
                                req.modelId(),
                                null,
                                List.of(
                                        new AiGatewayFacade.ChatMessage("system",
                                                "你是企业知识库权威问答助手。请严格基于提供的引用片段回答问题，并在结论句末使用 [1][2] 标注引用来源；若片段未提及请坦诚说明。"),
                                        new AiGatewayFacade.ChatMessage("user", "参考资料：\n" + ctx + "\n用户问题：" + question)
                                ),
                                0.2
                        )
                );
                if (out.reply() != null && !out.reply().isBlank()) {
                    answer = out.reply();
                    engine = "HYBRID_LLM_RAG";
                }
            } catch (Exception ignored) {
            }
        }

        if (answer == null) {
            if (citations.isEmpty()) {
                answer = "未在当前知识库中检出与您问题高度匹配的文档片段，建议先上传相关规章制度或业务文档。";
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append("根据企业知识库混合向量 + BM25 RRF 召回结果，为您梳理核心依据如下：\n\n");
                for (int i = 0; i < Math.min(citations.size(), 3); i++) {
                    KnowledgeCitationDTO c = citations.get(i);
                    sb.append("- **[").append(c.citationNo()).append("] 《").append(c.docName())
                            .append("》（第 ").append(c.pageNo()).append(" 页 · ").append(c.sectionTitle())
                            .append("）**：").append(c.excerpt()).append("\n");
                }
                sb.append("\n提示：如需大模型深度归纳润色，可在「AI 模型网关」填写 DeepSeek/OpenAI API Key。");
                answer = sb.toString();
            }
        }

        return new KnowledgeAskResponse(question, answer, engine,
                Math.max(1L, System.currentTimeMillis() - start), citations);
    }

    public DocumentReviewResponse reviewDocument(DocumentReviewRequest req) {
        String title = req.documentTitle() != null && !req.documentTitle().isBlank()
                ? req.documentTitle().trim() : "待审合同/标书文本";
        String body = req.documentText() != null ? req.documentText().trim() : "";
        if (body.isEmpty()) {
            throw new IllegalArgumentException("请粘贴或上传待审查的合同/标书正文");
        }

        List<Long> kbIds = req.kbId() != null ? List.of(req.kbId()) : List.of();
        List<KnowledgeCitationDTO> references = hybridSearch(kbIds, title + " " + body.substring(0, Math.min(200, body.length())), 4);

        List<DocumentRiskItemDTO> risks = new ArrayList<>();
        String low = body.toLowerCase(Locale.ROOT);

        // Rule 1: Low advance payment check
        Matcher pctMatcher = NUMBER_PERCENT.matcher(body);
        if (body.contains("首付") || body.contains("预付")) {
            int minPct = 100;
            while (pctMatcher.find()) {
                int v = Integer.parseInt(pctMatcher.group(1));
                if (v < minPct && v > 0) minPct = v;
            }
            if (minPct < 30 || body.contains("零首付") || body.contains("无预付")) {
                risks.add(new DocumentRiskItemDTO(
                        "HIGH",
                        "付款与首付比例",
                        "文本涉及预付/首付比例低于 30%（检出 " + (minPct < 100 ? minPct + "%" : "零预付") + "）",
                        "违反公司《CRM商务合同审查与风控红线手册》：单笔合同首付款比例不得低于 30%。",
                        references.isEmpty() ? "《CRM商务合同审查与风控红线手册》 P1" : "《" + references.get(0).docName() + "》 P" + references.get(0).pageNo(),
                        "建议调整为：合同签署后 5 个工作日内支付合同总额 30% 作为首付款，降低履约垫资风险。"
                ));
            }
        }

        // Rule 2: Payment term > 45 days check
        if (body.contains("账期") || body.contains("付款") || body.contains("验收后")) {
            Matcher dayMatcher = NUMBER_DAYS.matcher(body);
            int maxDays = 0;
            while (dayMatcher.find()) {
                int d = Integer.parseInt(dayMatcher.group(1));
                if (d > maxDays) maxDays = d;
            }
            if (maxDays > 45) {
                risks.add(new DocumentRiskItemDTO(
                        "MEDIUM",
                        "回款账期上限",
                        "文本约定付款账期达 " + maxDays + " 天",
                        "超出企业财务内控标准（验收后 45 个工作日上限），会拉长现金流周转周期。",
                        "《CRM商务合同审查与风控红线手册》 P1",
                        "建议将尾款结算期缩短至验收合格后 30~45 个工作日内，并增设逾期日千分之零点五违约金。"
                ));
            }
        }

        // Rule 3: Unlimited indemnity / liability
        if (low.contains("无限连带") || low.contains("全部损失") || low.contains("无上限") || low.contains("连带无限")) {
            risks.add(new DocumentRiskItemDTO(
                    "HIGH",
                    "违约赔偿责任上限",
                    "存在「无限责任 / 全部间接损失赔偿」相关表述",
                    "企业红线严禁签署未设赔付封顶的无限连带责任条款，可能引发不可控经营风险。",
                    "《CRM商务合同审查与风控红线手册》 P1",
                    "建议明确约定：乙方累计违约赔偿总额以本合同已收款总额的 100% 为上限，且不含间接及预期利润损失。"
            ));
        }

        // Rule 4: Source code / IPR premature transfer
        if ((body.contains("源码") || body.contains("知识产权")) && (body.contains("全部归甲方") || body.contains("永久免费") || body.contains("签署即转移"))) {
            risks.add(new DocumentRiskItemDTO(
                    "MEDIUM",
                    "知识产权与源码归属",
                    "检出源码移交或知识产权前置转移约定",
                    "按照企业合规指引，定制成果知识产权及交付源码应在客户付清全部款项后方可转移。",
                    "《CRM商务合同审查与风控红线手册》 P2",
                    "建议补充限定条件：通用底座及第三方组件知识产权仍归我方所有，定制开发部分权利自甲方付清全款之日起转移。"
            ));
        }

        if (risks.isEmpty()) {
            risks.add(new DocumentRiskItemDTO(
                    "LOW",
                    "整体合规初审通过",
                    "未发现首付比例低于30%、超长账期、无限赔偿等明显红线违例条款",
                    "建议人工复核双方盖章主体名称、标的金额大小写一致及争议管辖法院条款。",
                    references.isEmpty() ? "企业合规基线检查" : "《" + references.get(0).docName() + "》",
                    "可直接发起用印审批或导入商务合同台账。"
            ));
        }

        int highCount = (int) risks.stream().filter(r -> "HIGH".equals(r.riskLevel())).count();
        int medCount = (int) risks.stream().filter(r -> "MEDIUM".equals(r.riskLevel())).count();
        int score = Math.max(25, 100 - highCount * 28 - medCount * 14);
        String summary = highCount > 0
                ? "检出 " + highCount + " 项高风险违约/账期红线与 " + medCount + " 项中风险提示，建议退回业务方修订后再进入签章流程。"
                : (medCount > 0
                        ? "未触发高危一票否决红线，但存在 " + medCount + " 项建议优化的账期或知识产权条款。"
                        : "文书核心财务与责任条款符合企业法务合规知识库红线规范。");

        return new DocumentReviewResponse(title, score, highCount, medCount, summary, risks, references);
    }

    private KnowledgeDocDTO persistIngestedDocument(
            Long kbId, String docName, String fileType, long fileSize, String summary, String rawText) {
        KnowledgeBaseMapper kbMapper = kbMapperProvider.getIfAvailable();
        KnowledgeDocMapper docMapper = docMapperProvider.getIfAvailable();
        KnowledgeChunkMapper chunkMapper = chunkMapperProvider.getIfAvailable();
        if (kbMapper == null || docMapper == null || chunkMapper == null) {
            throw new IllegalStateException("知识库存储映射未初始化");
        }
        Long tenantId = currentTenantId();
        Long userId = currentUserId();

        KnowledgeBaseEntity kb = kbMapper.selectOne(new LambdaQueryWrapper<KnowledgeBaseEntity>()
                .eq(KnowledgeBaseEntity::getId, kbId)
                .eq(KnowledgeBaseEntity::getTenantId, tenantId)
                .eq(KnowledgeBaseEntity::getDeleted, 0));
        if (kb == null) {
            throw new IllegalArgumentException("目标知识库不存在");
        }

        int chunkSize = kb.getChunkSize() != null ? kb.getChunkSize() : 480;
        int overlap = kb.getChunkOverlap() != null ? kb.getChunkOverlap() : 80;
        List<TextChunk> chunks = HybridRagScoring.splitIntoChunks(rawText, chunkSize, overlap);

        KnowledgeDocEntity doc = new KnowledgeDocEntity();
        doc.setTenantId(tenantId);
        doc.setKbId(kbId);
        doc.setDocName(docName);
        doc.setFileType(fileType);
        doc.setFileSize(fileSize);
        doc.setParseStatus("INDEXED");
        doc.setChunkCount(chunks.size());
        doc.setCharCount(rawText.length());
        doc.setSummary(summary);
        doc.setRawText(rawText);
        doc.setCreatedBy(userId);
        doc.setUpdatedBy(userId);
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());
        doc.setDeleted(0);
        docMapper.insert(doc);

        for (TextChunk tc : chunks) {
            KnowledgeChunkEntity ce = new KnowledgeChunkEntity();
            ce.setTenantId(tenantId);
            ce.setKbId(kbId);
            ce.setDocId(doc.getId());
            ce.setChunkIndex(tc.chunkIndex());
            ce.setPageNo(tc.pageNo());
            ce.setSectionTitle(tc.sectionTitle());
            ce.setContent(tc.content());
            ce.setTokenCount(tc.content().length());
            ce.setKeywords(tc.keywords());
            ce.setEmbeddingJson(toJsonSafe(tc.denseVector()));
            ce.setCreatedBy(userId);
            ce.setUpdatedBy(userId);
            ce.setCreatedAt(LocalDateTime.now());
            ce.setUpdatedAt(LocalDateTime.now());
            ce.setDeleted(0);
            chunkMapper.insert(ce);
        }

        recalcKbCounters(tenantId, kbId);
        return toDocDto(doc);
    }

    private void recalcKbCounters(Long tenantId, Long kbId) {
        KnowledgeBaseMapper kbMapper = kbMapperProvider.getIfAvailable();
        KnowledgeDocMapper docMapper = docMapperProvider.getIfAvailable();
        KnowledgeChunkMapper chunkMapper = chunkMapperProvider.getIfAvailable();
        if (kbMapper == null || docMapper == null || chunkMapper == null || kbId == null) return;

        long docs = docMapper.selectCount(new LambdaQueryWrapper<KnowledgeDocEntity>()
                .eq(KnowledgeDocEntity::getTenantId, tenantId)
                .eq(KnowledgeDocEntity::getKbId, kbId)
                .eq(KnowledgeDocEntity::getDeleted, 0));
        long chunks = chunkMapper.selectCount(new LambdaQueryWrapper<KnowledgeChunkEntity>()
                .eq(KnowledgeChunkEntity::getTenantId, tenantId)
                .eq(KnowledgeChunkEntity::getKbId, kbId)
                .eq(KnowledgeChunkEntity::getDeleted, 0));

        KnowledgeBaseEntity kb = kbMapper.selectById(kbId);
        if (kb != null) {
            kb.setDocCount((int) docs);
            kb.setChunkCount((int) chunks);
            kb.setUpdatedAt(LocalDateTime.now());
            kbMapper.updateById(kb);
        }
    }

    private KnowledgeBaseDTO toKbDto(KnowledgeBaseEntity e) {
        return new KnowledgeBaseDTO(
                e.getId(), e.getKbCode(), e.getKbName(), e.getCategory(), e.getEmbeddingModel(),
                e.getChunkSize() != null ? e.getChunkSize() : 480,
                e.getChunkOverlap() != null ? e.getChunkOverlap() : 80,
                e.getDocCount() != null ? e.getDocCount() : 0,
                e.getChunkCount() != null ? e.getChunkCount() : 0,
                e.getStatus(), e.getRemark(), e.getUpdatedAt()
        );
    }

    private KnowledgeDocDTO toDocDto(KnowledgeDocEntity e) {
        return new KnowledgeDocDTO(
                e.getId(), e.getKbId(), e.getDocName(), e.getFileType(),
                e.getFileSize() != null ? e.getFileSize() : 0L,
                e.getParseStatus(),
                e.getChunkCount() != null ? e.getChunkCount() : 0,
                e.getCharCount() != null ? e.getCharCount() : 0,
                e.getSummary(), e.getCreatedAt()
        );
    }

    private double[] parseVector(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ignored) {
            return null;
        }
    }

    private String toJsonSafe(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }

    private Long currentTenantId() {
        AuthPrincipal p = AuthContext.get();
        return p != null && p.tenantId() != null ? p.tenantId() : 1L;
    }

    private Long currentUserId() {
        AuthPrincipal p = AuthContext.get();
        return p != null && p.userId() != null ? p.userId() : 1L;
    }
}
