package com.saasbasics.platform.modules.ai.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.modules.ai.api.AiGatewayFacade;
import com.saasbasics.platform.modules.ai.application.dto.AiCommandDtos.Nl2SqlQueryRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiCommandDtos.Nl2SqlQueryResponse;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.AgentChatRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.AgentChatResponse;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.AiAgentDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.AiAgentSaveRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.AiToolOptionDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.KnowledgeCitationDTO;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiAgentEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiAgentSessionEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.AiAgentMapper;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.AiAgentSessionMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enterprise Visual Agent Studio Service:
 * orchestrates custom agent personas, knowledge base RAG bindings, MCP/platform tool whitelists,
 * multi-channel deployment configs, and live multi-turn sandbox execution.
 */
@Service
public class AgentStudioService {

    private final ObjectProvider<AiAgentMapper> agentMapperProvider;
    private final ObjectProvider<AiAgentSessionMapper> sessionMapperProvider;
    private final KnowledgeRagService knowledgeRagService;
    private final Nl2SqlService nl2SqlService;
    private final AiChatGatewayService chatGatewayService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AgentStudioService(
            ObjectProvider<AiAgentMapper> agentMapperProvider,
            ObjectProvider<AiAgentSessionMapper> sessionMapperProvider,
            KnowledgeRagService knowledgeRagService,
            Nl2SqlService nl2SqlService,
            AiChatGatewayService chatGatewayService,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.agentMapperProvider = agentMapperProvider;
        this.sessionMapperProvider = sessionMapperProvider;
        this.knowledgeRagService = knowledgeRagService;
        this.nl2SqlService = nl2SqlService;
        this.chatGatewayService = chatGatewayService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<AiAgentDTO> listAgents() {
        AiAgentMapper mapper = agentMapperProvider.getIfAvailable();
        if (mapper == null) return List.of();
        return mapper.selectList(new LambdaQueryWrapper<AiAgentEntity>()
                        .eq(AiAgentEntity::getTenantId, currentTenantId())
                        .eq(AiAgentEntity::getDeleted, 0)
                        .orderByDesc(AiAgentEntity::getId))
                .stream()
                .map(this::toAgentDto)
                .toList();
    }

    public List<AiToolOptionDTO> listToolCatalog() {
        return List.of(
                new AiToolOptionDTO("knowledge_search", "企业知识库混合检索 (RAG)", "KNOWLEDGE", "LOW",
                        "基于 BM25 + 向量 RRF 自动召回合规制度与原文页码引用"),
                new AiToolOptionDTO("nl2sql_query", "NL2SQL 多租户安全查数", "ANALYTICS", "LOW",
                        "自然语言转只读 SQL（自动租户隔离、PII 脱敏、EXPLAIN 限流）"),
                new AiToolOptionDTO("crm_query", "CRM 客户与合同摘要检索", "CRM", "LOW",
                        "查询当前客户历史合同金额、回款状况与跟进风险提示"),
                new AiToolOptionDTO("erp_inventory", "ERP 实时库存预警查询", "ERP", "LOW",
                        "核查仓库可用库存、锁定数量与安全库存阈值预警"),
                new AiToolOptionDTO("export_report", "带水印报表生成与投递", "EXPORT", "MEDIUM",
                        "一键汇总查询数据生成 XLSX / CSV / PDF 并附带追溯水印"),
                new AiToolOptionDTO("workflow_draft", "BPMN 审批流草稿规划", "WORKFLOW", "MEDIUM",
                        "根据业务条件分支自动生成多级审批节点蓝图")
        );
    }

    @Transactional
    public AiAgentDTO saveAgent(AiAgentSaveRequest req) {
        AiAgentMapper mapper = agentMapperProvider.getIfAvailable();
        if (mapper == null) throw new IllegalStateException("智能体服务不可用");
        if (req.agentName() == null || req.agentName().isBlank() || req.systemPrompt() == null || req.systemPrompt().isBlank()) {
            throw new IllegalArgumentException("智能体名称和人设提示词不能为空");
        }
        Long tenantId = currentTenantId();
        Long userId = currentUserId();
        AiAgentEntity entity = req.id() != null ? mapper.selectOne(new LambdaQueryWrapper<AiAgentEntity>()
                .eq(AiAgentEntity::getId, req.id())
                .eq(AiAgentEntity::getTenantId, tenantId)
                .eq(AiAgentEntity::getDeleted, 0)) : null;

        if (entity == null) {
            entity = new AiAgentEntity();
            entity.setTenantId(tenantId);
            entity.setAgentCode(req.agentCode() != null && !req.agentCode().isBlank()
                    ? req.agentCode().trim() : "agent_" + UUID.randomUUID().toString().substring(0, 8));
            entity.setRunCount(0);
            entity.setCreatedBy(userId);
            entity.setCreatedAt(LocalDateTime.now());
            entity.setDeleted(0);
        }
        entity.setAgentName(req.agentName().trim());
        entity.setAvatar(req.avatar() != null && !req.avatar().isBlank() ? req.avatar().trim() : "🤖");
        entity.setRoleTitle(req.roleTitle() != null && !req.roleTitle().isBlank() ? req.roleTitle().trim() : "企业专属数字员工");
        entity.setSystemPrompt(req.systemPrompt().trim());
        entity.setModelId(req.modelId() != null && !req.modelId().isBlank() ? req.modelId().trim() : "deepseek-chat");
        entity.setKbIdsJson(toJsonSafe(req.kbIds() != null ? req.kbIds() : List.of()));
        entity.setToolCodesJson(toJsonSafe(req.toolCodes() != null ? req.toolCodes() : List.of()));
        entity.setChannelTypes(req.channelTypes() != null && !req.channelTypes().isBlank()
                ? req.channelTypes().trim() : "CONSOLE,COMMAND_PALETTE");
        entity.setTemperature(BigDecimal.valueOf(req.temperature() != null ? req.temperature() : 0.30));
        entity.setWelcomeMessage(req.welcomeMessage());
        entity.setStatus(req.status() != null && !req.status().isBlank() ? req.status().trim().toUpperCase(Locale.ROOT) : "PUBLISHED");
        entity.setRemark(req.remark());
        entity.setUpdatedBy(userId);
        entity.setUpdatedAt(LocalDateTime.now());

        if (entity.getId() == null) mapper.insert(entity);
        else mapper.updateById(entity);
        return toAgentDto(entity);
    }

    @Transactional
    public void deleteAgent(Long id) {
        AiAgentMapper mapper = agentMapperProvider.getIfAvailable();
        if (mapper == null) return;
        AiAgentEntity entity = mapper.selectOne(new LambdaQueryWrapper<AiAgentEntity>()
                .eq(AiAgentEntity::getId, id)
                .eq(AiAgentEntity::getTenantId, currentTenantId())
                .eq(AiAgentEntity::getDeleted, 0));
        if (entity != null) {
            entity.setDeleted(1);
            entity.setDeletedAt(LocalDateTime.now());
            mapper.updateById(entity);
        }
    }

    @Transactional
    public AgentChatResponse chatWithAgent(AgentChatRequest req) {
        long start = System.currentTimeMillis();
        AiAgentMapper mapper = agentMapperProvider.getIfAvailable();
        if (mapper == null) throw new IllegalStateException("智能体服务不可用");
        if (req.agentId() == null || req.message() == null || req.message().isBlank()) {
            throw new IllegalArgumentException("请选择智能体并输入对话内容");
        }
        Long tenantId = currentTenantId();
        Long userId = currentUserId();
        AiAgentEntity agent = mapper.selectOne(new LambdaQueryWrapper<AiAgentEntity>()
                .eq(AiAgentEntity::getId, req.agentId())
                .eq(AiAgentEntity::getTenantId, tenantId)
                .eq(AiAgentEntity::getDeleted, 0));
        if (agent == null) {
            throw new IllegalArgumentException("智能体不存在");
        }

        String msg = req.message().trim();
        String low = msg.toLowerCase(Locale.ROOT);
        List<Long> kbIds = parseIdList(agent.getKbIdsJson());
        List<String> tools = parseStringList(agent.getToolCodesJson());
        List<String> toolTraces = new ArrayList<>();
        List<KnowledgeCitationDTO> citations = List.of();
        StringBuilder dynamicContext = new StringBuilder();

        // Step 1: Retrieve RAG context if KB bound
        if (!kbIds.isEmpty() || tools.contains("knowledge_search")) {
            citations = knowledgeRagService.hybridSearch(kbIds, msg, 3);
            if (!citations.isEmpty()) {
                toolTraces.add("knowledge_search -> 命中 " + citations.size() + " 条知识库原文切片");
                dynamicContext.append("【知识库引用】\n");
                for (KnowledgeCitationDTO c : citations) {
                    dynamicContext.append("[").append(c.citationNo()).append("] 《").append(c.docName())
                            .append("》 P").append(c.pageNo()).append(": ").append(c.excerpt()).append("\n");
                }
            }
        }

        // Step 2: Run safe read-only NL2SQL / CRM / ERP lookup when tool bound and user asks data question
        boolean asksData = containsAny(low, "客户", "合同", "回款", "商机", "库存", "采购", "订单", "多少", "排名", "统计", "金额");
        Nl2SqlQueryResponse nl2sqlOut = null;
        if (asksData && (tools.contains("nl2sql_query") || tools.contains("crm_query") || tools.contains("erp_inventory"))) {
            try {
                nl2sqlOut = nl2SqlService.executeNaturalQuery(new Nl2SqlQueryRequest(msg, agent.getModelId(), 10));
                toolTraces.add("nl2sql_query -> 检索 `" + String.join(",", nl2sqlOut.tableNames())
                        + "` (" + nl2sqlOut.rowCount() + " 行)");
                dynamicContext.append("【实时数据查询结果】SQL: ").append(nl2sqlOut.sql())
                        .append("，命中 ").append(nl2sqlOut.rowCount()).append(" 行，样例: ")
                        .append(toJsonSafe(nl2sqlOut.rows().stream().limit(5).toList())).append("\n");
            } catch (Exception ex) {
                toolTraces.add("nl2sql_query -> 跳过 (" + ex.getMessage() + ")");
            }
        }

        // Step 3: Call LLM if live key exists, else structured enterprise synthesis
        String reply = null;
        String engine = "AGENT_ORCHESTRATOR_LOCAL";
        boolean hasLiveKey = chatGatewayService.getModels().stream().anyMatch(m -> Boolean.TRUE.equals(m.get("hasApiKey")));
        if (hasLiveKey) {
            try {
                AiGatewayFacade.ChatCompletionOutput out = chatGatewayService.complete(
                        new AiGatewayFacade.ChatCompletionCommand(
                                agent.getModelId(),
                                null,
                                List.of(
                                        new AiGatewayFacade.ChatMessage("system", agent.getSystemPrompt()),
                                        new AiGatewayFacade.ChatMessage("user",
                                                dynamicContext + "\n用户问题: " + msg)
                                ),
                                agent.getTemperature() != null ? agent.getTemperature().doubleValue() : 0.3
                        )
                );
                if (out.reply() != null && !out.reply().isBlank()) {
                    reply = out.reply();
                    engine = "LLM_AGENT_RUNTIME";
                }
            } catch (Exception ignored) {
            }
        }

        if (reply == null) {
            reply = buildGroundedAgentReply(agent, msg, citations, nl2sqlOut, toolTraces);
        }

        agent.setRunCount((agent.getRunCount() != null ? agent.getRunCount() : 0) + 1);
        agent.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(agent);

        Long sessionId = persistSessionMessage(tenantId, userId, agent.getId(), req.sessionId(), msg, reply);
        return new AgentChatResponse(
                agent.getId(),
                sessionId,
                agent.getAgentName(),
                reply,
                engine,
                toolTraces,
                citations,
                Math.max(1L, System.currentTimeMillis() - start)
        );
    }

    /**
     * Inline CRM Copilot: synthesizes follow-up pitch & deal risk advice for a customer/business/contract.
     */
    public Map<String, Object> buildCrmFollowUpSuggestion(String bizType, Long bizId) {
        Long tenantId = currentTenantId();
        String targetName = "当前客户";
        List<Map<String, Object>> contracts = List.of();
        List<Map<String, Object>> followUps = List.of();
        try {
            if ("CUSTOMER".equalsIgnoreCase(bizType) && bizId != null) {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                        "SELECT name, level, industry, deal_status, remark FROM crm_customer WHERE id = ? AND tenant_id = ? AND deleted = 0 LIMIT 1",
                        bizId, tenantId);
                if (!rows.isEmpty()) {
                    targetName = String.valueOf(rows.get(0).get("name"));
                }
                contracts = jdbcTemplate.queryForList(
                        "SELECT contract_no, name, total_price, status, end_time FROM crm_contract WHERE customer_id = ? AND tenant_id = ? AND deleted = 0 ORDER BY id DESC LIMIT 5",
                        bizId, tenantId);
                followUps = jdbcTemplate.queryForList(
                        "SELECT follow_type, content, created_at FROM crm_follow_up_record WHERE biz_id = ? AND tenant_id = ? ORDER BY id DESC LIMIT 5",
                        bizId, tenantId);
            }
        } catch (Exception ignored) {
        }

        List<KnowledgeCitationDTO> policyRefs = knowledgeRagService.hybridSearch(List.of(), "合同 付款 首付 账期 违约", 2);
        String riskLevel = contracts.isEmpty() ? "MEDIUM" : "LOW";
        String riskSummary = contracts.isEmpty()
                ? "该客户暂无已签署有效合同，建议重点推进方案演示与试用落地，并确认决策人预算周期。"
                : "已关联 " + contracts.size() + " 份历史合同（最近跟进 " + followUps.size() + " 条），建议关注尾款账期（红线≤45个工作日）及续费节点。";
        String pitch = "【AI 推荐跟进话术 · " + targetName + "】\n"
                + "您好！结合贵司在数字化升级与降本增效上的诉求，我们整理了同行业标杆客户的上线收益对照（平均交付周期缩短 40%）。"
                + "本次报价方案严格遵循首付 30% + 里程碑验收节点结算机制，附件已附上安全合规白皮书，方便您与技术/法务团队并行评审。本周方便约 20 分钟做一次高管方案对齐吗？";

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("targetName", targetName);
        out.put("riskLevel", riskLevel);
        out.put("riskSummary", riskSummary);
        out.put("suggestedPitch", pitch);
        out.put("policyReferences", policyRefs);
        out.put("recentContractCount", contracts.size());
        return out;
    }

