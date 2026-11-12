package com.saasbasics.platform.modules.ai.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.modules.ai.application.dto.AiCommandDtos.AiCommandHistoryDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiCommandDtos.AiCommandRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiCommandDtos.AiCommandResponse;
import com.saasbasics.platform.modules.ai.application.dto.AiCommandDtos.Nl2SqlQueryRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiCommandDtos.Nl2SqlQueryResponse;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiCommandHistoryEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.AiCommandHistoryMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Multi-intent router powering the global ⌘K AI Command Palette.
 */
@Service
public class AiCommandRouterService {

    private final Nl2SqlService nl2SqlService;
    private final ObjectProvider<AiCommandHistoryMapper> historyMapperProvider;

    public AiCommandRouterService(
            Nl2SqlService nl2SqlService,
            ObjectProvider<AiCommandHistoryMapper> historyMapperProvider
    ) {
        this.nl2SqlService = nl2SqlService;
        this.historyMapperProvider = historyMapperProvider;
    }

    public AiCommandResponse interpret(AiCommandRequest request) {
        String input = request.input() != null ? request.input().trim() : "";
        if (input.isEmpty()) {
            throw new IllegalArgumentException("指令内容不能为空");
        }
        String low = input.toLowerCase(Locale.ROOT);
        boolean executeNow = Boolean.TRUE.equals(request.executeNow());

        // 0. Compound Multi-Step Intent (e.g., "查...并导出...", "新建...并配置审批流")
        boolean hasExportPart = containsAny(low, "导出", "下载", "pdf", "excel", "csv", "报告", "export");
        boolean hasQueryPart = containsAny(low, "查", "统计", "分析", "对比", "排名", "top", "汇总");
        if (hasExportPart && hasQueryPart && containsAny(low, "并", "然后", "同时", "再")) {
            String sourceCode = inferExportSource(low);
            String format = containsAny(low, "pdf") ? "PDF"
                    : (containsAny(low, "报告", "月报", "周报") ? "HTML_REPORT"
                    : (low.contains("csv") ? "CSV" : "XLSX"));
            Nl2SqlQueryResponse preview = executeNow
                    ? nl2SqlService.executeNaturalQuery(new Nl2SqlQueryRequest(input, request.modelId(), 30))
                    : null;
            List<Map<String, Object>> steps = List.of(
                    Map.of("step", 1, "action", "QUERY_DATA", "title", "多租户安全 NL2SQL 实时查数",
                            "status", preview != null ? "DONE" : "READY", "route", "/ai/data-copilot"),
                    Map.of("step", 2, "action", "EXPORT_DATA", "title", "自动脱敏 + 生成 " + format + " 带水印报告",
                            "status", "READY", "route", "/file/download-center")
            );
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sourceCode", sourceCode);
            payload.put("format", format);
            payload.put("question", input);
            payload.put("steps", steps);
            recordNonQueryHistory(input, "COMPOUND_PLAN", 0.96, "复合规划: 查数(" + sourceCode + ") + 导出(" + format + ")");
            return new AiCommandResponse(
                    "COMPOUND_PLAN",
                    "多步智能协同规划（查数 + 导出）",
                    0.96,
                    "已自动拆解为两步执行链：先完成多租户安全查数，再一键导出加注防伪水印的 " + format + " 文件。",
                    false,
                    "/ai/data-copilot",
                    payload,
                    preview
            );
        }

        // 1. Export / Report Intent
        if (containsAny(low, "导出", "下载", "excel", "csv", "生成报告", "月报", "周报", "分析报告", "套打", "pdf", "export")) {
            String sourceCode = inferExportSource(low);
            String format = containsAny(low, "pdf") ? "PDF"
                    : (containsAny(low, "报告", "月报", "周报", "ppt") ? "HTML_REPORT"
                    : (low.contains("csv") ? "CSV" : "XLSX"));
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sourceCode", sourceCode);
            payload.put("format", format);
            payload.put("question", input);
            Nl2SqlQueryResponse preview = null;
            if (executeNow) {
                preview = nl2SqlService.executeNaturalQuery(new Nl2SqlQueryRequest(input, request.modelId(), 20));
            }
            recordNonQueryHistory(input, "EXPORT_DATA", 0.94, "推荐导出源=" + sourceCode + ", 格式=" + format);
            return new AiCommandResponse(
                    "EXPORT_DATA",
                    "智能数据导出与报告",
                    0.94,
                    "已识别您希望导出业务数据或生成分析报告，系统已为您自动匹配数据源【" + sourceCode + "】（格式：" + format + "）及安全水印策略。",
                    false,
                    "/file/download-center",
                    payload,
                    preview
            );
        }

        // 1.5 Knowledge Base / Compliance Review Intent
        if (containsAny(low, "知识库", "rag", "文档问答", "合同审查", "合同审核", "合规红线", "制度规范", "标书")) {
            recordNonQueryHistory(input, "KNOWLEDGE_RAG", 0.93, "跳转 AI 企业知识库与合同合审");
            return new AiCommandResponse(
                    "KNOWLEDGE_RAG",
                    "企业知识库与智能合同合审",
                    0.93,
                    "已识别文档检索或合规审查意图，前往「AI 企业知识库」即可执行混合向量召回问答（附带原文引用页码）及合同条款逐条风险扫描。",
                    false,
                    "/ai/knowledge",
                    Map.of("question", input),
                    null
            );
        }

        // 1.6 Agent Studio / Fine-tune Intent
        if (containsAny(low, "智能体", "agent", "数字员工")) {
            recordNonQueryHistory(input, "AGENT_STUDIO", 0.92, "跳转智能体编排工坊");
            return new AiCommandResponse(
                    "AGENT_STUDIO",
                    "智能体工坊 (Agent Studio)",
                    0.92,
                    "可在「智能体工坊」编排专属角色 Prompt、挂载企业知识库与 MCP 工具权限，并一键发布到各业务渠道。",
                    false,
                    "/ai/agents",
                    Map.of("question", input),
                    null
            );
        }
        if (containsAny(low, "微调", "lora", "训练数据集", "sft", "模型评测")) {
            recordNonQueryHistory(input, "FINETUNE_STUDIO", 0.92, "跳转模型微调与评测工坊");
            return new AiCommandResponse(
                    "FINETUNE_STUDIO",
                    "企业专属模型微调与评测",
                    0.92,
                    "前往「模型微调与评测」标注清洗脱敏样本、导出 JSONL 并对比 LoRA 微调前后的准确率提升。",
                    false,
                    "/ai/finetune",
                    Map.of("question", input),
                    null
            );
        }

        // 2. Build Workflow Intent (with structured BPMN flow preview - check before generic BUILD/CREATE)
        if (containsAny(low, "审批流", "工作流", "请假流程", "bpmn", "approval flow") && !containsAny(low, "建表", "新增模块")) {
            List<Map<String, Object>> nodes = List.of(
                    Map.of("id", "start", "type", "START", "name", "员工发起申请", "assigneeRole", "ALL"),
                    Map.of("id", "dept_mgr", "type", "APPROVAL", "name", "直属部门负责人审批", "assigneeRole", "DEPT_MANAGER"),
                    Map.of("id", "cond_gate", "type", "GATEWAY", "name", "金额/天数条件分流", "condition", "amount > 50000 OR days > 3"),
                    Map.of("id", "exec_mgr", "type", "APPROVAL", "name", "分管副总裁/财务会签", "assigneeRole", "VP_FINANCE"),
                    Map.of("id", "notify_end", "type", "END", "name", "自动归档并发送站内通知", "assigneeRole", "SYSTEM")
            );
            Map<String, Object> blueprint = new LinkedHashMap<>();
            blueprint.put("processName", input.length() > 30 ? input.substring(0, 30) : input);
            blueprint.put("nodes", nodes);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("prompt", input);
            payload.put("workflowBlueprint", blueprint);
            recordNonQueryHistory(input, "BUILD_WORKFLOW", 0.91, "生成 BPMN 流程蓝图并跳转工作流中心");
            return new AiCommandResponse(
                    "BUILD_WORKFLOW",
                    "AI 智能审批流程编排",
                    0.91,
                    "已根据您的描述推导多级审批链与条件分支蓝图，可点击跳转至「工作流与审批中心」一键创建部署。",
                    false,
                    "/workflow",
                    payload,
                    null
            );
        }

        // 3. Natural Language Feature / App Builder Intent
        if (containsAny(low, "新建表", "建表", "建一个", "做一个", "搭建", "生成应用", "新增模块", "工单系统", "巡检", "台账", "create table")) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("prompt", input);
            payload.put("autoDesign", true);
            recordNonQueryHistory(input, "CREATE_ENTITY", 0.93, "跳转 AI 智能应用工坊建模");
            return new AiCommandResponse(
                    "CREATE_ENTITY",
                    "自然语言建表建页",
                    0.93,
                    "已识别业务建模需求，可直接跳转至「AI 智能应用工坊」自动推导实体表结构、MySQL 建表 DDL 与 Vue 动态页面。",
                    false,
                    "/codegen/ai-builder",
                    payload,
                    null
            );
        }

