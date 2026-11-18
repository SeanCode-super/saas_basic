<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import { Close, InfoFilled, Plus } from "@element-plus/icons-vue";
import {
  createIamDataPermissionRule,
  updateIamDataPermissionRule,
  updateIamDataPermissionRuleStatus
} from "@/api/modules/iam";
import type {
  IamApiResourceRow,
  IamDataPermissionRuleRow,
  IamDataScopeRow,
  IamDepartmentRow,
  IamPositionRow,
  IamRoleRow,
  IamUserRow
} from "@/api/modules/iam";
import BaseStatusTag from "@/components/base/BaseStatusTag.vue";
import ProTable from "@/components/pro/ProTable.vue";
import type { ProTableColumn } from "@/components/pro/ProTable.vue";
import type { DimensionAxisConfig } from "@/utils/dimension-slice";
import { localizeScopeType } from "../iam-meta";

type SubjectType = "USER" | "DEPARTMENT" | "POSITION" | "COMPANY" | "TENANT" | "ROLE";
type AbacOperator = "EQ" | "NEQ" | "GT" | "GTE" | "LT" | "LTE" | "LIKE" | "IN";

interface AbacConditionDraft {
  field: string;
  operator: AbacOperator;
  rawValue: string;
}
type ScopeType =
  | "ALL"
  | "DEPT_AND_CHILDREN"
  | "DEPT_ONLY"
  | "SELF_AND_SUBORDINATES"
  | "SELF"
  | "ORG_AND_CHILDREN"
  | "CUSTOM"
  | "PERSONAL"
  | "DEPARTMENT"
  | "POSITION"
  | "COMPANY"
  | "TENANT"
  | "ROLE";

interface DeptTreeNode {
  id: number;
  deptName: string;
  deptFullName: string;
  children?: DeptTreeNode[];
}

const props = defineProps<{
  dataScopeColumns: ProTableColumn[];
  dataPermissionRuleColumns: ProTableColumn[];
  dataScopes: IamDataScopeRow[];
  dataPermissionRules: IamDataPermissionRuleRow[];
  apiResources: IamApiResourceRow[];
  users: IamUserRow[];
  roles: IamRoleRow[];
  departments: IamDepartmentRow[];
  positions: IamPositionRow[];
  loading: boolean;
  canWrite: boolean;
  currentTenantId: number;
  openDataScopeCreate: () => void;
  openDataScopeEdit: (row: IamDataScopeRow) => void;
  toggleDataScope: (row: IamDataScopeRow) => void;
  batchEnableDataScopes: (rows: IamDataScopeRow[]) => void | Promise<void>;
  batchDisableDataScopes: (rows: IamDataScopeRow[]) => void | Promise<void>;
  refreshData: () => Promise<void>;
}>();

const activeScopeTab = ref<"rules" | "templates">("rules");

const ruleDialog = reactive({
  visible: false,
  mode: "create" as "create" | "edit",
  id: 0
});

const ruleForm = reactive({
  resourceCode: "",
  resourceName: "",
  subjectType: "ROLE" as SubjectType,
  subjectValue: "",
  scopeType: "DEPT_AND_CHILDREN" as ScopeType,
  status: "ENABLED",
  remark: ""
});

const customForm = reactive({
  organizationIds: [] as number[],
  departmentIds: [] as number[],
  userIds: [] as number[],
  positionIds: [] as number[],
  abacConditions: [] as AbacConditionDraft[]
});

const abacFieldPresets = [
  { label: "所属仓库编码 (warehouse_code)", value: "warehouse_code" },
  { label: "业务大区编码 (region_code)", value: "region_code" },
  { label: "物料/业务分类 (category)", value: "category" },
  { label: "单据总金额 (total_amount)", value: "total_amount" },
  { label: "业务审批状态 (status)", value: "status" },
  { label: "部门成本中心 (cost_center)", value: "cost_center" }
];

const abacOperatorOptions: Array<{ label: string; value: AbacOperator }> = [
  { label: "等于 (=)", value: "EQ" },
  { label: "不等于 (!=)", value: "NEQ" },
  { label: "大于 (>)", value: "GT" },
  { label: "大于等于 (>=)", value: "GTE" },
  { label: "小于 (<)", value: "LT" },
  { label: "小于等于 (<=)", value: "LTE" },
  { label: "模糊包含 (LIKE)", value: "LIKE" },
  { label: "枚举集合 (IN)", value: "IN" }
];

function addAbacConditionRow() {
  customForm.abacConditions.push({
    field: "warehouse_code",
    operator: "EQ",
    rawValue: ""
  });
}

function removeAbacConditionRow(index: number) {
  customForm.abacConditions.splice(index, 1);
}

function parseAbacScalar(raw: string, op: AbacOperator): string | number | boolean {
  const trimmed = raw.trim();
  if (trimmed === "true") return true;
  if (trimmed === "false") return false;
  const numericPattern = /^-?(0|[1-9]\d*)(\.\d+)?$/;
  if (numericPattern.test(trimmed) || ((op === "GT" || op === "GTE" || op === "LT" || op === "LTE") && !Number.isNaN(Number(trimmed)))) {
    return Number(trimmed);
  }
  return trimmed;
}