    private String buildGroundedAgentReply(
            AiAgentEntity agent,
            String msg,
            List<KnowledgeCitationDTO> citations,
            Nl2SqlQueryResponse nl2sqlOut,
            List<String> toolTraces
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append(agent.getAvatar()).append(" **").append(agent.getAgentName()).append("** 已为您处理请求：\n\n");
        if (nl2sqlOut != null && nl2sqlOut.rowCount() > 0) {
            sb.append("### 📊 实时业务数据联动\n");
            sb.append("- 查询范围：`").append(String.join(", ", nl2sqlOut.tableNames()))
                    .append("`（命中 **").append(nl2sqlOut.rowCount()).append("** 条安全脱敏记录）\n");
            if (!nl2sqlOut.insights().isEmpty()) {
                sb.append("- 关键洞察：").append(nl2sqlOut.insights().get(0)).append("\n\n");
            }
        }
        if (!citations.isEmpty()) {
            sb.append("### 📚 企业知识库依据\n");
            for (KnowledgeCitationDTO c : citations) {
                sb.append("- **[").append(c.citationNo()).append("] 《").append(c.docName())
                        .append("》 P").append(c.pageNo()).append("**：").append(c.excerpt()).append("\n");
            }
            sb.append("\n");
        }
        if (citations.isEmpty() && (nl2sqlOut == null || nl2sqlOut.rowCount() == 0)) {
            sb.append("基于角色设定（").append(agent.getRoleTitle()).append("），已收到您的指令：「")
                    .append(msg).append("」。当前共挂载 ")
                    .append(parseIdList(agent.getKbIdsJson()).size()).append(" 个知识库与 ")
                    .append(parseStringList(agent.getToolCodesJson()).size()).append(" 项平台工具。建议在提问中指定客户名称、合同条款或指标维度以获得精准数据与原文引用。");
        } else {
            sb.append("综合上述制度规范与实时经营数据，建议严格按红线标准推进，如需导出正式汇报文件可直接调用「AI 数据副驾驶」导出带水印 PDF。");
        }
        if (toolTraces.isEmpty()) {
            toolTraces.add("agent_persona -> " + agent.getAgentCode());
        }
        return sb.toString();
    }

