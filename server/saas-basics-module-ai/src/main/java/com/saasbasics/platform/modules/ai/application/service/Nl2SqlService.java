package com.saasbasics.platform.modules.ai.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.auth.DataPermissionProvider;
import com.saasbasics.platform.common.auth.DataPermissionSqlSpec;
import com.saasbasics.platform.common.mask.DataMaskUtils;
import com.saasbasics.platform.modules.ai.api.AiGatewayFacade;
import com.saasbasics.platform.modules.ai.application.dto.AiCommandDtos.Nl2SqlQueryRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiCommandDtos.Nl2SqlQueryResponse;
import com.saasbasics.platform.modules.ai.application.dto.AiCommandDtos.Nl2SqlSchemaColumnDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiCommandDtos.Nl2SqlSchemaTableDTO;
import com.saasbasics.platform.modules.ai.domain.Nl2SqlSecurityValidator;
import com.saasbasics.platform.modules.ai.domain.Nl2SqlSecurityValidator.ValidatedSql;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiCommandHistoryEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.AiCommandHistoryMapper;
import com.saasbasics.platform.modules.audit.api.AuditRecorder;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Enterprise NL2SQL Analytics Engine:
 * translates natural language into tenant-isolated read-only SQL, validates via security policy,
 * executes safely, masks sensitive columns, recommends charts, and generates analytical insights.
 */
@Service
public class Nl2SqlService {

    private static final Logger log = LoggerFactory.getLogger(Nl2SqlService.class);
    private static final long MAX_EXPLAIN_ROWS = 100_000L;