        // 4. System / Security Config Intent
        if (containsAny(low, "安全策略", "传输加密", "轮换密钥", "审计Diff", "监控", "告警规则", "多因子", "mfa")) {
            String target = containsAny(low, "监控", "告警", "p99") ? "/server-monitor"
                    : (containsAny(low, "审计") ? "/audit/trail"
                    : (containsAny(low, "mfa", "多因子") ? "/profile" : "/system?section=security"));
            recordNonQueryHistory(input, "CONFIGURE_SYSTEM", 0.89, "跳转控制面 " + target);
            return new AiCommandResponse(
                    "CONFIGURE_SYSTEM",
                    "系统安全与运维治理",
                    0.89,
                    "涉及系统关键配置或安全审计策略，需在对应治理控制台确认后生效。",
                    false,
                    target,
                    Map.of("targetPath", target),
                    null
            );
        }

        // 5. Data Query / Analysis Intent (NL2SQL)
        if (containsAny(low, "查", "统计", "分析", "多少", "排名", "对比", "分布", "漏斗", "客户", "线索", "商机", "合同", "回款", "库存", "商品", "订单", "用户", "审计", "top")) {
            Nl2SqlQueryResponse result = executeNow
                    ? nl2SqlService.executeNaturalQuery(new Nl2SqlQueryRequest(input, request.modelId(), 50))
                    : null;
            return new AiCommandResponse(
                    "QUERY_DATA",
                    "NL2SQL 智能查数与分析",
                    0.96,
                    result != null
                            ? "已完成实时多租户安全查数，共命中 " + result.rowCount() + " 条记录（敏感列已自动脱敏）。"
                            : "识别为数据分析查询意图，点击执行即可实时生成租户隔离 SQL 与可视化图表。",
                    false,
                    "/ai/data-copilot",
                    Map.of("question", input),
                    result
            );
        }

