<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import { InfoFilled } from "@element-plus/icons-vue";
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
import { localizeScopeType } from "../iam-meta";

type SubjectType = "USER" | "DEPARTMENT" | "POSITION" | "COMPANY" | "TENANT" | "ROLE";
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
  departmentIds: [] as number[],
  userIds: [] as number[],
  positionIds: [] as number[]
});

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

const resourceOptions = computed(() =>
  props.apiResources
    .filter((item) => item.resourceCode.includes(":list"))
    .map((item) => ({ label: `${item.resourceName} (${item.resourceCode})`, value: item.resourceCode, name: item.resourceName }))
);

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

  customForm.departmentIds = [];
  customForm.userIds = [];
  customForm.positionIds = [];
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

  customForm.departmentIds = [];
  customForm.userIds = [];
  customForm.positionIds = [];

  if (row.configJson) {
    try {
      const parsed = JSON.parse(row.configJson);
      customForm.departmentIds = parsed.departmentIds || parsed.orgUnitIds || [];
      customForm.userIds = parsed.userIds || parsed.employeeIds || [];
      customForm.positionIds = parsed.positionIds || [];
    } catch {
      customForm.departmentIds = [];
      customForm.userIds = [];
      customForm.positionIds = [];
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
    const hasAnySelection =
      customForm.departmentIds.length > 0 ||
      customForm.userIds.length > 0 ||
      customForm.positionIds.length > 0;

    if (!hasAnySelection) {
      ElMessage.warning("自定义数据范围下，请至少勾选一个允许访问的部门、成员或岗位");
      return;
    }

    configJson = JSON.stringify({
      departmentIds: customForm.departmentIds,
      userIds: customForm.userIds,
      positionIds: customForm.positionIds
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
      <div class="data-scope-header__hint">
        <el-icon><InfoFilled /></el-icon>
        <span v-if="activeScopeTab === 'rules'">
          面向业务主管与管理员，通过可视化勾选配置个人、部门、上下级及自定义数据访问边界。
        </span>
        <span v-else>
          预设企业通用访问边界模板，供角色和业务模块一键绑定与复用。
        </span>
      </div>
    </div>

    <!-- Tab 1: 数据权限规则 (全宽 100%) -->
    <div v-show="activeScopeTab === 'rules'" class="data-scope-table-wrap">
      <ProTable
        :columns="dataPermissionRuleColumns"
        :data="decoratedRules"
        :loading="loading"
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
            @click="toggleDataScope(row)"
          >{{ row.status === "ENABLED" ? "停用" : "启用" }}</el-button>
        </template>
      </ProTable>
    </div>

    <!-- 可视化数据权限规则配置抽屉/对话框 -->
    <el-dialog
      v-model="ruleDialog.visible"
      :title="ruleDialog.mode === 'create' ? '新增数据权限规则' : '编辑数据权限规则'"
      width="720px"
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

        <!-- 自定义范围：可视化勾选面板，彻底消除任何 JSON 或代码 -->
        <div v-else class="custom-scope-panel">
          <div class="custom-scope-header">
            <div class="custom-scope-title">可视化指定访问范围</div>
            <div class="custom-scope-subtitle">直接在下方勾选授权对象允许跨域查阅的部门、人员或岗位，底层自动转换为行级安全过滤条件。</div>
          </div>

          <div class="custom-scope-fields">
            <el-form-item label="允许访问的部门">
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

            <el-form-item label="允许访问的成员">
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

            <el-form-item label="允许访问的岗位（可选）">
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
          </div>
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
  gap: 16px;
  padding: 12px 16px;
  border-radius: var(--sb-radius-md);
  background: #fff;
  border: 1px solid var(--sb-border-color);
  flex-wrap: wrap;
}

.data-scope-header__nav {
  display: flex;
  align-items: center;
}

.data-scope-header__hint {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--sb-text-secondary);

  .el-icon {
    color: var(--sb-primary-color);
    font-size: 15px;
  }
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
  color: #1e293b;
}

.scope-policy-hint {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 16px;
  border-radius: 6px;
  background-color: #f8fafc;
  border: 1px solid #e2e8f0;
  margin-bottom: 8px;

  .scope-hint-icon {
    font-size: 18px;
    color: #2563eb;
    margin-top: 2px;
    flex-shrink: 0;
  }

  .scope-hint-title {
    font-size: 13px;
    font-weight: 600;
    color: #0f172a;
    margin-bottom: 2px;
  }

  .scope-hint-desc {
    font-size: 12px;
    color: #64748b;
    line-height: 1.5;
  }
}

.custom-scope-panel {
  padding: 14px 16px;
  border-radius: 6px;
  background-color: #f8fafc;
  border: 1px solid #cbd5e1;
  margin-bottom: 8px;

  .custom-scope-header {
    margin-bottom: 12px;

    .custom-scope-title {
      font-size: 13px;
      font-weight: 600;
      color: #0f172a;
    }

    .custom-scope-subtitle {
      font-size: 12px;
      color: #64748b;
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

@media (max-width: 1280px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