function serializeAbacConditions(drafts: AbacConditionDraft[]): Array<{ field: string; operator: AbacOperator; value: unknown }> {
  const result: Array<{ field: string; operator: AbacOperator; value: unknown }> = [];
  for (const item of drafts) {
    const field = item.field.trim();
    const raw = item.rawValue.trim();
    if (!field || !raw) continue;
    if (item.operator === "IN") {
      const parts = raw
        .split(/[,，]/)
        .map((s) => s.trim())
        .filter(Boolean);
      if (parts.length > 0) {
        result.push({
          field,
          operator: "IN",
          value: parts.map((p) => parseAbacScalar(p, "EQ"))
        });
      }
    } else {
      result.push({
        field,
        operator: item.operator,
        value: parseAbacScalar(raw, item.operator)
      });
    }
  }
  return result;
}

function formatSqlConditionLiteral(val: unknown): string {
  if (typeof val === "number" || typeof val === "boolean") {
    return String(val);
  }
  return `'${String(val).replace(/'/g, "''")}'`;
}

const sqlBoundaryPreview = computed(() => {
  switch (ruleForm.scopeType) {
    case "ALL":
    case "TENANT":
      return "tenant_id = #{currentUser.tenantId} /* 全租户通行，无额外行级裁剪 */";
    case "ORG_AND_CHILDREN":
      return "(tenant_id = #{currentUser.tenantId} AND org_unit_id IN (#{currentUser.companySubTreeIds}))";
    case "DEPT_AND_CHILDREN":
      return "(tenant_id = #{currentUser.tenantId} AND org_unit_id IN (#{currentUser.deptSubTreeIds}))";
    case "DEPT_ONLY":
      return "(tenant_id = #{currentUser.tenantId} AND org_unit_id = #{currentUser.departmentId})";
    case "SELF_AND_SUBORDINATES":
    case "SELF":
      return "(tenant_id = #{currentUser.tenantId} AND created_by = #{currentUser.userId})";
    case "CUSTOM": {
      const clauses: string[] = [];
      if (customForm.organizationIds.length > 0) {
        clauses.push(`organization_id IN (${customForm.organizationIds.join(", ")})`);
      }
      if (customForm.departmentIds.length > 0) {
        clauses.push(`org_unit_id IN (${customForm.departmentIds.join(", ")})`);
      }
      if (customForm.userIds.length > 0) {
        clauses.push(`created_by IN (${customForm.userIds.join(", ")})`);
      }
      if (customForm.positionIds.length > 0) {
        clauses.push(`/* 岗位维度: position_id IN (${customForm.positionIds.join(", ")}) */`);
      }
      for (const cond of serializeAbacConditions(customForm.abacConditions)) {
        const opMap: Record<AbacOperator, string> = {
          EQ: "=",
          NEQ: "!=",
          GT: ">",
          GTE: ">=",
          LT: "<",
          LTE: "<=",
          LIKE: "LIKE",
          IN: "IN"
        };
        if (cond.operator === "IN" && Array.isArray(cond.value)) {
          clauses.push(`${cond.field} IN (${cond.value.map(formatSqlConditionLiteral).join(", ")})`);
        } else {
          clauses.push(`${cond.field} ${opMap[cond.operator]} ${formatSqlConditionLiteral(cond.value)}`);
        }
      }
      if (!clauses.length) {
        return "1 = 0 /* 尚未勾选任何 5D 边界，默认拒绝所有行访问 */";
      }
      return clauses.length === 1
        ? `tenant_id = #{currentUser.tenantId} AND ${clauses[0]}`
        : `tenant_id = #{currentUser.tenantId} AND (${clauses.join(" OR ")})`;
    }
    default:
      return "tenant_id = #{currentUser.tenantId}";
  }
});

const ruleDimensionAxes = computed<DimensionAxisConfig[]>(() => [
  {
    key: "subjectType",
    label: "主体维度",
    options: subjectTypeOptions
  },
  {
    key: "scopeType",
    label: "范围策略",
    options: scopeTypeOptions.map((item) => ({
      label: localizeScopeType(item.value),
      value: item.value
    }))
  },
  {
    key: "status",
    label: "规则状态",
    options: [
      { label: "正常启用", value: "ENABLED" },
      { label: "暂停使用", value: "DISABLED" }
    ]
  }
]);

const companyDepartments = computed(() => props.departments.filter((item) => Number(item.parentId ?? 0) <= 0));

const departmentTreeOptions = computed(() => {
  const map = new Map<number, DeptTreeNode>();
  props.departments.forEach((dept) => {
    map.set(dept.id, {
      id: dept.id,
      deptName: dept.deptName,
      deptFullName: dept.deptFullName || dept.deptName,
      children: []
    });
  });

  const roots: DeptTreeNode[] = [];
  props.departments.forEach((dept) => {
    const node = map.get(dept.id);
    if (!node) return;
    const parentId = Number(dept.parentId ?? 0);
    if (parentId > 0 && map.has(parentId)) {
      map.get(parentId)!.children!.push(node);
    } else {
      roots.push(node);
    }
  });

  return roots;
});

const subjectTypeOptions: Array<{ label: string; value: SubjectType }> = [
  { label: "角色", value: "ROLE" },
  { label: "部门", value: "DEPARTMENT" },
  { label: "岗位", value: "POSITION" },
  { label: "个人", value: "USER" },
  { label: "公司", value: "COMPANY" },
  { label: "全租户", value: "TENANT" }
];