    private final AiSchemaCatalogService schemaCatalogService;
    private final AiChatGatewayService chatGatewayService;
    private final ObjectProvider<AiCommandHistoryMapper> historyMapperProvider;
    private final ObjectProvider<AuditRecorder> auditRecorderProvider;
    private final ObjectProvider<DataPermissionProvider> dataPermissionProvider;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public Nl2SqlService(
            AiSchemaCatalogService schemaCatalogService,
            AiChatGatewayService chatGatewayService,
            ObjectProvider<AiCommandHistoryMapper> historyMapperProvider,
            ObjectProvider<AuditRecorder> auditRecorderProvider,
            ObjectProvider<DataPermissionProvider> dataPermissionProvider,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.schemaCatalogService = schemaCatalogService;
        this.chatGatewayService = chatGatewayService;
        this.historyMapperProvider = historyMapperProvider;
        this.auditRecorderProvider = auditRecorderProvider;
        this.dataPermissionProvider = dataPermissionProvider;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Nl2SqlQueryResponse executeNaturalQuery(Nl2SqlQueryRequest request) {
        long startedAt = System.currentTimeMillis();
        AuthPrincipal principal = AuthContext.get();
        Long tenantId = principal != null && principal.tenantId() != null ? principal.tenantId() : 1L;
        Long userId = principal != null && principal.userId() != null ? principal.userId() : 1L;
        String username = principal != null && principal.username() != null ? principal.username() : "system";

        String question = request.question() != null ? request.question().trim() : "";
        if (question.isEmpty()) {
            throw new IllegalArgumentException("分析问题不能为空");
        }

        Map<String, Nl2SqlSchemaTableDTO> schemaMap = schemaCatalogService.getSchemaMap(tenantId);
        Set<String> allowedTables = schemaMap.keySet();

        int maxRows = request.maxRows() != null ? Math.min(Math.max(1, request.maxRows()), 200) : 50;
        GeneratedPlan plan = tryGenerateViaLlm(question, request.modelId(), tenantId, allowedTables);
        String engineTag = "LLM_GATEWAY";
        if (plan == null) {
            plan = buildHeuristicPlan(question, tenantId, maxRows, schemaMap);
            engineTag = "SEMANTIC_ENGINE";
        }

        ValidatedSql validated = Nl2SqlSecurityValidator.validateAndNormalize(plan.sql(), allowedTables, tenantId);
        String executableSql = validated.sanitizedSql();
        boolean ownerScopedApplied = false;
        if (Nl2SqlSecurityValidator.containsOwnerScopedTable(validated.referencedTables()) && !isAdminUser(principal)) {
            Set<Long> allowedOwnerIds = resolveAllowedOwnerIds(principal, validated.referencedTables(), userId);
            executableSql = Nl2SqlSecurityValidator.applyOwnerScopePredicate(executableSql, tenantId, allowedOwnerIds);
            ownerScopedApplied = true;
        }
        verifyExplainGuard(executableSql);

        List<Map<String, Object>> rawRows = jdbcTemplate.queryForList(executableSql);
        Set<String> maskedFields = new LinkedHashSet<>();
        List<Map<String, Object>> maskedRows = maskSensitiveRows(rawRows, maskedFields);

        List<String> columns = extractColumns(maskedRows, rawRows);
        Map<String, String> columnLabels = resolveColumnLabels(columns, validated.referencedTables(), schemaMap);

        String chartType = normalizeChartType(plan.chartType(), columns, maskedRows);
        String xAxis = resolveXAxis(plan.xAxisField(), columns);
        String seriesField = resolveSeriesField(plan.seriesField(), columns, xAxis, maskedRows);

        List<String> insights = new ArrayList<>(buildInsights(question, maskedRows, plan.insights(), seriesField, xAxis, maskedFields));
        if (ownerScopedApplied) {
            insights.add("🔒 已自动叠加行级数据权限（owner_user_id）隔离策略，仅返回您授权范围内的数据。");
        }
        List<String> followUps = plan.followUps().isEmpty()
                ? defaultFollowUps(validated.referencedTables())
                : plan.followUps();

        long latencyMs = Math.max(1L, System.currentTimeMillis() - startedAt);

        recordHistory(tenantId, userId, username, question, executableSql,
                chartType, maskedRows.size(), latencyMs, "COMPLETED");
        recordAudit(question, executableSql, maskedRows.size(), true);

        return new Nl2SqlQueryResponse(
                question,
                validated.referencedTables(),
                executableSql,
                columns,
                columnLabels,
                maskedRows,
                maskedRows.size(),
                chartType,
                plan.chartTitle(),
                xAxis,
                seriesField,
                insights,
                followUps,
                new ArrayList<>(maskedFields),
                engineTag,
                latencyMs
        );
    }

    private GeneratedPlan tryGenerateViaLlm(String question, String modelId, Long tenantId, Set<String> allowedTables) {
        try {
            boolean hasLiveKey = chatGatewayService.getModels().stream()
                    .anyMatch(m -> Boolean.TRUE.equals(m.get("hasApiKey")));
            if (!hasLiveKey) {
                return null;
            }
            String schemas = schemaCatalogService.buildPromptSchemaDescription(tenantId);
            String prompt = "tenantId=" + tenantId + "\n可查询 Schema:\n" + schemas
                    + "\n请针对问题严格输出单个 JSON 对象（不要加 Markdown 代码块）:\n"
                    + "{\"sql\":\"SELECT ... WHERE tenant_id = " + tenantId + " ... LIMIT 50\","
                    + "\"chartType\":\"BAR|LINE|PIE|STAT|TABLE\","
                    + "\"chartTitle\":\"图表标题\","
                    + "\"xAxisField\":\"维度列名\","
                    + "\"seriesField\":\"指标数值列名\","
                    + "\"insights\":[\"洞察1\",\"洞察2\"],"
                    + "\"followUps\":[\"追问1\",\"追问2\"]}\n问题: " + question;

            AiGatewayFacade.ChatCompletionOutput output = chatGatewayService.complete(
                    new AiGatewayFacade.ChatCompletionCommand(
                            modelId,
                            "nl2sql-analyst",
                            List.of(new AiGatewayFacade.ChatMessage("user", prompt)),
                            0.1
                    )
            );
            String reply = stripJsonFences(output.reply());
            JsonNode node = objectMapper.readTree(reply);
            String sql = node.path("sql").asText("");
            if (sql.isBlank()) {
                return null;
            }
            // Pre-validate so broken LLM SQL falls back cleanly to heuristic engine
            Nl2SqlSecurityValidator.validateAndNormalize(sql, allowedTables, tenantId);
            List<String> insights = readStringList(node.path("insights"));
            List<String> followUps = readStringList(node.path("followUps"));
            return new GeneratedPlan(
                    sql,
                    node.path("chartType").asText("TABLE"),
                    node.path("chartTitle").asText("智能数据分析结果"),
                    node.path("xAxisField").asText(""),
                    node.path("seriesField").asText(""),
                    insights,
                    followUps
            );
        } catch (Exception ex) {
            log.debug("[NL2SQL] LLM generation skipped or failed validation, falling back cleanly: {}", ex.getMessage());
            return null;
        }
    }

    private GeneratedPlan buildHeuristicPlan(String question, Long tenantId, int maxRows,
                                             Map<String, Nl2SqlSchemaTableDTO> schemaMap) {
        String q = question.toLowerCase(Locale.ROOT);

        // 1. Check dynamically created AI apps first if user mentions their name or code
        for (Nl2SqlSchemaTableDTO tbl : schemaMap.values()) {
            if ("DYNAMIC".equals(tbl.domain())) {
                String labelClean = tbl.tableLabel().replace(" (AI自建)", "").toLowerCase(Locale.ROOT);
                String codeClean = tbl.tableName().replace("biz_dyn_", "");
                if (q.contains(labelClean) || q.contains(codeClean) || q.contains(tbl.tableName())) {
                    String sql = "SELECT * FROM `" + tbl.tableName() + "` WHERE tenant_id = " + tenantId
                            + " AND deleted = 0 ORDER BY id DESC LIMIT " + maxRows;
                    return new GeneratedPlan(sql, "TABLE", tbl.tableLabel() + " · 实时明细",
                            "id", "", List.of("已定位至 AI 自建动态应用表 `" + tbl.tableName() + "`。"),
                            List.of("导出「" + labelClean + "」全部记录到 Excel"));
                }
            }
        }

        // 2. 回款分析 (Receivables)
        if (q.contains("回款") || q.contains("收款") || q.contains("receivable") || q.contains("逾期")) {
            if (q.contains("状态") || q.contains("对比") || q.contains("汇总") || q.contains("率") || q.contains("统计")) {
                String sql = "SELECT status AS dimension, COUNT(*) AS record_count, COALESCE(SUM(price), 0) AS total_amount "
                        + "FROM crm_receivable WHERE tenant_id = " + tenantId + " AND deleted = 0 "
                        + "GROUP BY status ORDER BY total_amount DESC LIMIT " + maxRows;
                return new GeneratedPlan(sql, "PIE", "财务回款状态金额分布", "dimension", "total_amount",
                        List.of("按回款单状态统计笔数与金额，可直观评估已回款与待回款比例。"),
                        List.of("查看所有待回款订单明细", "按客户查看商务合同金额排名"));
            }
            String sql = "SELECT receivable_no, customer_name, contract_name, price, return_time, pay_type, status "
                    + "FROM crm_receivable WHERE tenant_id = " + tenantId + " AND deleted = 0 "
                    + "ORDER BY price DESC, id DESC LIMIT " + maxRows;
            return new GeneratedPlan(sql, "BAR", "财务回款单明细（按金额降序）", "customer_name", "price",
                    List.of("已按回款金额降序提取最新回款明细。"),
                    List.of("统计回款各状态占比", "查看商务合同履约列表"));
        }

        // 3. 商务合同 (Contracts)
        if (q.contains("合同") || q.contains("contract") || q.contains("到期")) {
            if (q.contains("统计") || q.contains("状态") || q.contains("分布")) {
                String sql = "SELECT status AS dimension, COUNT(*) AS contract_count, COALESCE(SUM(total_price), 0) AS total_amount "
                        + "FROM crm_contract WHERE tenant_id = " + tenantId + " AND deleted = 0 "
                        + "GROUP BY status ORDER BY total_amount DESC LIMIT " + maxRows;
                return new GeneratedPlan(sql, "BAR", "商务合同状态与金额统计", "dimension", "total_amount",
                        List.of("已按合同审批/签署状态汇总合同数与累计标的额。"),
                        List.of("列出合同金额最高的 Top 客户", "统计回款完成率"));
            }
            String sql = "SELECT contract_no, name, customer_name, total_price, start_time, end_time, status, signer_user_name "
                    + "FROM crm_contract WHERE tenant_id = " + tenantId + " AND deleted = 0 "
                    + "ORDER BY total_price DESC, id DESC LIMIT " + maxRows;
            return new GeneratedPlan(sql, "BAR", "商务合同金额排行分析", "customer_name", "total_price",
                    List.of("已按合同金额降序呈现重点合同台账与到期时间窗口。"),
                    List.of("统计各状态合同金额汇总", "查询关联财务回款进度"));
        }

        // 4. 商机与销售漏斗 (Business / Opportunities)
        if (q.contains("商机") || q.contains("漏斗") || q.contains("赢单") || q.contains("business")) {
            if (q.contains("阶段") || q.contains("漏斗") || q.contains("统计") || q.contains("汇总") || q.contains("分布")) {
                String sql = "SELECT stage AS dimension, COUNT(*) AS deal_count, COALESCE(SUM(total_price), 0) AS expected_amount, "
                        + "ROUND(AVG(win_rate), 1) AS avg_win_rate "
                        + "FROM crm_business WHERE tenant_id = " + tenantId + " AND deleted = 0 "
                        + "GROUP BY stage ORDER BY expected_amount DESC LIMIT " + maxRows;
                return new GeneratedPlan(sql, "BAR", "销售商机阶段漏斗金额分析", "dimension", "expected_amount",
                        List.of("按商机推进阶段聚合单量、预估总额与平均赢率，精准定位转化瓶颈。"),
                        List.of("查看预计成交额最高的商机明细", "统计客户行业分布"));
            }
            String sql = "SELECT business_code, name, customer_name, total_price, stage, win_rate, deal_date, owner_user_name "
                    + "FROM crm_business WHERE tenant_id = " + tenantId + " AND deleted = 0 "
                    + "ORDER BY total_price DESC LIMIT " + maxRows;
            return new GeneratedPlan(sql, "BAR", "重点商机金额及赢率排行", "customer_name", "total_price",
                    List.of("按预估商机金额降序排列高价值跟进机会。"),
                    List.of("按商机阶段汇总转化漏斗", "查询所有 A 级核心客户"));
        }

        // 5. 销售线索 (Clues)
        if (q.contains("线索") || q.contains("clue")) {
            if (q.contains("统计") || q.contains("来源") || q.contains("状态")) {
                String sql = "SELECT source AS dimension, COUNT(*) AS clue_count "
                        + "FROM crm_clue WHERE tenant_id = " + tenantId + " AND deleted = 0 "
                        + "GROUP BY source ORDER BY clue_count DESC LIMIT " + maxRows;
                return new GeneratedPlan(sql, "PIE", "销售线索渠道来源分布", "dimension", "clue_count",
                        List.of("各渠道线索数量统计已生成（明细敏感联系方式受脱敏保护）。"),
                        List.of("查看全部线索列表", "查看客户转化公海池情况"));
            }
            String sql = "SELECT id, name, contact_name, mobile, source, status, owner_user_name, created_at "
                    + "FROM crm_clue WHERE tenant_id = " + tenantId + " AND deleted = 0 ORDER BY id DESC LIMIT " + maxRows;
            return new GeneratedPlan(sql, "TABLE", "最新销售线索台账（已自动脱敏）", "source", "",
                    List.of("线索联系人手机号已自动启用隐私脱敏。"),
                    List.of("按来源渠道统计线索分布", "查看高意向商机漏斗"));
        }

        // 6. ERP 商品与库存 / 订单
        if (q.contains("库存") || q.contains("inventory") || q.contains("商品") || q.contains("物料") || q.contains("product")) {
            if (q.contains("库存") || q.contains("warehouse")) {
                String sql = "SELECT p.product_code, p.product_name, p.category, COALESCE(SUM(i.stock_quantity), 0) AS total_stock, "
                        + "p.min_stock, p.sale_price FROM erp_product p "
                        + "LEFT JOIN erp_inventory i ON i.product_id = p.id AND i.tenant_id = " + tenantId + " "
                        + "WHERE p.tenant_id = " + tenantId + " AND p.deleted = 0 "
                        + "GROUP BY p.id, p.product_code, p.product_name, p.category, p.min_stock, p.sale_price "
                        + "ORDER BY total_stock DESC LIMIT " + maxRows;
                return new GeneratedPlan(sql, "BAR", "商品实时库存水位 vs 安全阈值", "product_name", "total_stock",
                        List.of("关联物料档案与多仓库存聚合统计完成，可对照安全库存阈值识别缺货风险。"),
                        List.of("查看销售订单营收概览", "查看采购订单状态"));
            }
            String sql = "SELECT product_code, product_name, category, specification, purchase_price, sale_price, min_stock, status "
                    + "FROM erp_product WHERE tenant_id = " + tenantId + " AND deleted = 0 ORDER BY sale_price DESC LIMIT " + maxRows;
            return new GeneratedPlan(sql, "BAR", "ERP 核心商品售价结构分析", "product_name", "sale_price",
                    List.of("按商品销售单价降序排列物料价目清单。"),
                    List.of("查询各商品当前总库存水位", "查看近期销售订单金额"));
        }

        if (q.contains("采购") || q.contains("purchase")) {
            String sql = "SELECT order_no, supplier_name, total_amount, status, created_at "
                    + "FROM erp_purchase_order WHERE tenant_id = " + tenantId + " AND deleted = 0 "
                    + "ORDER BY id DESC LIMIT " + maxRows;
            return new GeneratedPlan(sql, "BAR", "ERP 采购订单履约分析", "supplier_name", "total_amount",
                    List.of("采购订单按时间倒序采集完成。"),
                    List.of("查看物料安全库存", "统计销售订单情况"));
        }

        if (q.contains("销售订单") || q.contains("订单") || q.contains("sale")) {
            String sql = "SELECT order_no, customer_name, total_amount, status, created_at "
                    + "FROM erp_sale_order WHERE tenant_id = " + tenantId + " AND deleted = 0 "
                    + "ORDER BY total_amount DESC, id DESC LIMIT " + maxRows;
            return new GeneratedPlan(sql, "BAR", "ERP 销售订单客户贡献分析", "customer_name", "total_amount",
                    List.of("销售订单按金额降序统计完成。"),
                    List.of("统计商务合同签署情况", "查看商品实时库存"));
        }

        // 7. 审计、导出与用户
        if (q.contains("导出") || q.contains("下载") || q.contains("export")) {
            String sql = "SELECT task_no, task_name, source_code, export_format, total_rows, status, operator_name, created_at "
                    + "FROM sys_export_task WHERE tenant_id = " + tenantId + " AND deleted = 0 ORDER BY id DESC LIMIT " + maxRows;
            return new GeneratedPlan(sql, "TABLE", "数据导出与下载任务记录", "export_format", "",
                    List.of("导出任务均携带水印追溯码与 SHA-256 指纹校验。"),
                    List.of("查看系统操作审计记录", "统计客户行业分布"));
        }

        if (q.contains("审计") || q.contains("日志") || q.contains("audit")) {
            if (q.contains("统计") || q.contains("模块") || q.contains("分布")) {
                String sql = "SELECT biz_module AS dimension, COUNT(*) AS event_count, ROUND(AVG(duration_ms), 1) AS avg_ms "
                        + "FROM audit_operation_log WHERE tenant_id = " + tenantId + " "
                        + "GROUP BY biz_module ORDER BY event_count DESC LIMIT " + maxRows;
                return new GeneratedPlan(sql, "BAR", "各业务模块操作审计频次分析", "dimension", "event_count",
                        List.of("系统自动按业务模块汇总操作频次与平均耗时。"),
                        List.of("查看最近 20 条审计日志明细", "检查系统用户账号清单"));
            }
            String sql = "SELECT id, operator_name, biz_module, biz_type, operation_type, success, duration_ms, occurred_at "
                    + "FROM audit_operation_log WHERE tenant_id = " + tenantId + " ORDER BY id DESC LIMIT " + maxRows;
            return new GeneratedPlan(sql, "LINE", "最近业务操作审计耗时趋势", "operation_type", "duration_ms",
                    List.of("展示当前租户近期核心操作审计日志与接口耗时。"),
                    List.of("按业务模块统计审计操作占比"));
        }

        if (q.contains("用户") || q.contains("账号") || q.contains("员工") || q.contains("user")) {
            String sql = "SELECT id, user_code, username, nickname, user_type, status, mobile, email, last_login_at "
                    + "FROM iam_user WHERE tenant_id = " + tenantId + " AND deleted = 0 ORDER BY id ASC LIMIT " + maxRows;
            return new GeneratedPlan(sql, "TABLE", "当前租户账号台账（隐私字段已脱敏）", "username", "",
                    List.of("用户手机号与电子邮箱已自动按安全策略执行脱敏展示。"),
                    List.of("查询近期操作审计日志", "查询 CRM 客户档案"));
        }

        // 8. 默认 CRM 客户分析 (Industry / Pool / List)
        if (q.contains("行业") || q.contains("等级") || q.contains("统计") || q.contains("分布") || q.contains("多少")) {
            String sql = "SELECT industry AS dimension, COUNT(*) AS customer_count, SUM(deal_status) AS deal_count "
                    + "FROM crm_customer WHERE tenant_id = " + tenantId + " AND deleted = 0 "
                    + "GROUP BY industry ORDER BY customer_count DESC LIMIT " + maxRows;
            return new GeneratedPlan(sql, "PIE", "CRM 客户行业分布与成交数统计", "dimension", "customer_count",
                    List.of("按客户所属行业维度聚合客户规模与已成交数。"),
                    List.of("查看公海池待认领客户", "分析商机漏斗各阶段预计金额"));
        }

        String poolFilter = q.contains("公海") ? " AND is_pool = 1 " : "";
        String levelFilter = (q.contains("a级") || q.contains("核心")) ? " AND level = 'A' " : "";
        String sql = "SELECT customer_code, name, industry, level, mobile, email, deal_status, is_pool, owner_user_name, created_at "
                + "FROM crm_customer WHERE tenant_id = " + tenantId + " AND deleted = 0"
                + poolFilter + levelFilter + " ORDER BY id DESC LIMIT " + maxRows;
        return new GeneratedPlan(sql, "TABLE", q.contains("公海") ? "CRM 公海池客户清单" : "CRM 客户档案全景清单",
                "industry", "",
                List.of("敏感联系方式（手机/邮箱）已由平台数据安全策略自动脱敏。"),
                List.of("统计各行业客户分布", "分析销售商机阶段金额漏斗", "查看财务回款完成率"));
    }

    private void verifyExplainGuard(String sql) {
        try {
            List<Map<String, Object>> planRows = jdbcTemplate.queryForList("EXPLAIN " + sql);
            for (Map<String, Object> row : planRows) {
                Object estRowsObj = row.get("rows");
                if (estRowsObj instanceof Number num && num.longValue() > MAX_EXPLAIN_ROWS) {
                    throw new SecurityException("查询预估扫描行数过大(" + num.longValue() + ")，请增加筛选条件以保护数据库性能");
                }
            }
        } catch (SecurityException secEx) {
            throw secEx;
        } catch (Exception ex) {
            log.debug("[NL2SQL] EXPLAIN check bypassed for non-MySQL/mock driver: {}", ex.getMessage());
        }
    }

    private List<Map<String, Object>> maskSensitiveRows(List<Map<String, Object>> rawRows, Set<String> maskedFields) {
        if (rawRows == null || rawRows.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>(rawRows.size());
        for (Map<String, Object> row : rawRows) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String col = entry.getKey();
                Object val = entry.getValue();
                if (val instanceof String strVal && !strVal.isBlank() && AiSchemaCatalogService.isSensitiveColumn(col)) {
                    String lower = col.toLowerCase(Locale.ROOT);
                    String masked;
                    if (lower.contains("email")) {
                        masked = DataMaskUtils.maskEmail(strVal);
                    } else if (lower.contains("id_card")) {
                        masked = DataMaskUtils.maskIdCard(strVal);
                    } else if (lower.contains("bank_card")) {
                        masked = DataMaskUtils.maskBankCard(strVal);
                    } else {
                        masked = DataMaskUtils.maskMobile(strVal);
                    }
                    maskedFields.add(col);
                    copy.put(col, masked);
                } else {
                    copy.put(col, val);
                }
            }
            result.add(copy);
        }
        return result;
    }

