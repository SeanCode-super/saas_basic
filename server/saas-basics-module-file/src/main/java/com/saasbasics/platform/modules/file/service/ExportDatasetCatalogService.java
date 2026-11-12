package com.saasbasics.platform.modules.file.service;

import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.file.dto.ExportDtos.ExportDatasetInfoDTO;
import com.saasbasics.platform.modules.file.dto.ExportDtos.ExportFieldSpec;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Curated & dynamic tenant-isolated dataset catalog for universal enterprise exports.
 */
@Service
public class ExportDatasetCatalogService {

    private static final Logger log = LoggerFactory.getLogger(ExportDatasetCatalogService.class);
    private static final Pattern SAFE_IDENT = Pattern.compile("^[a-z0-9_]{1,64}$");
    private static final Set<String> SENSITIVE_HINTS = Set.of(
            "mobile", "phone", "telephone", "contact_phone", "email", "id_card", "bank_card"
    );

    private final JdbcTemplate jdbcTemplate;

    public ExportDatasetCatalogService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ExportDatasetInfoDTO> listDatasets(Long tenantId) {
        Map<String, ExportDatasetInfoDTO> map = buildBuiltinDatasets();
        discoverDynamicDatasets(map, tenantId);
        return new ArrayList<>(map.values());
    }

    public ExportDatasetInfoDTO requireDataset(Long tenantId, String sourceCode) {
        Map<String, ExportDatasetInfoDTO> map = buildBuiltinDatasets();
        discoverDynamicDatasets(map, tenantId);
        ExportDatasetInfoDTO found = map.get(sourceCode);
        if (found == null) {
            throw new BizException("EXPORT_DATASET_NOT_FOUND", "不支持的导出数据源: " + sourceCode);
        }
        return found;
    }