    private Long persistSessionMessage(Long tenantId, Long userId, Long agentId, Long sessionId, String userMsg, String assistantMsg) {
        AiAgentSessionMapper sm = sessionMapperProvider.getIfAvailable();
        if (sm == null) return 0L;
        AiAgentSessionEntity sess = sessionId != null && sessionId > 0
                ? sm.selectOne(new LambdaQueryWrapper<AiAgentSessionEntity>()
                        .eq(AiAgentSessionEntity::getId, sessionId)
                        .eq(AiAgentSessionEntity::getTenantId, tenantId)
                        .eq(AiAgentSessionEntity::getDeleted, 0))
                : null;
        List<Map<String, String>> msgs = new ArrayList<>();
        if (sess != null && sess.getMessagesJson() != null) {
            try {
                msgs = objectMapper.readValue(sess.getMessagesJson(), new TypeReference<>() {});
            } catch (Exception ignored) {
            }
        }
        msgs.add(Map.of("role", "user", "content", userMsg));
        msgs.add(Map.of("role", "assistant", "content", assistantMsg));

        if (sess == null) {
            sess = new AiAgentSessionEntity();
            sess.setTenantId(tenantId);
            sess.setAgentId(agentId);
            sess.setUserId(userId);
            sess.setSessionTitle(userMsg.length() > 36 ? userMsg.substring(0, 36) : userMsg);
            sess.setCreatedBy(userId);
            sess.setCreatedAt(LocalDateTime.now());
            sess.setDeleted(0);
        }
        sess.setMessagesJson(toJsonSafe(msgs));
        sess.setLastActiveAt(LocalDateTime.now());
        sess.setUpdatedBy(userId);
        sess.setUpdatedAt(LocalDateTime.now());
        if (sess.getId() == null) sm.insert(sess);
        else sm.updateById(sess);
        return sess.getId();
    }

    private AiAgentDTO toAgentDto(AiAgentEntity e) {
        return new AiAgentDTO(
                e.getId(), e.getAgentCode(), e.getAgentName(), e.getAvatar(), e.getRoleTitle(),
                e.getSystemPrompt(), e.getModelId(), parseIdList(e.getKbIdsJson()), parseStringList(e.getToolCodesJson()),
                e.getChannelTypes(), e.getTemperature() != null ? e.getTemperature().doubleValue() : 0.3,
                e.getWelcomeMessage(), e.getStatus(), e.getRunCount() != null ? e.getRunCount() : 0, e.getRemark()
        );
    }

    private List<Long> parseIdList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String toJsonSafe(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }

    private static boolean containsAny(String s, String... kws) {
        for (String k : kws) if (s.contains(k)) return true;
        return false;
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