    private List<String> extractColumns(List<Map<String, Object>> rows, List<Map<String, Object>> fallbackRows) {
        List<Map<String, Object>> src = !rows.isEmpty() ? rows : fallbackRows;
        if (src == null || src.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(src.get(0).keySet());
    }

    private Map<String, String> resolveColumnLabels(List<String> columns, List<String> tableNames,
                                                    Map<String, Nl2SqlSchemaTableDTO> schemaMap) {
        Map<String, String> dict = new LinkedHashMap<>();
        dict.put("dimension", "统计维度");
        dict.put("record_count", "记录数");
        dict.put("total_amount", "总金额");
        dict.put("expected_amount", "预计金额");
        dict.put("deal_count", "商机数");
        dict.put("avg_win_rate", "平均赢率(%)");
        dict.put("contract_count", "合同数");
        dict.put("clue_count", "线索数");
        dict.put("total_stock", "总库存");
        dict.put("event_count", "事件数");
        dict.put("avg_ms", "平均耗时(ms)");
        dict.put("customer_count", "客户数");

        for (String t : tableNames) {
            Nl2SqlSchemaTableDTO tbl = schemaMap.get(t);
            if (tbl != null) {
                for (Nl2SqlSchemaColumnDTO c : tbl.columns()) {
                    dict.putIfAbsent(c.columnCode(), c.columnLabel());
                }
            }
        }
        Map<String, String> resolved = new LinkedHashMap<>();
        for (String col : columns) {
            resolved.put(col, dict.getOrDefault(col, col));
        }
        return resolved;
    }

    private String normalizeChartType(String proposed, List<String> cols, List<Map<String, Object>> rows) {
        if (rows.isEmpty() || cols.size() < 2) {
            return "TABLE";
        }
        if ("PIE".equalsIgnoreCase(proposed) && rows.size() <= 8) {
            return "PIE";
        }
        if ("LINE".equalsIgnoreCase(proposed)) {
            return "LINE";
        }
        if ("BAR".equalsIgnoreCase(proposed)) {
            return "BAR";
        }
        return "TABLE";
    }

    private String resolveXAxis(String preferred, List<String> cols) {
        if (preferred != null && !preferred.isBlank() && cols.contains(preferred)) {
            return preferred;
        }
        return cols.isEmpty() ? "" : cols.get(0);
    }

    private String resolveSeriesField(String preferred, List<String> cols, String xAxis, List<Map<String, Object>> rows) {
        if (preferred != null && !preferred.isBlank() && cols.contains(preferred)) {
            return preferred;
        }
        if (rows.isEmpty()) {
            return "";
        }
        Map<String, Object> first = rows.get(0);
        for (String col : cols) {
            if (!col.equals(xAxis) && first.get(col) instanceof Number) {
                return col;
            }
        }
        return "";
    }

    private List<String> buildInsights(String question, List<Map<String, Object>> rows, List<String> baseInsights,
                                       String seriesField, String xAxis, Set<String> maskedFields) {
        List<String> list = new ArrayList<>();
        if (rows.isEmpty()) {
            list.add("当前租户暂未命中匹配「" + question + "」的业务记录，建议切换筛选维度或先录入演示数据。");
            return list;
        }
        list.addAll(baseInsights);
        if (seriesField != null && !seriesField.isBlank()) {
            BigDecimal sum = BigDecimal.ZERO;
            String topDim = "";
            BigDecimal topVal = null;
            for (Map<String, Object> r : rows) {
                Object valObj = r.get(seriesField);
                if (valObj instanceof Number num) {
                    BigDecimal bd = new BigDecimal(num.toString());
                    sum = sum.add(bd);
                    if (topVal == null || bd.compareTo(topVal) > 0) {
                        topVal = bd;
                        topDim = String.valueOf(r.getOrDefault(xAxis, "-"));
                    }
                }
            }
            if (topVal != null) {
                BigDecimal avg = sum.divide(BigDecimal.valueOf(rows.size()), 2, RoundingMode.HALF_UP);
                list.add("汇总结果共 " + rows.size() + " 条，指标合计 " + sum.stripTrailingZeros().toPlainString()
                        + "（均值 " + avg.stripTrailingZeros().toPlainString() + "），首位贡献为「" + topDim + "」。");
            }
        } else {
            list.add("已安全检索并呈现 " + rows.size() + " 条租户隔离数据。");
        }
        if (!maskedFields.isEmpty()) {
            list.add("安全脱敏已生效，已自动遮掩敏感列：" + String.join(", ", maskedFields) + "。");
        }
        return list;
    }

    private List<String> defaultFollowUps(List<String> tables) {
        if (tables.stream().anyMatch(t -> t.startsWith("crm_"))) {
            return List.of("分析商机漏斗各阶段预计金额", "统计回款状态金额占比", "导出当前数据到 Excel（带水印）");
        }
        if (tables.stream().anyMatch(t -> t.startsWith("erp_"))) {
            return List.of("查询各商品实时库存水位", "查看近期销售订单客户排行");
        }
        return List.of("统计各行业 CRM 客户分布", "查看近期操作审计热点模块");
    }

    private void recordHistory(Long tenantId, Long userId, String username, String question, String sql,
                               String chartType, int rowCount, long latencyMs, String status) {
        AiCommandHistoryMapper mapper = historyMapperProvider.getIfAvailable();
        if (mapper == null) {
            return;
        }
        try {
            AiCommandHistoryEntity entity = new AiCommandHistoryEntity();
            entity.setTenantId(tenantId);
            entity.setUserId(userId);
            entity.setUsername(username);
            entity.setRawCommand(question);
            entity.setIntentCode("QUERY_DATA");
            entity.setConfidence(new BigDecimal("0.9600"));
            entity.setGeneratedSql(sql);
            entity.setResultSummary("chart=" + chartType + ", rows=" + rowCount);
            entity.setRowCount(rowCount);
            entity.setLatencyMs(latencyMs);
            entity.setStatus(status);
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
            entity.setDeleted(0);
            mapper.insert(entity);
        } catch (Exception ex) {
            log.debug("[NL2SQL] History record skipped: {}", ex.getMessage());
        }
    }

    private void recordAudit(String question, String sql, int rowCount, boolean success) {
        AuditRecorder recorder = auditRecorderProvider.getIfAvailable();
        if (recorder != null) {
            try {
                recorder.record("ai", "nl2sql_query", String.valueOf(Math.abs(question.hashCode())),
                        "NL2SQL_EXECUTE", question, "rows=" + rowCount + "; sql=" + sql, success);
            } catch (Exception ignored) {
            }
        }
    }

    private static String stripJsonFences(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }

    private static List<String> readStringList(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return List.of();
        }
        List<String> list = new ArrayList<>();
        arrayNode.forEach(item -> {
            String val = item.asText("");
            if (!val.isBlank()) {
                list.add(val);
            }
        });
        return list;
    }