    public long countRows(Long tenantId, String sourceCode, Map<String, Object> filters) {
        ResolvedDatasetQuery q = buildQuery(tenantId, sourceCode, null, filters);
        Long cnt = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + q.tableSql() + q.whereSql(), Long.class, q.args().toArray());
        return cnt != null ? cnt : 0L;
    }

    public void streamRows(Long tenantId, String sourceCode, List<String> selectedFields,
                           Map<String, Object> filters, int maxRows, Consumer<Map<String, Object>> rowConsumer) {
        ResolvedDatasetQuery q = buildQuery(tenantId, sourceCode, selectedFields, filters);
        String sql = "SELECT " + q.selectColsSql() + " FROM " + q.tableSql() + q.whereSql()
                + " ORDER BY id DESC" + (maxRows > 0 ? " LIMIT " + maxRows : "");
        jdbcTemplate.query(sql, ps -> {
            for (int i = 0; i < q.args().size(); i++) {
                ps.setObject(i + 1, q.args().get(i));
            }
        }, rs -> {
            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();
            Map<String, Object> row = new LinkedHashMap<>(colCount);
            for (int i = 1; i <= colCount; i++) {
                row.put(meta.getColumnLabel(i), rs.getObject(i));
            }
            rowConsumer.accept(row);
        });
    }

    public List<ExportFieldSpec> resolveSelectedFieldSpecs(ExportDatasetInfoDTO dataset, List<String> selectedFields) {
        if (selectedFields == null || selectedFields.isEmpty()) {
            return dataset.fields();
        }
        Map<String, ExportFieldSpec> byCode = new LinkedHashMap<>();
        for (ExportFieldSpec spec : dataset.fields()) {
            byCode.put(spec.fieldCode(), spec);
        }
        List<ExportFieldSpec> ordered = new ArrayList<>();
        for (String code : selectedFields) {
            ExportFieldSpec spec = byCode.get(code);
            if (spec != null) {
                ordered.add(spec);
            }
        }
        return ordered.isEmpty() ? dataset.fields() : ordered;
    }

    private ResolvedDatasetQuery buildQuery(Long tenantId, String sourceCode, List<String> selectedFields,
                                           Map<String, Object> filters) {
        ExportDatasetInfoDTO dataset = requireDataset(tenantId, sourceCode);
        String physicalTable = resolvePhysicalTable(tenantId, sourceCode);
        List<ExportFieldSpec> cols = resolveSelectedFieldSpecs(dataset, selectedFields);

        String selectCols = cols.stream()
                .map(c -> "`" + sanitizeIdent(c.fieldCode()) + "`")
                .collect(java.util.stream.Collectors.joining(", "));

        Set<String> validCols = dataset.fields().stream()
                .map(ExportFieldSpec::fieldCode)
                .collect(java.util.stream.Collectors.toSet());

        StringBuilder where = new StringBuilder(" WHERE tenant_id = ? AND deleted = 0 ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);

        if (filters != null && !filters.isEmpty()) {
            Object kwObj = filters.get("keyword");
            if (kwObj != null && !String.valueOf(kwObj).isBlank()) {
                String kw = "%" + String.valueOf(kwObj).trim() + "%";
                List<String> strCols = cols.stream()
                        .filter(c -> "VARCHAR".equalsIgnoreCase(c.dataType()) || "TEXT".equalsIgnoreCase(c.dataType()))
                        .limit(4)
                        .map(ExportFieldSpec::fieldCode)
                        .toList();
                if (!strCols.isEmpty()) {
                    where.append(" AND (");
                    for (int i = 0; i < strCols.size(); i++) {
                        if (i > 0) where.append(" OR ");
                        where.append("`").append(sanitizeIdent(strCols.get(i))).append("` LIKE ?");
                        args.add(kw);
                    }
                    where.append(") ");
                }
            }
            for (Map.Entry<String, Object> entry : filters.entrySet()) {
                String k = entry.getKey();
                Object v = entry.getValue();
                if ("keyword".equals(k) || v == null || String.valueOf(v).isBlank()) {
                    continue;
                }
                if (validCols.contains(k)) {
                    where.append(" AND `").append(sanitizeIdent(k)).append("` = ? ");
                    args.add(v);
                }
            }
        }

        return new ResolvedDatasetQuery("`" + physicalTable + "`", selectCols, where.toString(), args);
    }

    private String resolvePhysicalTable(Long tenantId, String sourceCode) {
        if (sourceCode != null && sourceCode.startsWith("dynamic:")) {
            String modelCode = sourceCode.substring("dynamic:".length()).trim().toLowerCase(Locale.ROOT);
            sanitizeIdent(modelCode);
            List<Map<String, Object>> found = jdbcTemplate.queryForList(
                    "SELECT table_name FROM gen_dynamic_model WHERE tenant_id = ? AND model_code = ? AND deleted = 0 LIMIT 1",
                    tenantId, modelCode
            );
            if (found.isEmpty()) {
                throw new BizException("DYNAMIC_MODEL_NOT_FOUND", "动态模型不存在: " + modelCode);
            }
            return sanitizeIdent(String.valueOf(found.get(0).get("table_name")).toLowerCase(Locale.ROOT));
        }
        return sanitizeIdent(sourceCode);
    }

    private static String sanitizeIdent(String ident) {
        if (ident == null || !SAFE_IDENT.matcher(ident).matches()) {
            throw new BizException("INVALID_EXPORT_IDENTIFIER", "非法数据源或字段标识: " + ident);
        }
        return ident;
    }

    public static boolean isSensitiveField(String code) {
        if (code == null) return false;
        String lower = code.toLowerCase(Locale.ROOT);
        return SENSITIVE_HINTS.contains(lower)
                || lower.contains("phone") || lower.contains("mobile")
                || lower.contains("id_card") || lower.contains("bank_card")
                || lower.endsWith("_email") || "email".equals(lower);
    }

    private void discoverDynamicDatasets(Map<String, ExportDatasetInfoDTO> catalog, Long tenantId) {
        try {
            List<Map<String, Object>> models = jdbcTemplate.queryForList(
                    "SELECT id, model_code, model_name FROM gen_dynamic_model WHERE tenant_id = ? AND deleted = 0 AND status = 'PUBLISHED' ORDER BY id DESC",
                    tenantId
            );
            for (Map<String, Object> m : models) {
                Long modelId = ((Number) m.get("id")).longValue();
                String modelCode = String.valueOf(m.get("model_code"));
                String modelName = String.valueOf(m.get("model_name"));
                List<Map<String, Object>> fieldRows = jdbcTemplate.queryForList(
                        "SELECT field_code, field_name, column_type FROM gen_dynamic_field WHERE tenant_id = ? AND model_id = ? AND deleted = 0 ORDER BY sort_no ASC",
                        tenantId, modelId
                );
                List<ExportFieldSpec> fields = new ArrayList<>();
                fields.add(field("id", "记录ID", "BIGINT"));
                for (Map<String, Object> f : fieldRows) {
                    String fc = String.valueOf(f.get("field_code"));
                    fields.add(field(fc, String.valueOf(f.get("field_name")), String.valueOf(f.get("column_type"))));
                }
                fields.add(field("created_at", "创建时间", "DATETIME"));
                catalog.put("dynamic:" + modelCode, new ExportDatasetInfoDTO(
                        "dynamic:" + modelCode,
                        modelName + " (AI自建应用)",
                        "DYNAMIC",
                        fields
                ));
            }
        } catch (Exception ex) {
            log.debug("[ExportCatalog] Dynamic model inspection skipped: {}", ex.getMessage());
        }
    }

    private Map<String, ExportDatasetInfoDTO> buildBuiltinDatasets() {
        Map<String, ExportDatasetInfoDTO> map = new LinkedHashMap<>();

        map.put("crm_customer", new ExportDatasetInfoDTO("crm_customer", "CRM 客户档案台账", "CRM", List.of(
                field("customer_code", "客户编码", "VARCHAR"),
                field("name", "客户名称", "VARCHAR"),
                field("industry", "所属行业", "VARCHAR"),
                field("level", "客户等级", "VARCHAR"),
                field("source", "来源渠道", "VARCHAR"),
                field("mobile", "联系电话", "VARCHAR"),
                field("email", "电子邮箱", "VARCHAR"),
                field("address", "联系地址", "VARCHAR"),
                field("deal_status", "成交状态", "TINYINT"),
                field("is_pool", "是否公海池", "TINYINT"),
                field("owner_user_name", "负责人", "VARCHAR"),
                field("remark", "备注", "VARCHAR"),
                field("created_at", "创建时间", "DATETIME")
        )));

        map.put("crm_clue", new ExportDatasetInfoDTO("crm_clue", "CRM 销售线索台账", "CRM", List.of(
                field("id", "线索ID", "BIGINT"),
                field("name", "线索主题", "VARCHAR"),
                field("contact_name", "联系人", "VARCHAR"),
                field("mobile", "手机号码", "VARCHAR"),
                field("email", "邮箱", "VARCHAR"),
                field("source", "来源渠道", "VARCHAR"),
                field("status", "跟进状态", "VARCHAR"),
                field("owner_user_name", "负责人", "VARCHAR"),
                field("remark", "备注", "VARCHAR"),
                field("created_at", "创建时间", "DATETIME")
        )));

        map.put("crm_business", new ExportDatasetInfoDTO("crm_business", "CRM 销售商机漏斗", "CRM", List.of(
                field("business_code", "商机编码", "VARCHAR"),
                field("name", "商机名称", "VARCHAR"),
                field("customer_name", "客户名称", "VARCHAR"),
                field("total_price", "预计金额", "DECIMAL"),
                field("stage", "商机阶段", "VARCHAR"),
                field("win_rate", "赢率(%)", "INT"),
                field("deal_date", "预计成交日", "DATE"),
                field("owner_user_name", "负责人", "VARCHAR"),
                field("created_at", "创建时间", "DATETIME")
        )));

        map.put("crm_contract", new ExportDatasetInfoDTO("crm_contract", "CRM 商务合同台账", "CRM", List.of(
                field("contract_no", "合同编号", "VARCHAR"),
                field("name", "合同名称", "VARCHAR"),
                field("customer_name", "客户名称", "VARCHAR"),
                field("total_price", "合同总额", "DECIMAL"),
                field("start_time", "生效日期", "DATE"),
                field("end_time", "到期日期", "DATE"),
                field("status", "合同状态", "VARCHAR"),
                field("signer_user_name", "签约人", "VARCHAR"),
                field("created_at", "创建时间", "DATETIME")
        )));

        map.put("crm_receivable", new ExportDatasetInfoDTO("crm_receivable", "CRM 财务回款明细", "CRM", List.of(
                field("receivable_no", "回款单号", "VARCHAR"),
                field("contract_name", "关联合同", "VARCHAR"),
                field("customer_name", "客户名称", "VARCHAR"),
                field("price", "回款金额", "DECIMAL"),
                field("return_time", "回款日期", "DATE"),
                field("pay_type", "支付方式", "VARCHAR"),
                field("status", "回款状态", "VARCHAR"),
                field("created_at", "创建时间", "DATETIME")
        )));

        map.put("erp_product", new ExportDatasetInfoDTO("erp_product", "ERP 商品物料档案", "ERP", List.of(
                field("product_code", "商品编码", "VARCHAR"),
                field("product_name", "商品名称", "VARCHAR"),
                field("category", "分类", "VARCHAR"),
                field("specification", "规格型号", "VARCHAR"),
                field("unit", "单位", "VARCHAR"),
                field("purchase_price", "采购成本价", "DECIMAL"),
                field("sale_price", "销售价", "DECIMAL"),
                field("min_stock", "安全库存", "INT"),
                field("status", "状态", "VARCHAR")
        )));

        map.put("erp_sale_order", new ExportDatasetInfoDTO("erp_sale_order", "ERP 销售订单台账", "ERP", List.of(
                field("order_no", "订单编号", "VARCHAR"),
                field("customer_name", "客户名称", "VARCHAR"),
                field("total_amount", "订单总额", "DECIMAL"),
                field("status", "状态", "VARCHAR"),
                field("remark", "备注", "VARCHAR"),
                field("created_at", "下单时间", "DATETIME")
        )));

        map.put("erp_purchase_order", new ExportDatasetInfoDTO("erp_purchase_order", "ERP 采购订单台账", "ERP", List.of(
                field("order_no", "采购单号", "VARCHAR"),
                field("supplier_name", "供应商名称", "VARCHAR"),
                field("total_amount", "采购金额", "DECIMAL"),
                field("status", "状态", "VARCHAR"),
                field("remark", "备注", "VARCHAR"),
                field("created_at", "创建时间", "DATETIME")
        )));

        map.put("iam_user", new ExportDatasetInfoDTO("iam_user", "IAM 企业用户台账", "IAM", List.of(
                field("user_code", "用户编码", "VARCHAR"),
                field("username", "登录账号", "VARCHAR"),
                field("nickname", "用户昵称", "VARCHAR"),
                field("user_type", "用户类型", "VARCHAR"),
                field("status", "状态", "VARCHAR"),
                field("mobile", "手机号码", "VARCHAR"),
                field("email", "电子邮箱", "VARCHAR"),
                field("last_login_at", "最近登录时间", "DATETIME")
        )));

        return map;
    }

    private static ExportFieldSpec field(String code, String label, String dataType) {
        return new ExportFieldSpec(code, label, isSensitiveField(code), dataType);
    }

    private record ResolvedDatasetQuery(String tableSql, String selectColsSql, String whereSql, List<Object> args) {}
}
