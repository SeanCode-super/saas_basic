<script setup lang="ts">
import { computed, reactive, watch } from "vue";
import { ElMessage } from "element-plus";
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
import BaseCard from "@/components/base/BaseCard.vue";
import BaseStatusTag from "@/components/base/BaseStatusTag.vue";
import ProTable from "@/components/pro/ProTable.vue";
import type { ProTableColumn } from "@/components/pro/ProTable.vue";

type SubjectType = "USER" | "DEPARTMENT" | "POSITION" | "COMPANY" | "TENANT" | "ROLE";
type ScopeType = "PERSONAL" | "DEPARTMENT" | "POSITION" | "COMPANY" | "TENANT" | "ROLE" | "CUSTOM";

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
  scopeType: "TENANT" as ScopeType,
  configText: "",
  status: "ENABLED",
  remark: ""
});

const companyDepartments = computed(() => props.departments.filter((item) => Number(item.parentId ?? 0) <= 0));

const subjectTypeOptions: Array<{ label: string; value: SubjectType }> = [
  { label: "个人", value: "USER" },
  { label: "部门", value: "DEPARTMENT" },
  { label: "岗位", value: "POSITION" },
  { label: "公司", value: "COMPANY" },
  { label: "租户", value: "TENANT" },
  { label: "角色", value: "ROLE" }
];

const scopeTypeOptions: Array<{ label: string; value: ScopeType }> = [
  { label: "个人", value: "PERSONAL" },
  { label: "部门", value: "DEPARTMENT" },
  { label: "岗位", value: "POSITION" },
  { label: "公司", value: "COMPANY" },
  { label: "租户", value: "TENANT" },
  { label: "角色", value: "ROLE" },
  { label: "自定义", value: "CUSTOM" }
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

function summarizeConfig(scopeType: string, configJson?: string) {
  if (scopeType !== "CUSTOM") {
    return "按范围类型自动计算";
  }
  if (!configJson) {
    return "未配置";
  }
  return configJson.length > 48 ? `${configJson.slice(0, 48)}...` : configJson;
}

function resetRuleForm() {
  ruleForm.resourceCode = resourceOptions.value[0]?.value ?? "";
  ruleForm.resourceName = resourceOptions.value[0]?.name ?? "";
  ruleForm.subjectType = "ROLE";
  ruleForm.subjectValue = props.roles[0] ? String(props.roles[0].id) : "";
  ruleForm.scopeType = "TENANT";
  ruleForm.configText = "";
  ruleForm.status = "ENABLED";
  ruleForm.remark = "";
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
  ruleForm.scopeType = (row.scopeType as ScopeType) || "TENANT";
  ruleForm.configText = row.configJson ?? "";
  ruleForm.status = row.status;
  ruleForm.remark = row.remark ?? "";
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
  const payload = {
    tenantId: props.currentTenantId,
    resourceCode: ruleForm.resourceCode,
    resourceName: ruleForm.resourceName,
    subjectType: ruleForm.subjectType,
    subjectValue: ruleForm.subjectValue,
    scopeType: ruleForm.scopeType,
    configJson: ruleForm.scopeType === "CUSTOM" ? ruleForm.configText || undefined : undefined,
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
  <div class="governance-grid">
    <div class="data-scope-grid">
      <ProTable
        :columns="dataPermissionRuleColumns"
        :data="decoratedRules"
        :loading="loading"
        selectable
        title="数据权限规则"
        subtitle="按个人、部门、岗位、公司、租户、角色六个维度定义数据访问范围，并支持自定义 JSON 配置。"
      >
        <template #toolbar>
          <el-button
            v-if="canWrite"
            v-button-permission="{ code: 'iam_data_scope_rule:create', fallback: 'iam:data-scope:write' }"
            type="primary"
            @click="openRuleCreate"
          >新增数据权限</el-button>
        </template>
        <template #bulkActions="{ rows }">
          <el-button v-if="canWrite" type="primary" plain @click="batchUpdateRules(asRuleRows(rows), 'ENABLED')">批量启用</el-button>
          <el-button v-if="canWrite" plain @click="batchUpdateRules(asRuleRows(rows), 'DISABLED')">批量停用</el-button>
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

      <BaseCard>
        <template #header>
          <div class="header-stack">
            <h3>规则模板台账</h3>
            <p>这部分保留数据范围模板，用于角色基础模型和历史兼容；真正的数据访问以右侧规则矩阵为准。</p>
          </div>
        </template>
        <ProTable
          :columns="dataScopeColumns"
          :data="dataScopes"
          :loading="loading"
          selectable
          compact
          title="范围模板"
          subtitle="模板描述可复用的范围类型，不直接决定谁能看什么。"
        >
          <template #toolbar>
            <el-button
              v-if="canWrite"
              v-button-permission="{ code: 'iam_data_scope_template:create', fallback: 'iam:data-scope:write' }"
              type="primary"
              @click="openDataScopeCreate"
            >新增范围模板</el-button>
          </template>
          <template #bulkActions="{ rows }">
            <el-button v-if="canWrite" type="primary" plain @click="batchEnableDataScopes(asDataScopeRows(rows))">批量启用</el-button>
            <el-button v-if="canWrite" plain @click="batchDisableDataScopes(asDataScopeRows(rows))">批量停用</el-button>
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
      </BaseCard>
    </div>

    <el-dialog v-model="ruleDialog.visible" :title="ruleDialog.mode === 'create' ? '新增数据权限规则' : '编辑数据权限规则'" width="760px">
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="数据资源">
            <el-select v-model="ruleForm.resourceCode" filterable style="width: 100%">
              <el-option v-for="item in resourceOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="授权维度">
            <el-select v-model="ruleForm.subjectType" style="width: 100%">
              <el-option v-for="item in subjectTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="授权对象">
            <el-select v-model="ruleForm.subjectValue" filterable style="width: 100%">
              <el-option v-for="item in subjectValueOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="数据范围">
            <el-select v-model="ruleForm.scopeType" style="width: 100%">
              <el-option v-for="item in scopeTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="ruleForm.status" style="width: 100%">
              <el-option label="启用" value="ENABLED" />
              <el-option label="停用" value="DISABLED" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item v-if="ruleForm.scopeType === 'CUSTOM'" label="自定义配置 JSON">
          <el-input
            v-model="ruleForm.configText"
            type="textarea"
            :rows="6"
            placeholder='例如 {"departmentIds":[1,2],"positionIds":[3],"roleIds":[1]}'
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="ruleForm.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ruleDialog.visible = false">取消</el-button>
        <el-button v-button-permission="{ code: 'iam_data_scope_rule:save', fallback: 'iam:data-scope:write' }" type="primary" @click="submitRule">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.data-scope-grid {
  display: grid;
  gap: 16px;
  grid-template-columns: 1.25fr 0.9fr;
}

.form-grid {
  display: grid;
  gap: 16px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

@media (max-width: 1280px) {
  .data-scope-grid,
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