const scopeTypeOptions: Array<{ label: string; value: ScopeType; desc: string }> = [
  { label: "全企业全部数据 (ALL)", value: "ALL", desc: "可查阅企业内所有部门与成员的数据" },
  { label: "本部门及所有下级部门 (DEPT_AND_CHILDREN)", value: "DEPT_AND_CHILDREN", desc: "自动继承本人部门及所有子部门数据" },
  { label: "仅限本部门 (DEPT_ONLY)", value: "DEPT_ONLY", desc: "仅能查阅本人当前直接所在部门数据，不含子部门" },
  { label: "本人及直属下级 (SELF_AND_SUBORDINATES)", value: "SELF_AND_SUBORDINATES", desc: "仅能查阅本人及汇报线上下属录入的数据" },
  { label: "仅限本人经办 (SELF)", value: "SELF", desc: "只能查看本人作为创建人或责任人的数据" },
  { label: "本公司及所有分支 (ORG_AND_CHILDREN)", value: "ORG_AND_CHILDREN", desc: "可查阅所属独立公司法人及分支机构的数据" },
  { label: "自定义勾选范围 (CUSTOM)", value: "CUSTOM", desc: "无需编写代码，可视化勾选指定部门、人员或岗位" }
];

const subjectValueOptions = computed(() => {
  switch (ruleForm.subjectType) {
    case "USER":
      return props.users.map((item) => ({ label: `${item.nickname} (${item.username})`, value: String(item.id) }));
    case "DEPARTMENT":
      return props.departments.map((item) => ({ label: item.deptFullName, value: String(item.id) }));
    case "POSITION":
      return props.positions.map((item) => ({ label: item.positionName, value: String(item.id) }));
    case "COMPANY":
      return companyDepartments.value.map((item) => ({ label: item.deptName, value: String(item.id) }));
    case "TENANT":
      return [{ label: `当前租户 (${props.currentTenantId})`, value: String(props.currentTenantId) }];
    case "ROLE":
    default:
      return props.roles.map((item) => ({ label: item.roleName, value: String(item.id) }));
  }
});

const CANONICAL_DATA_SCOPE_RESOURCES = [
  { name: "全局业务域通配 (所有表)", value: "*" },
  { name: "CRM 客户档案主数据 (crm_customer)", value: "crm:customer" },
  { name: "CRM 营销线索池 (crm_clue)", value: "crm:clue" },
  { name: "CRM 商机与项目推进 (crm_business)", value: "crm:business" },
  { name: "CRM 合同签约台账 (crm_contract)", value: "crm:contract" },
  { name: "ERP 物料与商品档案 (erp_product)", value: "erp:product" },
  { name: "ERP 采购履约订单 (erp_order)", value: "erp:order" },
  { name: "工作流审批实例 (workflow_instance)", value: "workflow:instance" }
];

const resourceOptions = computed(() => {
  const seen = new Set<string>();
  const items: Array<{ label: string; value: string; name: string }> = [];

  for (const canonical of CANONICAL_DATA_SCOPE_RESOURCES) {
    seen.add(canonical.value);
    items.push({
      label: `${canonical.name} (${canonical.value})`,
      value: canonical.value,
      name: canonical.name
    });
  }

  for (const item of props.apiResources) {
    if (!item.resourceCode.includes(":list") || seen.has(item.resourceCode)) {
      continue;
    }
    seen.add(item.resourceCode);
    items.push({
      label: `${item.resourceName} (${item.resourceCode})`,
      value: item.resourceCode,
      name: item.resourceName
    });
  }

  return items;
});

const decoratedRules = computed(() =>
  props.dataPermissionRules.map((row) => ({
    ...row,
    subjectLabel: resolveSubjectLabel(row.subjectType, row.subjectValue),
    configSummary: summarizeConfig(row.scopeType, row.configJson)
  }))
);

watch(
  () => ruleForm.subjectType,
  () => {
    ruleForm.subjectValue = subjectValueOptions.value[0]?.value ?? "";
  }
);

watch(
  () => ruleForm.resourceCode,
  (value) => {
    const matched = resourceOptions.value.find((item) => item.value === value);
    if (matched) {
      ruleForm.resourceName = matched.name;
    }
  }
);

function asDataScopeRows(rows: object[]) {
  return rows as IamDataScopeRow[];
}

function asRuleRows(rows: object[]) {
  return rows as IamDataPermissionRuleRow[];
}

function resolveSubjectLabel(subjectType: string, subjectValue: string) {
  if (!subjectValue) {
    return "-";
  }
  switch (subjectType) {
    case "USER":
      return props.users.find((item) => String(item.id) === subjectValue)?.nickname ?? subjectValue;
    case "DEPARTMENT":
      return props.departments.find((item) => String(item.id) === subjectValue)?.deptFullName ?? subjectValue;
    case "POSITION":
      return props.positions.find((item) => String(item.id) === subjectValue)?.positionName ?? subjectValue;
    case "COMPANY":
      return companyDepartments.value.find((item) => String(item.id) === subjectValue)?.deptName ?? subjectValue;
    case "TENANT":
      return `租户 ${subjectValue}`;
    case "ROLE":
      return props.roles.find((item) => String(item.id) === subjectValue)?.roleName ?? subjectValue;
    default:
      return subjectValue;
  }
}

