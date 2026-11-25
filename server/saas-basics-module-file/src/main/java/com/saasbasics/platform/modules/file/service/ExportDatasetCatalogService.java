package com.saasbasics.platform.modules.file.service;

import com.saasbasics.platform.common.auth.DataPermissionSqlSpec;
import com.saasbasics.platform.common.auth.DataPermissionContext;
import com.saasbasics.platform.common.auth.DataScope;
import com.saasbasics.platform.modules.file.mapper.ExportDatasetMapper;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.file.dto.ExportDtos.ExportDatasetInfoDTO;
import com.saasbasics.platform.modules.file.dto.ExportDtos.ExportFieldSpec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.springframework.core.annotation.AnnotationUtils;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/**
 * Curated & dynamic tenant-isolated dataset catalog for universal enterprise exports.
 */
@Service
public class ExportDatasetCatalogService {

    private static final Pattern SAFE_IDENT = Pattern.compile("^[a-z0-9_]{1,64}$");
    private static final Set<String> SENSITIVE_HINTS = Set.of(
            "mobile", "phone", "telephone", "contact_phone", "email", "id_card", "bank_card"
    );
    private static final Set<String> NO_DELETED_TABLES = Set.of(
            "audit_operation_log", "erp_inventory", "erp_inventory_record"
    );

    private final ExportDatasetMapper mapper;

    public ExportDatasetCatalogService(ExportDatasetMapper mapper) {
        this.mapper = mapper;
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
        return countRows(tenantId, sourceCode, filters, null);
    }

    public long countRows(Long tenantId, String sourceCode, Map<String, Object> filters, DataPermissionSqlSpec spec) {
        var query = buildQuery(tenantId, sourceCode, null, filters);
        return withRowScope(query.table(), spec, () -> mapper.countRows(query));
    }

    public void streamRows(Long tenantId, String sourceCode, List<String> selectedFields,
                           Map<String, Object> filters, int maxRows, Consumer<Map<String, Object>> rowConsumer) {
        streamRows(tenantId, sourceCode, selectedFields, filters, maxRows, null, rowConsumer);
    }

    public void streamRows(Long tenantId, String sourceCode, List<String> selectedFields,
                           Map<String, Object> filters, int maxRows, DataPermissionSqlSpec spec,
                           Consumer<Map<String, Object>> rowConsumer) {
        var query = buildQuery(tenantId, sourceCode, selectedFields, filters);
        withRowScope(query.table(), spec, () -> {
            mapper.streamRows(query, maxRows, result -> rowConsumer.accept(result.getResultObject()));
            return null;
        });
    }

    private <T> T withRowScope(String table, DataPermissionSqlSpec spec, Supplier<T> read) {
        var previousScope = DataPermissionContext.getDataScope();
        var previousSpec = DataPermissionContext.getSqlSpec();
        var scope = AnnotationUtils.synthesizeAnnotation(Map.of("orgColumn", "", "unitColumn", resolveUnitColumnForTable(table),
                "userColumn", resolveUserColumnForTable(table)), DataScope.class, ExportDatasetCatalogService.class);
        try {
            DataPermissionContext.setDataScope(scope);
            DataPermissionContext.setSqlSpec(spec == null ? previousSpec : spec);
            return read.get();
        } finally {
            DataPermissionContext.setDataScope(previousScope);
            DataPermissionContext.setSqlSpec(previousSpec);
        }
    }

    private static String resolveUnitColumnForTable(String physicalTable) {
        if (physicalTable == null) {
            return "";
        }
        String lower = physicalTable.toLowerCase(Locale.ROOT);
        if (lower.startsWith("biz_")) {
            return "dept_id";
        }
        return switch (lower) {
            case "crm_customer", "crm_clue", "crm_business", "crm_contract",
                 "crm_receivable", "crm_follow_up_record",
                 "erp_product", "erp_purchase_order", "erp_sale_order",
                 "erp_inventory_record", "iam_employee" -> "dept_id";
            default -> "";
        };
    }

