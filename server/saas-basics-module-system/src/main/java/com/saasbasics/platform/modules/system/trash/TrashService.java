package com.saasbasics.platform.modules.system.trash;

import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TrashService {

    private static final Logger log = LoggerFactory.getLogger(TrashService.class);
    private final JdbcTemplate jdbcTemplate;

    public record CascadeRule(String childTable, String foreignKeyColumn) {}

    public record TableMeta(
            String tableName,
            String appCode,
            String appName,
            String moduleName,
            String titleColumn,
            List<CascadeRule> cascadeRules
    ) {}

    private static final Map<String, TableMeta> PRESET_METAS = new LinkedHashMap<>();

    static {
        // CRM 模块
        PRESET_METAS.put("crm_customer", new TableMeta(
                "crm_customer", "crm", "客户关系 CRM", "客户档案", "name",
                List.of(new CascadeRule("crm_clue", "customer_id"), new CascadeRule("crm_business", "customer_id"))
        ));
        PRESET_METAS.put("crm_clue", new TableMeta(
                "crm_clue", "crm", "客户关系 CRM", "销售线索", "name", List.of()
        ));
        PRESET_METAS.put("crm_business", new TableMeta(
                "crm_business", "crm", "客户关系 CRM", "商机阶段", "name", List.of()
        ));
        PRESET_METAS.put("crm_contract", new TableMeta(
                "crm_contract", "crm", "客户关系 CRM", "商务合同", "title", List.of()
        ));
        PRESET_METAS.put("crm_receivable", new TableMeta(
                "crm_receivable", "crm", "客户关系 CRM", "财务回款", "receipt_no", List.of()
        ));

        // ERP 进销存
        PRESET_METAS.put("erp_product", new TableMeta(
                "erp_product", "erp", "进销存 ERP", "物料商品", "product_name", List.of()
        ));
        PRESET_METAS.put("erp_purchase_order", new TableMeta(
                "erp_purchase_order", "erp", "进销存 ERP", "采购订单", "order_no", List.of()
        ));
        PRESET_METAS.put("erp_sale_order", new TableMeta(
                "erp_sale_order", "erp", "进销存 ERP", "销售订单", "order_no", List.of()
        ));

        // 工作流与审批
        PRESET_METAS.put("wf_process_definition", new TableMeta(
                "wf_process_definition", "workflow", "工作流中心", "流程定义", "process_name",
                List.of(new CascadeRule("wf_process_instance", "process_definition_id"), new CascadeRule("wf_task", "process_definition_id"))
        ));
        PRESET_METAS.put("wf_process_instance", new TableMeta(
                "wf_process_instance", "workflow", "工作流中心", "流程实例", "title",
                List.of(new CascadeRule("wf_task", "instance_id"))
        ));

        // IAM 组织与权限
        PRESET_METAS.put("iam_user", new TableMeta(
                "iam_user", "iam", "身份与权限中心", "系统用户", "username",
                List.of(new CascadeRule("iam_user_role", "user_id"))
        ));
        PRESET_METAS.put("iam_role", new TableMeta(
                "iam_role", "iam", "身份与权限中心", "角色权限", "role_name",
                List.of(new CascadeRule("iam_role_menu", "role_id"))
        ));
        PRESET_METAS.put("iam_department", new TableMeta(
                "iam_department", "iam", "身份与权限中心", "部门机构", "dept_name", List.of()
        ));

        // 系统中心
        PRESET_METAS.put("sys_config", new TableMeta(
                "sys_config", "system", "系统中心", "参数配置", "name", List.of()
        ));
        PRESET_METAS.put("sys_dict_type", new TableMeta(
                "sys_dict_type", "system", "系统中心", "数据字典", "dict_name",
                List.of(new CascadeRule("sys_dict_item", "dict_type_id"))
        ));
        PRESET_METAS.put("sys_dict_item", new TableMeta(
                "sys_dict_item", "system", "系统中心", "字典明细", "item_label", List.of()
        ));
    }

    private final Map<String, TableMeta> dynamicMetas = new ConcurrentHashMap<>();

    public TrashService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private Map<String, TableMeta> getActiveRegistry() {
        Map<String, TableMeta> merged = new LinkedHashMap<>(PRESET_METAS);
        merged.putAll(dynamicMetas);
        return merged;
    }

    /**
     * 自动从数据库动态探测新增且包含 deleted 和 tenant_id 的物理表
     */
    public synchronized void discoverDynamicTables() {
        try {
            String sql = "SELECT DISTINCT TABLE_NAME FROM information_schema.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME = 'deleted' " +
                    "AND TABLE_NAME IN (" +
                    "  SELECT TABLE_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME = 'tenant_id'" +
                    ")";
            List<String> tables = jdbcTemplate.queryForList(sql, String.class);

            for (String table : tables) {
                if (PRESET_METAS.containsKey(table) || dynamicMetas.containsKey(table)) {
                    continue;
                }
                String appCode = resolveAppCode(table);
                String appName = resolveAppName(appCode);
                String titleCol = resolveTitleColumn(table);
                dynamicMetas.put(table, new TableMeta(
                        table, appCode, appName, "动态扩展: " + table, titleCol, List.of()
                ));
            }
        } catch (Exception e) {
            log.warn("动态探测软删除数据表失败: {}", e.getMessage());
        }
    }

    private String resolveAppCode(String tableName) {
        if (tableName.startsWith("crm_")) return "crm";
        if (tableName.startsWith("erp_")) return "erp";
        if (tableName.startsWith("wf_")) return "workflow";
        if (tableName.startsWith("iam_") || tableName.startsWith("org_")) return "iam";
        if (tableName.startsWith("sys_") || tableName.startsWith("system_")) return "system";
        if (tableName.startsWith("file_")) return "file";
        if (tableName.startsWith("sched_")) return "scheduler";
        if (tableName.startsWith("gen_")) return "codegen";
        return "other";
    }

    private String resolveAppName(String appCode) {
        return switch (appCode) {
            case "crm" -> "客户关系 CRM";
            case "erp" -> "进销存 ERP";
            case "workflow" -> "工作流中心";
            case "iam" -> "身份与权限中心";
            case "system" -> "系统中心";
            case "file" -> "文件中心";
            case "scheduler" -> "调度中心";
            case "codegen" -> "代码生成中心";
            default -> "其他应用";
        };
    }

    private String resolveTitleColumn(String table) {
        try {
            String sql = "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
            List<String> cols = jdbcTemplate.queryForList(sql, String.class, table);
            for (String cand : List.of("name", "title", "username", "product_name", "order_no", "code", "remark")) {
                if (cols.contains(cand)) return cand;
            }
        } catch (Exception ignored) {}
        return "id";
    }

    public List<TrashAppSummaryResponse> getAppSummaries() {
        AuthPrincipal principal = AuthContext.get();
        if (principal == null || principal.tenantId() == null) {
            return List.of();
        }
        discoverDynamicTables();
        Long tenantId = principal.tenantId();
        Map<String, TableMeta> registry = getActiveRegistry();

        Map<String, Long> appCounts = new LinkedHashMap<>();
        Map<String, String> appNames = new LinkedHashMap<>();
        long totalAll = 0;

        for (TableMeta meta : registry.values()) {
            try {
                String sql = String.format("SELECT COUNT(*) FROM %s WHERE tenant_id = ? AND deleted = 1", meta.tableName);
                Long count = jdbcTemplate.queryForObject(sql, Long.class, tenantId);
                long c = count != null ? count : 0;
                if (c > 0) {
                    appCounts.put(meta.appCode, appCounts.getOrDefault(meta.appCode, 0L) + c);
                    appNames.put(meta.appCode, meta.appName);
                    totalAll += c;
                }
            } catch (Exception ignored) {}
        }

        List<TrashAppSummaryResponse> list = new ArrayList<>();
        list.add(new TrashAppSummaryResponse("ALL", "全部应用", "Files", totalAll));

        for (Map.Entry<String, Long> e : appCounts.entrySet()) {
            list.add(new TrashAppSummaryResponse(
                    e.getKey(),
                    appNames.getOrDefault(e.getKey(), e.getKey()),
                    resolveAppIcon(e.getKey()),
                    e.getValue()
            ));
        }

        return list;
    }

    private String resolveAppIcon(String appCode) {
        return switch (appCode) {
            case "crm" -> "TrendCharts";
            case "erp" -> "Briefcase";
            case "workflow" -> "Tickets";
            case "iam" -> "User";
            case "system" -> "Setting";
            case "file" -> "Folder";
            default -> "Box";
        };
    }

    public List<TrashItemResponse> listTrash(String appCode, String tableName, String keyword) {
        AuthPrincipal principal = AuthContext.get();
        if (principal == null || principal.tenantId() == null) {
            return Collections.emptyList();
        }
        discoverDynamicTables();
        Long tenantId = principal.tenantId();
        List<TrashItemResponse> results = new ArrayList<>();
        Map<String, TableMeta> registry = getActiveRegistry();

        for (TableMeta meta : registry.values()) {
            if (appCode != null && !appCode.isBlank() && !"ALL".equalsIgnoreCase(appCode) && !appCode.equalsIgnoreCase(meta.appCode)) {
                continue;
            }
            if (tableName != null && !tableName.isBlank() && !tableName.equalsIgnoreCase(meta.tableName)) {
                continue;
            }

            try {
                String sql = String.format(
                        "SELECT id, %s AS title_col, deleted_at FROM %s " +
                                "WHERE tenant_id = ? AND deleted = 1 " +
                                "ORDER BY deleted_at DESC LIMIT 50",
                        meta.titleColumn, meta.tableName
                );

                List<TrashItemResponse> list = jdbcTemplate.query(sql, (rs, rowNum) -> {
                    long rid = rs.getLong("id");
                    String title = rs.getString("title_col");
                    Timestamp delTs = rs.getTimestamp("deleted_at");
                    return new TrashItemResponse(
                            meta.tableName + "_" + rid,
                            meta.appCode,
                            meta.appName,
                            meta.tableName,
                            meta.moduleName,
                            rid,
                            title != null ? title : ("#" + rid),
                            delTs != null ? delTs.toLocalDateTime() : null,
                            !meta.cascadeRules.isEmpty() ? "支持主子级联无损自愈还原" : "支持单记录自愈还原",
                            !meta.cascadeRules.isEmpty()
                    );
                }, tenantId);

                results.addAll(list);
            } catch (Exception ignored) {}
        }

        // 内存关键词过滤
        if (keyword != null && !keyword.isBlank()) {
            String lower = keyword.trim().toLowerCase();
            results = results.stream().filter(item ->
                    (item.title() != null && item.title().toLowerCase().contains(lower)) ||
                    (item.moduleName() != null && item.moduleName().toLowerCase().contains(lower)) ||
                    String.valueOf(item.recordId()).contains(lower) ||
                    item.tableName().toLowerCase().contains(lower)
            ).toList();
        }

        // 统一按删除时间倒序排列
        List<TrashItemResponse> sorted = new ArrayList<>(results);
        sorted.sort((a, b) -> {
            if (a.deletedAt() == null && b.deletedAt() == null) return 0;
            if (a.deletedAt() == null) return 1;
            if (b.deletedAt() == null) return -1;
            return b.deletedAt().compareTo(a.deletedAt());
        });

        return sorted;
    }

    @Transactional
    public TrashRestoreResponse restore(TrashActionRequest req) {
        AuthPrincipal principal = AuthContext.get();
        if (principal == null || principal.tenantId() == null) {
            throw new BizException("UNAUTHORIZED", "未授权访问");
        }

        Map<String, TableMeta> registry = getActiveRegistry();
        TableMeta meta = registry.get(req.tableName());
        if (meta == null) {
            throw new BizException("INVALID_TABLE", "不支持的回收站业务表: " + req.tableName());
        }

        // 1. 恢复主记录
        String sql = String.format(
                "UPDATE %s SET deleted = 0, deleted_at = NULL, updated_at = NOW() " +
                        "WHERE id = ? AND tenant_id = ? AND deleted = 1",
                meta.tableName
        );
        int mainRestored = jdbcTemplate.update(sql, req.recordId(), principal.tenantId());
        if (mainRestored == 0) {
            throw new BizException("NOT_FOUND", "未找到待还原的数据记录或该记录已被恢复/物理清除");
        }

        // 2. 级联恢复从表记录 (如果开启且存在规则)
        int cascadeTotal = 0;
        if (req.isCascade() && !meta.cascadeRules.isEmpty()) {
            for (CascadeRule rule : meta.cascadeRules) {
                try {
                    String cascadeSql = String.format(
                            "UPDATE %s SET deleted = 0, deleted_at = NULL, updated_at = NOW() " +
                                    "WHERE %s = ? AND tenant_id = ? AND deleted = 1",
                            rule.childTable, rule.foreignKeyColumn
                    );
                    int c = jdbcTemplate.update(cascadeSql, req.recordId(), principal.tenantId());
                    cascadeTotal += c;
                } catch (Exception e) {
                    log.warn("级联恢复从表 {} 失败: {}", rule.childTable, e.getMessage());
                }
            }
        }

        String msg = cascadeTotal > 0
                ? String.format("主数据记录还原成功，并已自动级联恢复关联的 %d 条业务从属数据！", cascadeTotal)
                : "主数据记录已成功无损还原至业务模块！";

        return new TrashRestoreResponse(meta.tableName, req.recordId(), mainRestored, cascadeTotal, msg);
    }

    @Transactional
    public void purge(TrashActionRequest req) {
        AuthPrincipal principal = AuthContext.get();
        if (principal == null || principal.tenantId() == null) {
            throw new BizException("UNAUTHORIZED", "未授权访问");
        }

        Map<String, TableMeta> registry = getActiveRegistry();
        TableMeta meta = registry.get(req.tableName());
        if (meta == null) {
            throw new BizException("INVALID_TABLE", "不支持的回收站业务表: " + req.tableName());
        }

        // 1. 级联物理粉碎从表记录
        if (!meta.cascadeRules.isEmpty()) {
            for (CascadeRule rule : meta.cascadeRules) {
                try {
                    String cascadeSql = String.format(
                            "DELETE FROM %s WHERE %s = ? AND tenant_id = ? AND deleted = 1",
                            rule.childTable, rule.foreignKeyColumn
                    );
                    jdbcTemplate.update(cascadeSql, req.recordId(), principal.tenantId());
                } catch (Exception e) {
                    log.warn("级联粉碎从表 {} 失败: {}", rule.childTable, e.getMessage());
                }
            }
        }

        // 2. 物理彻底删除主记录
        String sql = String.format(
                "DELETE FROM %s WHERE id = ? AND tenant_id = ? AND deleted = 1",
                meta.tableName
        );
        jdbcTemplate.update(sql, req.recordId(), principal.tenantId());
    }
}
