package com.saasbasics.platform.modules.ai.application.service;

import com.saasbasics.platform.modules.ai.application.dto.AiCommandDtos.Nl2SqlSchemaColumnDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiCommandDtos.Nl2SqlSchemaTableDTO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Curated and dynamic NL2SQL schema catalog scoped per tenant.
 */
@Service
public class AiSchemaCatalogService {

    private static final Logger log = LoggerFactory.getLogger(AiSchemaCatalogService.class);
    private static final Set<String> SENSITIVE_COLUMN_HINTS = Set.of(
            "mobile", "phone", "telephone", "contact_phone", "email", "id_card", "bank_card"
    );

    private final JdbcTemplate jdbcTemplate;

    public AiSchemaCatalogService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Nl2SqlSchemaTableDTO> listAllowedSchemas(Long tenantId) {
        Map<String, Nl2SqlSchemaTableDTO> catalog = new LinkedHashMap<>();
        registerBuiltinSchemas(catalog);
        registerDynamicSchemas(catalog, tenantId);
        return new ArrayList<>(catalog.values());
    }

    public Set<String> getAllowedTableNames(Long tenantId) {
        return listAllowedSchemas(tenantId).stream()
                .map(t -> t.tableName().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    public Map<String, Nl2SqlSchemaTableDTO> getSchemaMap(Long tenantId) {
        Map<String, Nl2SqlSchemaTableDTO> map = new LinkedHashMap<>();
        for (Nl2SqlSchemaTableDTO table : listAllowedSchemas(tenantId)) {
            map.put(table.tableName().toLowerCase(Locale.ROOT), table);
        }
        return map;
    }

    public String buildPromptSchemaDescription(Long tenantId) {
        StringBuilder sb = new StringBuilder();
        for (Nl2SqlSchemaTableDTO table : listAllowedSchemas(tenantId)) {
            sb.append("- ").append(table.tableName()).append(" (").append(table.tableLabel())
                    .append(", deletedCol=").append(table.hasDeletedColumn()).append("): ");
            String cols = table.columns().stream()
                    .map(c -> c.columnCode() + "[" + c.columnLabel() + ":" + c.dataType() + "]")
                    .collect(Collectors.joining(", "));
            sb.append(cols).append("\n");
        }
        return sb.toString();
    }

    public static boolean isSensitiveColumn(String columnName) {
        if (columnName == null || columnName.isBlank()) {
            return false;
        }
        String lower = columnName.toLowerCase(Locale.ROOT);
        if (SENSITIVE_COLUMN_HINTS.contains(lower)) {
            return true;
        }
        return lower.contains("phone") || lower.contains("mobile") || lower.contains("id_card")
                || lower.contains("bank_card") || lower.endsWith("_email") || "email".equals(lower);
    }

    private void registerBuiltinSchemas(Map<String, Nl2SqlSchemaTableDTO> catalog) {
        catalog.put("crm_customer", new Nl2SqlSchemaTableDTO("crm_customer", "CRM 客户档案", "CRM", true, List.of(
                col("id", "主键ID", "BIGINT"),
                col("customer_code", "客户编码", "VARCHAR"),
                col("name", "客户名称", "VARCHAR"),
                col("industry", "所属行业", "VARCHAR"),
                col("level", "客户等级(A/B/C)", "VARCHAR"),
                col("source", "来源渠道", "VARCHAR"),
                col("mobile", "手机号", "VARCHAR"),
                col("email", "邮箱", "VARCHAR"),
                col("deal_status", "成交状态(1已成交/0未成交)", "TINYINT"),
                col("is_pool", "是否公海池(1公海/0跟进中)", "TINYINT"),
                col("owner_user_name", "负责人", "VARCHAR"),
                col("created_at", "创建时间", "DATETIME")
        )));

        catalog.put("crm_clue", new Nl2SqlSchemaTableDTO("crm_clue", "CRM 销售线索", "CRM", true, List.of(
                col("id", "主键ID", "BIGINT"),
                col("name", "线索主题", "VARCHAR"),
                col("contact_name", "联系人", "VARCHAR"),
                col("mobile", "手机号", "VARCHAR"),
                col("source", "线索来源", "VARCHAR"),
                col("status", "跟进状态", "VARCHAR"),
                col("owner_user_name", "负责人", "VARCHAR"),
                col("created_at", "创建时间", "DATETIME")
        )));

        catalog.put("crm_business", new Nl2SqlSchemaTableDTO("crm_business", "CRM 商机漏斗", "CRM", true, List.of(
                col("id", "主键ID", "BIGINT"),
                col("business_code", "商机编码", "VARCHAR"),
                col("name", "商机名称", "VARCHAR"),
                col("customer_name", "关联客户", "VARCHAR"),
                col("total_price", "预计金额", "DECIMAL"),
                col("deal_date", "预计成交日", "DATE"),
                col("stage", "商机阶段", "VARCHAR"),
                col("win_rate", "赢率(%)", "INT"),
                col("owner_user_name", "负责人", "VARCHAR"),
                col("created_at", "创建时间", "DATETIME")
        )));

        catalog.put("crm_contract", new Nl2SqlSchemaTableDTO("crm_contract", "CRM 商务合同", "CRM", true, List.of(
                col("id", "主键ID", "BIGINT"),
                col("contract_no", "合同编号", "VARCHAR"),
                col("name", "合同名称", "VARCHAR"),
                col("customer_name", "客户名称", "VARCHAR"),
                col("total_price", "合同金额", "DECIMAL"),
                col("start_time", "生效日期", "DATE"),
                col("end_time", "到期日期", "DATE"),
                col("status", "合同状态", "VARCHAR"),
                col("signer_user_name", "签约人", "VARCHAR")
        )));

        catalog.put("crm_receivable", new Nl2SqlSchemaTableDTO("crm_receivable", "CRM 财务回款", "CRM", true, List.of(
                col("id", "主键ID", "BIGINT"),
                col("receivable_no", "回款单号", "VARCHAR"),
                col("contract_name", "关联合同", "VARCHAR"),
                col("customer_name", "客户名称", "VARCHAR"),
                col("price", "回款金额", "DECIMAL"),
                col("return_time", "回款日期", "DATE"),
                col("pay_type", "支付方式", "VARCHAR"),
                col("status", "回款状态(CONFIRMED/PENDING)", "VARCHAR")
        )));

        catalog.put("erp_product", new Nl2SqlSchemaTableDTO("erp_product", "ERP 商品物料档案", "ERP", true, List.of(
                col("id", "主键ID", "BIGINT"),
                col("product_code", "商品编码", "VARCHAR"),
                col("product_name", "商品名称", "VARCHAR"),
                col("category", "物料分类", "VARCHAR"),
                col("specification", "规格型号", "VARCHAR"),
                col("unit", "单位", "VARCHAR"),
                col("purchase_price", "采购成本价", "DECIMAL"),
                col("sale_price", "销售指导价", "DECIMAL"),
                col("min_stock", "安全库存阈值", "INT"),
                col("status", "状态", "VARCHAR")
        )));

        catalog.put("erp_inventory", new Nl2SqlSchemaTableDTO("erp_inventory", "ERP 实时库存水位", "ERP", false, List.of(
                col("id", "主键ID", "BIGINT"),
                col("product_id", "商品ID", "BIGINT"),
                col("warehouse_id", "仓库ID", "BIGINT"),
                col("stock_quantity", "当前库存量", "INT"),
                col("locked_quantity", "锁定库存量", "INT"),
                col("updated_at", "更新时间", "DATETIME")
        )));

        catalog.put("erp_sale_order", new Nl2SqlSchemaTableDTO("erp_sale_order", "ERP 销售订单", "ERP", true, List.of(
                col("id", "主键ID", "BIGINT"),
                col("order_no", "订单编号", "VARCHAR"),
                col("customer_name", "客户名称", "VARCHAR"),
                col("total_amount", "订单总金额", "DECIMAL"),
                col("status", "订单状态", "VARCHAR"),
                col("created_at", "下单时间", "DATETIME")
        )));

        catalog.put("erp_purchase_order", new Nl2SqlSchemaTableDTO("erp_purchase_order", "ERP 采购订单", "ERP", true, List.of(
                col("id", "主键ID", "BIGINT"),
                col("order_no", "采购单号", "VARCHAR"),
                col("supplier_name", "供应商名称", "VARCHAR"),
                col("total_amount", "采购金额", "DECIMAL"),
                col("status", "状态", "VARCHAR"),
                col("created_at", "创建时间", "DATETIME")
        )));

        catalog.put("iam_user", new Nl2SqlSchemaTableDTO("iam_user", "IAM 用户账号", "IAM", true, List.of(
                col("id", "用户ID", "BIGINT"),
                col("user_code", "用户编码", "VARCHAR"),
                col("username", "登录账号", "VARCHAR"),
                col("nickname", "用户昵称", "VARCHAR"),
                col("user_type", "用户类型", "VARCHAR"),
                col("status", "状态", "VARCHAR"),
                col("mobile", "手机号", "VARCHAR"),
                col("email", "电子邮箱", "VARCHAR"),
                col("last_login_at", "最近登录时间", "DATETIME")
        )));

        catalog.put("sys_export_task", new Nl2SqlSchemaTableDTO("sys_export_task", "数据导出任务记录", "EXPORT", true, List.of(
                col("id", "任务ID", "BIGINT"),
                col("task_no", "任务编号", "VARCHAR"),
                col("task_name", "导出名称", "VARCHAR"),
                col("source_code", "数据源编码", "VARCHAR"),
                col("export_format", "格式", "VARCHAR"),
                col("total_rows", "导出行数", "BIGINT"),
                col("status", "任务状态", "VARCHAR"),
                col("operator_name", "操作人", "VARCHAR"),
                col("created_at", "创建时间", "DATETIME")
        )));

        catalog.put("audit_operation_log", new Nl2SqlSchemaTableDTO("audit_operation_log", "系统操作审计日志", "AUDIT", false, List.of(
                col("id", "记录ID", "BIGINT"),
                col("operator_name", "操作人", "VARCHAR"),
                col("biz_module", "业务模块", "VARCHAR"),
                col("biz_type", "对象类型", "VARCHAR"),
                col("operation_type", "动作编码", "VARCHAR"),
                col("success", "是否成功", "TINYINT"),
                col("duration_ms", "耗时(ms)", "INT"),
                col("occurred_at", "发生时间", "DATETIME")
        )));
    }

    private void registerDynamicSchemas(Map<String, Nl2SqlSchemaTableDTO> catalog, Long tenantId) {
        try {
            List<Map<String, Object>> models = jdbcTemplate.queryForList(
                    "SELECT id, model_code, model_name, table_name FROM gen_dynamic_model WHERE tenant_id = ? AND deleted = 0 AND status = 'PUBLISHED'",
                    tenantId
            );
            for (Map<String, Object> m : models) {
                String tableName = String.valueOf(m.get("table_name")).toLowerCase(Locale.ROOT);
                String modelName = String.valueOf(m.get("model_name"));
                Long modelId = ((Number) m.get("id")).longValue();
                List<Map<String, Object>> fieldRows = jdbcTemplate.queryForList(
                        "SELECT field_code, field_name, column_type FROM gen_dynamic_field WHERE tenant_id = ? AND model_id = ? AND deleted = 0 ORDER BY sort_no ASC",
                        tenantId, modelId
                );
                List<Nl2SqlSchemaColumnDTO> cols = new ArrayList<>();
                cols.add(col("id", "记录ID", "BIGINT"));
                for (Map<String, Object> f : fieldRows) {
                    cols.add(col(
                            String.valueOf(f.get("field_code")),
                            String.valueOf(f.get("field_name")),
                            String.valueOf(f.get("column_type"))
                    ));
                }
                cols.add(col("created_at", "创建时间", "DATETIME"));
                catalog.put(tableName, new Nl2SqlSchemaTableDTO(tableName, modelName + " (AI自建)", "DYNAMIC", true, cols));
            }
        } catch (Exception ex) {
            log.debug("[AI SchemaCatalog] Dynamic model discovery skipped: {}", ex.getMessage());
        }
    }

    private static Nl2SqlSchemaColumnDTO col(String code, String label, String dataType) {
        return new Nl2SqlSchemaColumnDTO(code, label, dataType, isSensitiveColumn(code));
    }
}