function summarizeConfig(scopeType: string, configJson?: string): string {
  if (scopeType !== "CUSTOM") {
    switch (scopeType) {
      case "ALL":
      case "TENANT":
        return "全企业全部业务数据";
      case "DEPT_AND_CHILDREN":
      case "DEPARTMENT":
        return "所属部门及所有下级部门";
      case "DEPT_ONLY":
        return "仅限所属直属部门 (不含下级)";
      case "SELF_AND_SUBORDINATES":
        return "本人及直属下属录入数据";
      case "SELF":
      case "PERSONAL":
        return "仅限本人经办或录入的数据";
      case "ORG_AND_CHILDREN":
        return "所属公司主体及所有分支机构";
      case "ORG_ONLY":
      case "COMPANY":
        return "仅限所属公司主体";
      default:
        return "系统自动按归属计算";
    }
  }

  if (!configJson) {
    return "未指定具体范围";
  }

  try {
    const parsed = JSON.parse(configJson);
    const parts: string[] = [];
    const orgIds: number[] = parsed.organizationIds || [];
    if (orgIds.length > 0) {
      const orgNames = orgIds
        .map((id) => props.departments.find((d) => d.id === id)?.deptName ?? `公司#${id}`)
        .slice(0, 2);
      const extra = orgIds.length > 2 ? ` 等${orgIds.length}个主体` : "";
      parts.push(`主体: ${orgNames.join("、")}${extra}`);
    }

    const deptIds: number[] = parsed.departmentIds || parsed.orgUnitIds || [];
    if (deptIds.length > 0) {
      const deptNames = deptIds
        .map((id) => props.departments.find((d) => d.id === id)?.deptName ?? `部门#${id}`)
        .slice(0, 3);
      const extra = deptIds.length > 3 ? ` 等${deptIds.length}个部门` : "";
      parts.push(`部门: ${deptNames.join("、")}${extra}`);
    }

    const userIds: number[] = parsed.userIds || parsed.employeeIds || [];
    if (userIds.length > 0) {
      const userNames = userIds
        .map((id) => props.users.find((u) => u.id === id)?.nickname ?? `成员#${id}`)
        .slice(0, 3);
      const extra = userIds.length > 3 ? ` 等${userIds.length}人` : "";
      parts.push(`人员: ${userNames.join("、")}${extra}`);
    }

    const posIds: number[] = parsed.positionIds || [];
    if (posIds.length > 0) {
      const posNames = posIds
        .map((id) => props.positions.find((p) => p.id === id)?.positionName ?? `岗位#${id}`)
        .slice(0, 3);
      parts.push(`岗位: ${posNames.join("、")}`);
    }

    const conditions = Array.isArray(parsed.conditions) ? parsed.conditions : [];
    if (conditions.length > 0) {
      const first = conditions[0];
      const condSummary = `${first.field} ${first.operator} ${Array.isArray(first.value) ? first.value.join(",") : first.value}`;
      const extra = conditions.length > 1 ? ` 等${conditions.length}条属性规则` : "";
      parts.push(`ABAC: ${condSummary}${extra}`);
    }

    return parts.length > 0 ? parts.join("；") : "已配置自定义过滤";
  } catch {
    return "自定义配置";
  }
}

function resolveScopeTagType(scopeType: string): "primary" | "success" | "warning" | "info" | "danger" {
  switch (scopeType) {
    case "ALL":
    case "TENANT":
      return "danger";
    case "DEPT_AND_CHILDREN":
    case "DEPARTMENT":
    case "ORG_AND_CHILDREN":
      return "primary";
    case "DEPT_ONLY":
    case "ORG_ONLY":
      return "info";
    case "SELF_AND_SUBORDINATES":
    case "SELF":
    case "PERSONAL":
      return "success";
    case "CUSTOM":
      return "warning";
    default:
      return "info";
  }
}

function getScopeHintTitle(scopeType: string) {
  switch (scopeType) {
    case "ALL":
      return "全企业穿透数据访问";
    case "DEPT_AND_CHILDREN":
      return "部门树自动穿透";
    case "DEPT_ONLY":
      return "单部门严格限制";
    case "SELF_AND_SUBORDINATES":
      return "组织汇报链穿透";
    case "SELF":
      return "个人级严格隔离";
    case "ORG_AND_CHILDREN":
      return "法人与多分支架构穿透";
    default:
      return "标准数据范围";
  }
}

function getScopeHintDesc(scopeType: string) {
  switch (scopeType) {
    case "ALL":
      return "授权对象可以查看整个企业（租户）内所有部门、员工的所有业务数据，适用于平台审计员、企业总裁等顶级角色。";
    case "DEPT_AND_CHILDREN":
      return "授权对象登录后，系统会自动根据其所属部门，动态穿透授权其部门及所有下级子部门的数据，适用于部门总监、区域经理。";
    case "DEPT_ONLY":
      return "授权对象仅能查看直接归属于其当前部门的数据，其下级子部门数据完全不可见，适用于基层部门主管。";
    case "SELF_AND_SUBORDINATES":
      return "授权对象可以查看自己录入的数据，以及管理汇报线上下属人员录入的数据，适用于销售组长、项目主管。";
    case "SELF":
      return "授权对象只能查看经办人或创建人为本人的业务数据，跨人数据严格隔离，适用于普通销售、一线员工。";
    case "ORG_AND_CHILDREN":
      return "授权对象可以查看其所在公司主体及全部下属分支机构的数据，适用于集团总部业务人员。";
    default:
      return "系统将在数据库层面进行行级动态过滤，无需手动编写 SQL 或规则。";
  }
}

