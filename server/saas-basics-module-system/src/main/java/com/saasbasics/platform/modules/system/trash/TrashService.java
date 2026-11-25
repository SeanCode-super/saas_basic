package com.saasbasics.platform.modules.system.trash;

import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.file.TenantFileRestoreCapacity;
import com.saasbasics.platform.common.tenant.TenantMembershipCapacity;
import com.saasbasics.platform.modules.system.mapper.TrashMapper;
import com.saasbasics.platform.modules.system.trash.TrashTarget.TableMeta;
import com.saasbasics.platform.modules.system.trash.TrashTarget.CascadeRule;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrashService {
    private final TrashMapper mapper;
    private final TenantMembershipCapacity members;
    private final TenantFileRestoreCapacity files;

    public TrashService(TrashMapper mapper, TenantMembershipCapacity members, TenantFileRestoreCapacity files) {
        this.mapper = mapper;
        this.members = members;
        this.files = files;
    }

    private static final Map<String, TableMeta> PRESET_METAS = new LinkedHashMap<>();

    static {
        // CRM 模块
        PRESET_METAS.put("crm_customer", new TableMeta(
                "crm_customer", "crm", "客户关系 CRM", "客户档案", "name",
                List.of(new CascadeRule("crm_clue", "converted_customer_id"), new CascadeRule("crm_business", "customer_id"))
        ));
        PRESET_METAS.put("crm_clue", new TableMeta(
                "crm_clue", "crm", "客户关系 CRM", "销售线索", "name", List.of()
        ));
        PRESET_METAS.put("crm_business", new TableMeta(
                "crm_business", "crm", "客户关系 CRM", "商机阶段", "name", List.of()
        ));
        PRESET_METAS.put("crm_contract", new TableMeta(
                "crm_contract", "crm", "客户关系 CRM", "商务合同", "name", List.of()
        ));
        PRESET_METAS.put("crm_receivable", new TableMeta(
                "crm_receivable", "crm", "客户关系 CRM", "财务回款", "receivable_no", List.of()
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
                List.of(new CascadeRule("wf_process_instance", "definition_id"))
        ));
        PRESET_METAS.put("wf_process_instance", new TableMeta(
                "wf_process_instance", "workflow", "工作流中心", "流程实例", "title",
                List.of(new CascadeRule("wf_task", "instance_id"))
        ));

        // IAM 组织与权限
        PRESET_METAS.put("iam_user", new TableMeta(
                "iam_user", "iam", "身份与权限中心", "系统用户", "username",
                List.of()
        ));
        PRESET_METAS.put("iam_role", new TableMeta(
                "iam_role", "iam", "身份与权限中心", "角色权限", "role_name",
                List.of()
        ));
        PRESET_METAS.put("iam_department", new TableMeta(
                "iam_department", "iam", "身份与权限中心", "部门机构", "dept_name", List.of()
        ));

        // 系统中心
        PRESET_METAS.put("sys_config", new TableMeta(
                "sys_config", "system", "系统中心", "参数配置", "config_name", List.of()
        ));
        PRESET_METAS.put("sys_dict_type", new TableMeta(
                "sys_dict_type", "system", "系统中心", "数据字典", "dict_name",
                List.of(new CascadeRule("sys_dict_item", "dict_type_id"))
        ));
        PRESET_METAS.put("sys_dict_item", new TableMeta(
                "sys_dict_item", "system", "系统中心", "字典明细", "item_label", List.of()
        ));
        PRESET_METAS.put("wf_task", new TableMeta("wf_task", "workflow", "工作流中心", "流程任务", "node_name", List.of()));
        PRESET_METAS.put("org_unit", new TableMeta("org_unit", "iam", "身份与权限中心", "组织单元", "display_name", List.of()));
        PRESET_METAS.put("file_object", new TableMeta("file_object", "file", "文件中心", "文件", "file_name", List.of()));
    }

    private Map<String, TrashTarget> registry(long tenantId) {
        Map<String, String> dynamic = new LinkedHashMap<>();
        String prefix = "biz_dyn_t" + tenantId + "_";
        for (var model : mapper.registeredModels(tenantId)) {
            String code = model.modelCode();
            if (!TrashTarget.validName(code) || !TrashTarget.validName(model.tableName())) continue;
            boolean currentName = model.tableName().equals(prefix + code);
            // Legacy models used a shared physical table. A tenant-qualified name can never qualify as legacy.
            boolean legacyName = !code.matches("t[0-9]+_.*") && model.tableName().equals("biz_dyn_" + code);
            if (currentName || legacyName) dynamic.put(model.tableName(), model.modelName());
        }
        Map<String, Set<String>> schema = new LinkedHashMap<>();
        Set<String> names = new LinkedHashSet<>(PRESET_METAS.keySet());
        names.addAll(dynamic.keySet());
        for (var column : mapper.resourceColumns(names)) {
            schema.computeIfAbsent(column.tableName(), ignored -> new LinkedHashSet<>()).add(column.columnName());
        }
        Map<String, TrashTarget> result = new LinkedHashMap<>();
        for (var e : schema.entrySet()) {
            String name = e.getKey();
            if (!TrashTarget.validName(name) || !e.getValue().containsAll(List.of("id", "tenant_id", "deleted"))) continue;
            TableMeta meta = PRESET_METAS.get(name);
            if (meta == null) {
                if (dynamic.containsKey(name)) {
                    meta = new TableMeta(name, "other", "其他应用", dynamic.get(name), "title", List.of());
                } else if (name.startsWith("erp_") || name.startsWith("crm_") || (name.startsWith("biz_") && !name.startsWith("biz_dyn_"))) {
                    String code = name.startsWith("erp_") ? "erp" : name.startsWith("crm_") ? "crm" : "other";
                    String app = code.equals("erp") ? "进销存 ERP" : code.equals("crm") ? "客户关系 CRM" : "其他应用";
                    meta = new TableMeta(name, code, app, name, "name", List.of());
                } else {
                    continue;
                }
            }
            result.put(name, new TrashTarget(meta, e.getValue(), "id"));
        }
        return result;
    }

    public List<TrashAppSummaryResponse> getAppSummaries() {
        long tenantId = principal().tenantId();
        Map<String, Long> counts = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        long total = 0;
        for (TrashTarget target : registry(tenantId).values()) {
            long count = mapper.countDeleted(target, tenantId);
            if (count > 0) {
                counts.merge(target.meta.appCode(), count, Long::sum);
                names.put(target.meta.appCode(), target.meta.appName());
                total += count;
            }
        }
        List<TrashAppSummaryResponse> list = new ArrayList<>();
        list.add(new TrashAppSummaryResponse("ALL", "全部应用", "Files", total));
        counts.forEach((code, count) -> list.add(new TrashAppSummaryResponse(code, names.get(code), resolveAppIcon(code), count)));
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
        long tenantId = principal().tenantId();
        List<TrashItemResponse> results = new ArrayList<>();
        String search = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        for (TrashTarget target : registry(tenantId).values()) {
            TableMeta meta = target.meta;
            if (appCode != null && !appCode.isBlank() && !"ALL".equalsIgnoreCase(appCode) && !appCode.equalsIgnoreCase(meta.appCode())) continue;
            if (tableName != null && !tableName.isBlank() && !tableName.equalsIgnoreCase(meta.tableName())) continue;
            for (var row : mapper.listDeleted(target, tenantId)) {
                String title = row.title() == null ? "#" + row.id() : row.title();
                if (!search.isEmpty() && !title.toLowerCase(Locale.ROOT).contains(search)
                        && !meta.moduleName().toLowerCase(Locale.ROOT).contains(search)
                        && !Long.toString(row.id()).contains(search) && !meta.tableName().contains(search)) continue;
                boolean cascade = !meta.cascadeRules().isEmpty() && row.deletedAt() != null;
                results.add(new TrashItemResponse(meta.tableName() + "_" + row.id(), meta.appCode(), meta.appName(),
                        meta.tableName(), meta.moduleName(), row.id(), title, row.deletedAt(),
                        cascade ? "可同时恢复本次删除的关联记录" : "可恢复此记录", cascade));
            }
        }
        results.sort(Comparator.comparing(TrashItemResponse::deletedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return results;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TrashRestoreResponse restore(TrashActionRequest req) {
        AuthPrincipal actor = principal();
        long tenantId = actor.tenantId();
        var registry = registry(tenantId);
        TrashTarget target = target(registry, req.tableName());
        switch (target.getTable()) {
            case "iam_user" -> members.requireSeat(tenantId);
            case "iam_department", "org_unit" -> members.requireDepartment(tenantId);
            case "file_object" -> files.requireCapacity(tenantId, req.recordId());
        }
        var row = requiredDeleted(target, tenantId, req.recordId());
        int main = mapper.restore(target, tenantId, req.recordId(), actor.userId(), null);
        int count = req.isCascade() ? cascade(registry, target, actor, row, true) : 0;
        return new TrashRestoreResponse(target.getTable(), row.id(), main, count, "记录已恢复");
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void purge(TrashActionRequest req) {
        AuthPrincipal actor = principal();
        var registry = registry(actor.tenantId());
        TrashTarget target = target(registry, req.tableName());
        var row = requiredDeleted(target, actor.tenantId(), req.recordId());
        cascade(registry, target, actor, row, false);
        mapper.purge(target, actor.tenantId(), row.id(), null);
    }

    private int cascade(Map<String, TrashTarget> registry, TrashTarget parent, AuthPrincipal actor, TrashMapper.DeletedRow row, boolean restore) {
        if (row.deletedAt() == null) return 0;
        int total = 0;
        for (var rule : parent.meta.cascadeRules()) {
            TrashTarget child = registry.get(rule.childTable());
            if (child == null || !child.isDated() || !child.columns.contains(rule.foreignKeyColumn())) continue;
            TrashTarget related = new TrashTarget(child.meta, child.columns, rule.foreignKeyColumn());
            total += restore ? mapper.restore(related, actor.tenantId(), row.id(), actor.userId(), row.deletedAt())
                    : mapper.purge(related, actor.tenantId(), row.id(), row.deletedAt());
        }
        return total;
    }

    private TrashMapper.DeletedRow requiredDeleted(TrashTarget target, long tenantId, long recordId) {
        var row = mapper.lockDeleted(target, tenantId, recordId);
        if (row == null) throw new BizException("NOT_FOUND", "待处理的回收站记录不存在");
        return row;
    }

    private static TrashTarget target(Map<String, TrashTarget> registry, String name) {
        TrashTarget target = registry.get(name);
        if (target == null) throw new BizException("INVALID_TABLE", "不支持的回收站业务表");
        return target;
    }

    private static AuthPrincipal principal() {
        AuthPrincipal actor = AuthContext.get();
        if (actor == null || actor.tenantId() == null) throw new BizException("UNAUTHORIZED", "未授权访问");
        return actor;
    }
}