    private static boolean isAdminUser(AuthPrincipal principal) {
        if (principal == null) {
            return true;
        }
        String userType = principal.userType();
        if ("SUPER_ADMIN".equalsIgnoreCase(userType) || "TENANT_ADMIN".equalsIgnoreCase(userType)) {
            return true;
        }
        if (principal.roleIds() != null && principal.roleIds().contains(1L)) {
            return true;
        }
        return principal.permissions() != null && principal.permissions().contains("*");
    }

    private Set<Long> resolveAllowedOwnerIds(AuthPrincipal principal, List<String> tables, Long userId) {
        Set<Long> ownerIds = new LinkedHashSet<>();
        DataPermissionProvider dp = dataPermissionProvider.getIfAvailable();
        if (dp != null) {
            String primaryTable = tables.stream()
                    .filter(t -> List.of("crm_customer", "crm_clue", "crm_business").contains(t))
                    .findFirst()
                    .orElse("crm_customer");
            String resourceCode = switch (primaryTable) {
                case "crm_clue" -> "crm:clue:query";
                case "crm_business" -> "crm:business:query";
                default -> "crm:customer:query";
            };
            try {
                DataPermissionSqlSpec spec = dp.resolveDataPermission(resourceCode);
                if (spec != null) {
                    if (spec.isAllowAll()) {
                        return Set.of();
                    }
                    if (spec.isDenyAll()) {
                        throw new SecurityException("当前账号数据权限受限，无法访问该明细数据");
                    }
                    if (spec.getUserIds() != null) {
                        ownerIds.addAll(spec.getUserIds());
                    }
                }
            } catch (SecurityException se) {
                throw se;
            } catch (Exception ignored) {
            }
        }
        if (ownerIds.isEmpty() && userId != null) {
            ownerIds.add(userId);
        }
        return ownerIds;
    }

    private record GeneratedPlan(
            String sql,
            String chartType,
            String chartTitle,
            String xAxisField,
            String seriesField,
            List<String> insights,
            List<String> followUps
    ) {}
}