function resetRuleForm() {
  ruleForm.resourceCode = resourceOptions.value[0]?.value ?? "";
  ruleForm.resourceName = resourceOptions.value[0]?.name ?? "";
  ruleForm.subjectType = "ROLE";
  ruleForm.subjectValue = props.roles[0] ? String(props.roles[0].id) : "";
  ruleForm.scopeType = "DEPT_AND_CHILDREN";
  ruleForm.status = "ENABLED";
  ruleForm.remark = "";

  customForm.organizationIds = [];
  customForm.departmentIds = [];
  customForm.userIds = [];
  customForm.positionIds = [];
  customForm.abacConditions = [];
}

function openRuleCreate() {
  resetRuleForm();
  ruleDialog.mode = "create";
  ruleDialog.id = 0;
  ruleDialog.visible = true;
}

function openRuleEdit(row: IamDataPermissionRuleRow) {
  ruleDialog.mode = "edit";
  ruleDialog.id = row.id;
  ruleForm.resourceCode = row.resourceCode;
  ruleForm.resourceName = row.resourceName;
  ruleForm.subjectType = (row.subjectType as SubjectType) || "ROLE";
  ruleForm.subjectValue = row.subjectValue;
  ruleForm.scopeType = (row.scopeType as ScopeType) || "DEPT_AND_CHILDREN";
  ruleForm.status = row.status;
  ruleForm.remark = row.remark ?? "";

  customForm.organizationIds = [];
  customForm.departmentIds = [];
  customForm.userIds = [];
  customForm.positionIds = [];
  customForm.abacConditions = [];

  if (row.configJson) {
    try {
      const parsed = JSON.parse(row.configJson);
      customForm.organizationIds = parsed.organizationIds || [];
      customForm.departmentIds = parsed.departmentIds || parsed.orgUnitIds || [];
      customForm.userIds = parsed.userIds || parsed.employeeIds || [];
      customForm.positionIds = parsed.positionIds || [];
      if (Array.isArray(parsed.conditions)) {
        customForm.abacConditions = parsed.conditions.map(
          (item: { field?: string; operator?: string; value?: unknown }) => ({
            field: String(item.field ?? ""),
            operator: (String(item.operator ?? "EQ").toUpperCase() as AbacOperator) || "EQ",
            rawValue: Array.isArray(item.value)
              ? item.value.map((v) => String(v)).join(", ")
              : item.value == null
                ? ""
                : String(item.value)
          })
        );
      }
    } catch {
      customForm.organizationIds = [];
      customForm.departmentIds = [];
      customForm.userIds = [];
      customForm.positionIds = [];
      customForm.abacConditions = [];
    }
  }

  ruleDialog.visible = true;
}

async function submitRule() {
  if (!ruleForm.resourceCode) {
    ElMessage.error("请选择要控制的数据资源");
    return;
  }
  if (!ruleForm.subjectValue) {
    ElMessage.error("请选择授权对象");
    return;
  }

  let configJson: string | undefined = undefined;
  if (ruleForm.scopeType === "CUSTOM") {
    const hasIncompleteAbac = customForm.abacConditions.some(
      (item) => (item.field.trim() && !item.rawValue.trim()) || (!item.field.trim() && item.rawValue.trim())
    );
    if (hasIncompleteAbac) {
      ElMessage.warning("请补全业务属性维度条件中的字段名与匹配值");
      return;
    }

    const conditions = serializeAbacConditions(customForm.abacConditions);
    const hasAnySelection =
      customForm.organizationIds.length > 0 ||
      customForm.departmentIds.length > 0 ||
      customForm.userIds.length > 0 ||
      customForm.positionIds.length > 0 ||
      conditions.length > 0;

    if (!hasAnySelection) {
      ElMessage.warning("自定义数据范围下，请至少勾选组织/人员维度或添加一条业务属性维度条件");
      return;
    }

    configJson = JSON.stringify({
      organizationIds: customForm.organizationIds,
      departmentIds: customForm.departmentIds,
      userIds: customForm.userIds,
      positionIds: customForm.positionIds,
      conditions
    });
  }

  const payload = {
    tenantId: props.currentTenantId,
    resourceCode: ruleForm.resourceCode,
    resourceName: ruleForm.resourceName,
    subjectType: ruleForm.subjectType,
    subjectValue: ruleForm.subjectValue,
    scopeType: ruleForm.scopeType,
    configJson,
    status: ruleForm.status,
    remark: ruleForm.remark || undefined
  };

  if (ruleDialog.mode === "create") {
    await createIamDataPermissionRule(payload);
  } else {
    await updateIamDataPermissionRule(ruleDialog.id, payload);
  }
  ruleDialog.visible = false;
  await props.refreshData();
  ElMessage.success("数据权限规则已保存");
}

async function toggleRule(row: IamDataPermissionRuleRow) {
  await updateIamDataPermissionRuleStatus(row.id, {
    status: row.status === "ENABLED" ? "DISABLED" : "ENABLED"
  });
  await props.refreshData();
  ElMessage.success("数据权限规则状态已更新");
}

async function batchUpdateRules(rows: IamDataPermissionRuleRow[], status: "ENABLED" | "DISABLED") {
  await Promise.all(rows.map((row) => updateIamDataPermissionRuleStatus(row.id, { status })));
  await props.refreshData();
  ElMessage.success(status === "ENABLED" ? "已批量启用数据权限规则" : "已批量停用数据权限规则");
}
</script>