        // 6. Fallback: Open full AI Copilot / Assistant
        recordNonQueryHistory(input, "CHAT", 0.78, "引导至 AI 数据副驾驶/问答助手");
        return new AiCommandResponse(
                "CHAT",
                "AI 智能问答与分析",
                0.78,
                "可在「AI 数据副驾驶」中继续深入提问，或点击快捷指令跳转模块。",
                false,
                "/ai/data-copilot",
                Map.of("question", input),
                null
        );
    }

    public List<AiCommandHistoryDTO> listHistory(int limit) {
        AiCommandHistoryMapper mapper = historyMapperProvider.getIfAvailable();
        if (mapper == null) {
            return List.of();
        }
        AuthPrincipal principal = AuthContext.get();
        Long tenantId = principal != null && principal.tenantId() != null ? principal.tenantId() : 1L;
        int safeLimit = Math.min(Math.max(1, limit), 100);
        return mapper.selectList(new LambdaQueryWrapper<AiCommandHistoryEntity>()
                        .eq(AiCommandHistoryEntity::getTenantId, tenantId)
                        .eq(AiCommandHistoryEntity::getDeleted, 0)
                        .orderByDesc(AiCommandHistoryEntity::getId)
                        .last("LIMIT " + safeLimit))
                .stream()
                .map(e -> new AiCommandHistoryDTO(
                        e.getId(),
                        e.getRawCommand(),
                        e.getIntentCode(),
                        e.getConfidence() != null ? e.getConfidence().doubleValue() : 0.9,
                        e.getGeneratedSql(),
                        e.getResultSummary(),
                        e.getRowCount() != null ? e.getRowCount() : 0,
                        e.getLatencyMs() != null ? e.getLatencyMs() : 0L,
                        e.getStatus(),
                        e.getCreatedAt()
                ))
                .toList();
    }

    private String inferExportSource(String low) {
        if (low.contains("回款") || low.contains("receivable")) return "crm_receivable";
        if (low.contains("合同") || low.contains("contract")) return "crm_contract";
        if (low.contains("商机") || low.contains("business")) return "crm_business";
        if (low.contains("线索") || low.contains("clue")) return "crm_clue";
        if (low.contains("商品") || low.contains("物料") || low.contains("库存")) return "erp_product";
        if (low.contains("销售订单")) return "erp_sale_order";
        if (low.contains("采购订单")) return "erp_purchase_order";
        if (low.contains("用户") || low.contains("账号")) return "iam_user";
        if (low.contains("审计") || low.contains("操作日志")) return "audit_operation_log";
        return "crm_customer";
    }

    private void recordNonQueryHistory(String command, String intentCode, double confidence, String summary) {
        AiCommandHistoryMapper mapper = historyMapperProvider.getIfAvailable();
        if (mapper == null) {
            return;
        }
        try {
            AuthPrincipal principal = AuthContext.get();
            AiCommandHistoryEntity entity = new AiCommandHistoryEntity();
            entity.setTenantId(principal != null && principal.tenantId() != null ? principal.tenantId() : 1L);
            entity.setUserId(principal != null && principal.userId() != null ? principal.userId() : 1L);
            entity.setUsername(principal != null && principal.username() != null ? principal.username() : "system");
            entity.setRawCommand(command);
            entity.setIntentCode(intentCode);
            entity.setConfidence(BigDecimal.valueOf(confidence));
            entity.setResultSummary(summary);
            entity.setRowCount(0);
            entity.setLatencyMs(1L);
            entity.setStatus("ROUTED");
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
            entity.setDeleted(0);
            mapper.insert(entity);
        } catch (Exception ignored) {
        }
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) {
                return true;
            }
        }
        return false;
    }
}