    private static String resolveUserColumnForTable(String physicalTable) {
        if (physicalTable == null) {
            return "created_by";
        }
        return switch (physicalTable.toLowerCase(Locale.ROOT)) {
            case "crm_customer", "crm_clue", "crm_business" -> "owner_user_id";
            case "erp_inventory", "audit_operation_log" -> "";
            case "iam_user" -> "id";
            default -> "created_by";
        };
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
            if (spec == null) throw new BizException("EXPORT_FIELD_INVALID", "数据源不包含此导出字段");
            ordered.add(spec);
        }
        return ordered;
    }

    private ExportDatasetMapper.Query buildQuery(Long tenantId, String sourceCode, List<String> selectedFields,
                                                  Map<String, Object> filters) {
        ExportDatasetInfoDTO dataset = buildBuiltinDatasets().get(sourceCode);
        String table;
        if (dataset != null) {
            table = dataset.sourceCode();
        } else {
            if (sourceCode == null || !sourceCode.startsWith("dynamic:")) {
                throw new BizException("EXPORT_DATASET_NOT_FOUND", "不支持的导出数据源");
            }
            var model = mapper.publishedModel(tenantId, sanitizeIdent(sourceCode.substring(8)));
            if (model == null) throw new BizException("EXPORT_DATASET_NOT_FOUND", "租户没有已发布的导出模型");
            table = dynamicTable(tenantId, model.tableName());
            dataset = describeModel(tenantId, model);
        }
        List<ExportFieldSpec> selected = resolveSelectedFieldSpecs(dataset, selectedFields);
        var columns = selected.stream().map(f -> sanitizeIdent(f.fieldCode())).toList();
        var valid = dataset.fields().stream().map(ExportFieldSpec::fieldCode).collect(java.util.stream.Collectors.toSet());
        List<ExportDatasetMapper.Filter> equals = new ArrayList<>();
        String keyword = null;
        if (filters != null) {
            for (var filter : filters.entrySet()) {
                if (filter.getValue() == null || filter.getValue().toString().isBlank()) continue;
                if ("keyword".equals(filter.getKey())) keyword = "%" + filter.getValue().toString().trim() + "%";
                else if (valid.contains(filter.getKey())) equals.add(new ExportDatasetMapper.Filter(sanitizeIdent(filter.getKey()), filter.getValue()));
                else throw new BizException("EXPORT_FIELD_INVALID", "数据源不包含此筛选字段");
            }
        }
        var keywordColumns = selected.stream()
                .filter(f -> "VARCHAR".equalsIgnoreCase(f.dataType()) || "TEXT".equalsIgnoreCase(f.dataType()))
                .limit(4).map(ExportFieldSpec::fieldCode).toList();
        return new ExportDatasetMapper.Query(tenantId, table, !NO_DELETED_TABLES.contains(table),
                columns, keywordColumns, keyword, equals);
    }

    private static String dynamicTable(Long tenantId, String table) {
        sanitizeIdent(table);
        if (!table.startsWith("biz_dyn_") || (table.matches("biz_dyn_t[0-9]+_.*")
                && !table.startsWith("biz_dyn_t" + tenantId + "_"))) {
            throw new BizException("INVALID_EXPORT_IDENTIFIER", "动态导出表不属于当前租户的业务模型");
        }
        return table;
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
        for (var model : mapper.publishedModels(tenantId)) {
            dynamicTable(tenantId, model.tableName());
            var dataset = describeModel(tenantId, model);
            catalog.put(dataset.sourceCode(), dataset);
        }
    }

    private ExportDatasetInfoDTO describeModel(Long tenantId, ExportDatasetMapper.Model model) {
        List<ExportFieldSpec> fields = new ArrayList<>();
        fields.add(field("id", "记录ID", "BIGINT"));
        for (var column : mapper.modelFields(tenantId, model.id())) {
            fields.add(field(sanitizeIdent(column.fieldCode()), column.fieldName(), column.columnType()));
        }
        fields.add(field("created_at", "创建时间", "DATETIME"));
        return new ExportDatasetInfoDTO("dynamic:" + sanitizeIdent(model.modelCode()), model.modelName() + " (AI自建应用)",
                "DYNAMIC", fields);
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

        map.put("audit_operation_log", new ExportDatasetInfoDTO("audit_operation_log", "操作审计与风控轨迹日志", "AUDIT", List.of(
                field("id", "日志ID", "BIGINT"),
                field("trace_id", "追踪TraceId", "VARCHAR"),
                field("operator_name", "操作人", "VARCHAR"),
                field("biz_module", "业务模块", "VARCHAR"),
                field("biz_type", "业务类型", "VARCHAR"),
                field("biz_id", "业务对象ID", "VARCHAR"),
                field("operation_type", "操作动作", "VARCHAR"),
                field("request_uri", "请求路径", "VARCHAR"),
                field("http_method", "HTTP方法", "VARCHAR"),
                field("success", "是否成功", "TINYINT"),
                field("duration_ms", "耗时(ms)", "INT"),
                field("occurred_at", "操作时间", "DATETIME")
        )));

        map.put("erp_inventory", new ExportDatasetInfoDTO("erp_inventory", "ERP 实时仓库库存明细", "ERP", List.of(
                field("id", "库存ID", "BIGINT"),
                field("product_id", "商品ID", "BIGINT"),
                field("warehouse_id", "仓库ID", "BIGINT"),
                field("stock_quantity", "当前库存量", "INT"),
                field("locked_quantity", "锁定库存量", "INT"),
                field("updated_at", "更新时间", "DATETIME")
        )));

        return map;
    }

    private static ExportFieldSpec field(String code, String label, String dataType) {
        return new ExportFieldSpec(code, label, isSensitiveField(code), dataType);
    }

}