<template>
  <div class="data-scope-view">
    <div class="data-scope-header">
      <div class="data-scope-header__nav">
        <el-radio-group v-model="activeScopeTab" size="default">
          <el-radio-button value="rules">
            数据权限规则 ({{ decoratedRules.length }})
          </el-radio-button>
          <el-radio-button value="templates">
            标准数据范围模板 ({{ dataScopes.length }})
          </el-radio-button>
        </el-radio-group>
      </div>
      <div class="data-scope-header__priority">
        <span class="priority-pill priority-pill--l1">L1 超管破窗</span>
        <span class="priority-arrow">→</span>
        <span class="priority-pill priority-pill--l2">L2 资源 5D 规则</span>
        <span class="priority-arrow">→</span>
        <span class="priority-pill priority-pill--l3">L3 角色默认范围</span>
      </div>
    </div>

    <!-- Tab 1: 数据权限规则 (全宽 100%) -->
    <div v-show="activeScopeTab === 'rules'" class="data-scope-table-wrap">
      <ProTable
        :columns="dataPermissionRuleColumns"
        :data="decoratedRules"
        :loading="loading"
        :dimensions="ruleDimensionAxes"
        dimension-storage-key="iam-data-scope-rules"
        selectable
        title="数据权限规则"
        subtitle="面向业务主管与管理员，通过可视化勾选配置个人、部门、上下级及自定义数据访问边界。"
      >
        <template #toolbar>
          <el-button
            v-if="canWrite"
            v-button-permission="{ code: 'iam_data_scope_rule:create', fallback: 'iam:data-scope:write' }"
            type="primary"
            @click="openRuleCreate"
          >
            + 新增权限规则
          </el-button>
        </template>
        <template #bulkActions="{ rows }">
          <el-button v-if="canWrite" type="primary" plain @click="batchUpdateRules(asRuleRows(rows), 'ENABLED')">批量启用</el-button>
          <el-button v-if="canWrite" plain @click="batchUpdateRules(asRuleRows(rows), 'DISABLED')">批量停用</el-button>
        </template>
        <template #scopeType="{ row }">
          <el-tag size="small" effect="plain" :type="resolveScopeTagType(row.scopeType)">
            {{ localizeScopeType(row.scopeType) }}
          </el-tag>
        </template>
        <template #configSummary="{ row }">
          <span class="config-summary-text" :title="row.configSummary">
            {{ row.configSummary }}
          </span>
        </template>
        <template #status="{ row }">
          <BaseStatusTag :status="String(row.status)" :type="row.status === 'ENABLED' ? 'success' : 'danger'" />
        </template>
        <template #actions="{ row }">
          <el-button
            v-if="canWrite"
            v-button-permission="{ code: 'iam_data_scope_rule:edit', fallback: 'iam:data-scope:write' }"
            link
            type="primary"
            @click="openRuleEdit(row)"
          >编辑</el-button>
          <el-button
            v-if="canWrite"
            v-button-permission="{ code: 'iam_data_scope_rule:toggle', fallback: 'iam:data-scope:write' }"
            link
            :type="row.status === 'ENABLED' ? 'danger' : 'success'"
            @click="toggleRule(row)"
          >{{ row.status === "ENABLED" ? "停用" : "启用" }}</el-button>
        </template>
      </ProTable>
    </div>

    <!-- Tab 2: 标准数据范围模板 (全宽 100%) -->
    <div v-show="activeScopeTab === 'templates'" class="data-scope-table-wrap">
      <ProTable
        :columns="dataScopeColumns"
        :data="dataScopes"
        :loading="loading"
        selectable
        title="标准数据范围模板"
        subtitle="内置可复用范围类型，直接决定角色绑定的默认数据边界。"
      >
        <template #toolbar>
          <el-button
            v-if="canWrite"
            v-button-permission="{ code: 'iam_data_scope_template:create', fallback: 'iam:data-scope:write' }"
            type="primary"
            @click="openDataScopeCreate"
          >+ 新增范围模板</el-button>
        </template>
        <template #bulkActions="{ rows }">
          <el-button v-if="canWrite" type="primary" plain @click="batchEnableDataScopes(asDataScopeRows(rows))">批量启用</el-button>
          <el-button v-if="canWrite" plain @click="batchDisableDataScopes(asDataScopeRows(rows))">批量停用</el-button>
        </template>
        <template #scopeType="{ row }">
          <el-tag size="small" effect="plain" :type="resolveScopeTagType(row.scopeType)">
            {{ localizeScopeType(row.scopeType) }}
          </el-tag>
        </template>
        <template #status="{ row }">
          <BaseStatusTag :status="String(row.status)" :type="row.status === 'ENABLED' ? 'success' : 'danger'" />
        </template>
        <template #actions="{ row }">
          <el-button
            v-if="canWrite"
            v-button-permission="{ code: 'iam_data_scope_template:edit', fallback: 'iam:data-scope:write' }"
            link
            type="primary"
            @click="openDataScopeEdit(row)"
          >编辑</el-button>
          <el-button
            v-if="canWrite"
            v-button-permission="{ code: 'iam_data_scope_template:toggle', fallback: 'iam:data-scope:write' }"
            link
            :type="row.status === 'ENABLED' ? 'danger' : 'success'"
            @click="toggleDataScope(row)"
          >{{ row.status === "ENABLED" ? "停用" : "启用" }}</el-button>
        </template>
      </ProTable>
    </div>

    <!-- 可视化数据权限规则配置抽屉/对话框 -->
    <el-dialog
      v-model="ruleDialog.visible"
      :title="ruleDialog.mode === 'create' ? '新增数据权限规则' : '编辑数据权限规则'"
      width="780px"
      class="visual-rule-dialog"
      destroy-on-close
    >
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="受控数据资源" required>
            <el-select v-model="ruleForm.resourceCode" filterable style="width: 100%" placeholder="选择需要行级保护的业务数据">
              <el-option v-for="item in resourceOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="授权主体维度" required>
            <el-select v-model="ruleForm.subjectType" style="width: 100%">
              <el-option v-for="item in subjectTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="授权主体对象" required>
            <el-select v-model="ruleForm.subjectValue" filterable style="width: 100%" placeholder="选择具体的授权角色或部门">
              <el-option v-for="item in subjectValueOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="规则状态" required>
            <el-select v-model="ruleForm.status" style="width: 100%">
              <el-option label="正常启用" value="ENABLED" />
              <el-option label="暂停使用" value="DISABLED" />
            </el-select>
          </el-form-item>
        </div>

        <el-form-item label="数据访问范围策略" required>
          <el-select v-model="ruleForm.scopeType" style="width: 100%" placeholder="请选择数据过滤模式">
            <el-option
              v-for="item in scopeTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <!-- 非自定义范围：业务提示卡片 -->
        <div v-if="ruleForm.scopeType !== 'CUSTOM'" class="scope-policy-hint">
          <el-icon class="scope-hint-icon"><InfoFilled /></el-icon>
          <div class="scope-hint-content">
            <div class="scope-hint-title">{{ getScopeHintTitle(ruleForm.scopeType) }}</div>
            <div class="scope-hint-desc">{{ getScopeHintDesc(ruleForm.scopeType) }}</div>
          </div>
        </div>

        <!-- 自定义范围：天枢 5D 五轴可视化边界构建器，彻底消除任何 JSON 或手写 SQL -->
        <div v-else class="custom-scope-panel">
          <div class="custom-scope-header">
            <div class="custom-scope-title">天枢 5D 五轴可视化数据边界</div>
            <div class="custom-scope-subtitle">按需勾选法人公司、部门树、岗位、人员，或叠加 ABAC 业务属性条件；底层自动编译为 JSqlParser 行级过滤 AST。</div>
          </div>

          <div class="custom-scope-fields">
            <el-form-item label="轴 1 · 独立法人公司主体">
              <el-select
                v-model="customForm.organizationIds"
                multiple
                filterable
                collapse-tags
                collapse-tags-tooltip
                clearable
                placeholder="勾选允许跨域查阅的公司法人主体（可选）"
                style="width: 100%"
              >
                <el-option
                  v-for="corp in companyDepartments"
                  :key="corp.id"
                  :label="corp.deptName"
                  :value="corp.id"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="轴 2 · 允许访问的部门">
              <el-tree-select
                v-model="customForm.departmentIds"
                :data="departmentTreeOptions"
                node-key="id"
                :props="{ label: 'deptFullName', children: 'children' }"
                multiple
                show-checkbox
                check-strictly
                collapse-tags
                collapse-tags-tooltip
                filterable
                clearable
                placeholder="展开组织架构树，点击勾选允许访问的部门（可多选）"
                style="width: 100%"
              />
            </el-form-item>

            <el-form-item label="轴 3 · 允许访问的岗位（可选）">
              <el-select
                v-model="customForm.positionIds"
                multiple
                filterable
                collapse-tags
                collapse-tags-tooltip
                clearable
                placeholder="勾选允许查阅的岗位（可多选）"
                style="width: 100%"
              >
                <el-option
                  v-for="p in props.positions"
                  :key="p.id"
                  :label="`${p.positionName} (${p.positionCode})`"
                  :value="p.id"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="轴 4 · 允许访问的成员">
              <el-select
                v-model="customForm.userIds"
                multiple
                filterable
                collapse-tags
                collapse-tags-tooltip
                clearable
                placeholder="搜索并勾选允许查阅的成员姓名或账号（可多选）"
                style="width: 100%"
              >
                <el-option
                  v-for="u in props.users"
                  :key="u.id"
                  :label="`${u.nickname} (${u.username})`"
                  :value="u.id"
                />
              </el-select>
            </el-form-item>

            <div class="abac-builder-block">
              <div class="abac-builder-header">
                <div>
                  <span class="abac-builder-title">轴 5 · 业务属性动态维度条件 (ABAC)</span>
                  <span class="abac-builder-hint">例如限制仅查看指定仓库、地域、物料分类或金额上限内的数据</span>
                </div>
                <el-button size="small" type="primary" plain :icon="Plus" @click="addAbacConditionRow">
                  添加属性维度
                </el-button>
              </div>

              <div v-if="customForm.abacConditions.length === 0" class="abac-empty-hint">
                暂未添加业务属性条件，当前仅按上方组织/岗位/人员边界过滤。
              </div>

              <div v-else class="abac-rows">
                <div v-for="(cond, index) in customForm.abacConditions" :key="index" class="abac-row">
                  <el-select
                    v-model="cond.field"
                    filterable
                    allow-create
                    default-first-option
                    placeholder="选择或输入字段名"
                    class="abac-field-select"
                  >
                    <el-option
                      v-for="preset in abacFieldPresets"
                      :key="preset.value"
                      :label="preset.label"
                      :value="preset.value"
                    />
                  </el-select>

                  <el-select v-model="cond.operator" class="abac-op-select">
                    <el-option
                      v-for="op in abacOperatorOptions"
                      :key="op.value"
                      :label="op.label"
                      :value="op.value"
                    />
                  </el-select>

                  <el-input
                    v-model="cond.rawValue"
                    :placeholder="cond.operator === 'IN' ? '多个值用逗号分隔，如 WH-SH, WH-SZ' : '输入比较值，如 50000 或 APPROVED'"
                    class="abac-val-input"
                  />

                  <el-button
                    circle
                    size="small"
                    :icon="Close"
                    aria-label="删除属性条件"
                    @click="removeAbacConditionRow(index)"
                  />
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 实时等效 SQL 边界预览 -->
        <div class="sql-preview-box">
          <div class="sql-preview-header">
            <span>实时等效行级 SQL 断言预览 (JSqlParser AST)</span>
            <el-tag size="small" effect="plain" type="info">{{ localizeScopeType(ruleForm.scopeType) }}</el-tag>
          </div>
          <code class="sql-preview-code">WHERE {{ sqlBoundaryPreview }}</code>
        </div>

        <el-form-item label="规则业务备注" style="margin-top: 16px">
          <el-input
            v-model="ruleForm.remark"
            type="textarea"
            :rows="2"
            placeholder="例如：允许华东区域销售总监查阅上海、杭州分公司的客户台账"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="ruleDialog.visible = false">取消</el-button>
        <el-button
          v-button-permission="{ code: 'iam_data_scope_rule:save', fallback: 'iam:data-scope:write' }"
          type="primary"
          @click="submitRule"
        >
          保存权限规则
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.data-scope-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
  min-width: 0;
}

.data-scope-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 2px 0 4px;
  border-radius: 0;
  background: transparent;
  border: 0;
  box-shadow: none;
  flex-wrap: wrap;
}

.data-scope-header__nav {
  display: flex;
  align-items: center;
}

.data-scope-header__priority {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  font-size: 12px;
}

.data-scope-table-wrap {
  width: 100%;
  min-width: 0;
}

.form-grid {
  display: grid;
  gap: 16px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.config-summary-text {
  display: inline-block;
  max-width: 420px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  color: var(--sb-text-secondary);
}

.scope-policy-hint {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 16px;
  border-radius: 8px;
  background-color: var(--sb-surface-soft);
  border: 1px solid var(--sb-border-subtle);
  margin-bottom: 10px;

  .scope-hint-icon {
    font-size: 18px;
    color: var(--sb-primary-color);
    margin-top: 2px;
    flex-shrink: 0;
  }

  .scope-hint-title {
    font-size: 13px;
    font-weight: 600;
    color: var(--sb-text-primary);
    margin-bottom: 2px;
  }

  .scope-hint-desc {
    font-size: 12px;
    color: var(--sb-text-muted);
    line-height: 1.5;
  }
}

.custom-scope-panel {
  padding: 14px 16px;
  border-radius: 10px;
  background-color: var(--sb-surface-soft);
  border: 1px solid var(--sb-border-subtle);
  margin-bottom: 10px;

  .custom-scope-header {
    margin-bottom: 12px;

    .custom-scope-title {
      font-size: 13.5px;
      font-weight: 700;
      color: var(--sb-text-primary);
    }

    .custom-scope-subtitle {
      font-size: 12px;
      color: var(--sb-text-muted);
      margin-top: 2px;
    }
  }

  .custom-scope-fields {
    display: flex;
    flex-direction: column;
    gap: 12px;

    :deep(.el-form-item) {
      margin-bottom: 0;
    }
  }
}

.abac-builder-block {
  margin-top: 4px;
  padding-top: 12px;
  border-top: 1px dashed var(--sb-border-color);
}

.abac-builder-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.abac-builder-title {
  display: block;
  font-size: 12.5px;
  font-weight: 650;
  color: var(--sb-text-primary);
}

.abac-builder-hint {
  display: block;
  font-size: 11.5px;
  color: var(--sb-text-muted);
  margin-top: 2px;
}

.abac-empty-hint {
  padding: 8px 12px;
  border-radius: 6px;
  border: 1px dashed var(--sb-border-subtle);
  font-size: 12px;
  color: var(--sb-text-muted);
  background: var(--sb-tier2-bg);
}

.abac-rows {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.abac-row {
  display: grid;
  grid-template-columns: minmax(170px, 1.3fr) 136px minmax(160px, 1.4fr) 28px;
  gap: 8px;
  align-items: center;
}

.sql-preview-box {
  padding: 10px 14px;
  border-radius: 8px;
  border: 1px solid var(--sb-badge-primary-border);
  background: var(--sb-badge-primary-bg);
  margin-top: 10px;
}

.sql-preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 650;
  color: var(--sb-badge-primary-text);
}

.sql-preview-code {
  display: block;
  font-family: var(--sb-font-mono);
  font-size: 12px;
  line-height: 1.45;
  color: var(--sb-text-primary);
  word-break: break-all;
}

@media (max-width: 1280px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}

.priority-pill {
  display: inline-flex;
  align-items: center;
  padding: 2px 9px;
  border-radius: 9999px;
  font-weight: 600;
  border: 1px solid var(--sb-border-subtle);

  &--l1 {
    background: var(--sb-tier1-bg);
    color: var(--sb-text-primary);
  }

  &--l2 {
    background: var(--sb-badge-primary-bg);
    border-color: var(--sb-badge-primary-border);
    color: var(--sb-badge-primary-text);
  }

  &--l3 {
    background: var(--sb-tier2-bg);
    color: var(--sb-text-secondary);
  }
}

.priority-arrow {
  color: var(--sb-text-muted);
  font-weight: 600;
}
</style>
