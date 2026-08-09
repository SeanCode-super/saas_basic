<script setup lang="ts">
import { computed, reactive, watch } from "vue";
import { ElMessage } from "element-plus";
import {
  createIamMenuPermission,
  updateIamMenuPermission,
  updateIamMenuPermissionStatus
} from "@/api/modules/iam";
import type {
  IamDepartmentRow,
  IamMenuPermissionRow,
  IamMenuRow,
  IamPositionRow,
  IamRoleRow,
  IamUserRow
} from "@/api/modules/iam";
import BaseCard from "@/components/base/BaseCard.vue";
import BaseStatusTag from "@/components/base/BaseStatusTag.vue";
import NavigationAtlas from "@/components/platform/NavigationAtlas.vue";
import ProTable from "@/components/pro/ProTable.vue";
import type { ProTableColumn } from "@/components/pro/ProTable.vue";
interface AtlasItem {
  id: number | string;
  code: string;
  title: string;
  path: string;
  query: Record<string, string>;
  children: AtlasItem[];
}

type SubjectType = "USER" | "DEPARTMENT" | "POSITION" | "COMPANY" | "TENANT" | "ROLE";

const props = defineProps<{
  menuAtlasApps: AtlasItem[];
  menuColumns: ProTableColumn[];
  menuPermissionColumns: ProTableColumn[];
  menus: IamMenuRow[];
  menuPermissions: IamMenuPermissionRow[];
  users: IamUserRow[];
  roles: IamRoleRow[];
  departments: IamDepartmentRow[];
  positions: IamPositionRow[];
  loading: boolean;
  canWrite: boolean;
  currentTenantId: number;
  handleMenuAtlasSelect: (item: { id: number | string }) => void;
  openMenuCreate: () => void;
  openMenuEdit: (row: IamMenuRow) => void;
  toggleMenu: (row: IamMenuRow) => void;
  batchEnableMenus: (rows: IamMenuRow[]) => void | Promise<void>;
  batchDisableMenus: (rows: IamMenuRow[]) => void | Promise<void>;
  refreshData: () => Promise<void>;
}>();

const permissionDialog = reactive({
  visible: false,
  mode: "create" as "create" | "edit",
  id: 0
});

const permissionForm = reactive({
  menuId: 0,
  subjectType: "ROLE" as SubjectType,
  subjectValue: "",
  buttonCodesText: "",
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

const menuOptions = computed(() =>
  props.menus.map((item) => ({
    label: `${item.menuName} (${item.menuCode})`,
    value: item.id
  }))
);

const subjectValueOptions = computed(() => {
  switch (permissionForm.subjectType) {
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

const decoratedMenuPermissions = computed(() =>
  props.menuPermissions.map((row) => ({
    ...row,
    subjectLabel: resolveSubjectLabel(row.subjectType, row.subjectValue),
    buttonCodesSummary: summarizeButtonCodes(row.buttonCodesJson)
  }))
);

watch(
  () => permissionForm.subjectType,
  () => {
    permissionForm.subjectValue = subjectValueOptions.value[0]?.value ?? "";
  }
);

function asMenuRows(rows: object[]) {
  return rows as IamMenuRow[];
}

function asPermissionRows(rows: object[]) {
  return rows as IamMenuPermissionRow[];
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

function summarizeButtonCodes(buttonCodesJson?: string) {
  if (!buttonCodesJson) {
    return "默认菜单访问";
  }
  try {
    const codes = JSON.parse(buttonCodesJson) as string[];
    return codes.length ? codes.join(" / ") : "默认菜单访问";
  } catch {
    return buttonCodesJson;
  }
}

function resetPermissionForm() {
  permissionForm.menuId = props.menus[0]?.id ?? 0;
  permissionForm.subjectType = "ROLE";
  permissionForm.subjectValue = props.roles[0] ? String(props.roles[0].id) : "";
  permissionForm.buttonCodesText = "";
  permissionForm.status = "ENABLED";
  permissionForm.remark = "";
}

function openPermissionCreate() {
  resetPermissionForm();
  permissionDialog.mode = "create";
  permissionDialog.id = 0;
  permissionDialog.visible = true;
}

function openPermissionEdit(row: IamMenuPermissionRow) {
  permissionDialog.mode = "edit";
  permissionDialog.id = row.id;
  permissionForm.menuId = row.menuId;
  permissionForm.subjectType = (row.subjectType as SubjectType) || "ROLE";
  permissionForm.subjectValue = row.subjectValue;
  permissionForm.buttonCodesText = parseCodesToText(row.buttonCodesJson);
  permissionForm.status = row.status;
  permissionForm.remark = row.remark ?? "";
  permissionDialog.visible = true;
}

async function submitPermission() {
  const selectedMenu = props.menus.find((item) => item.id === permissionForm.menuId);
  if (!selectedMenu) {
    ElMessage.error("请选择要授权的菜单");
    return;
  }
  if (!permissionForm.subjectValue) {
    ElMessage.error("请选择授权对象");
    return;
  }
  const payload = {
    tenantId: props.currentTenantId,
    menuId: permissionForm.menuId,
    menuCode: selectedMenu.menuCode,
    subjectType: permissionForm.subjectType,
    subjectValue: permissionForm.subjectValue,
    buttonCodesJson: parseButtonCodesText(permissionForm.buttonCodesText),
    status: permissionForm.status,
    remark: permissionForm.remark || undefined
  };
  if (permissionDialog.mode === "create") {
    await createIamMenuPermission(payload);
  } else {
    await updateIamMenuPermission(permissionDialog.id, payload);
  }
  permissionDialog.visible = false;
  await props.refreshData();
  ElMessage.success("菜单权限已保存");
}

async function togglePermission(row: IamMenuPermissionRow) {
  await updateIamMenuPermissionStatus(row.id, {
    status: row.status === "ENABLED" ? "DISABLED" : "ENABLED"
  });
  await props.refreshData();
  ElMessage.success("菜单权限状态已更新");
}

async function batchUpdatePermissions(rows: IamMenuPermissionRow[], status: "ENABLED" | "DISABLED") {
  await Promise.all(rows.map((row) => updateIamMenuPermissionStatus(row.id, { status })));
  await props.refreshData();
  ElMessage.success(status === "ENABLED" ? "已批量启用菜单权限" : "已批量停用菜单权限");
}

function parseCodesToText(buttonCodesJson?: string) {
  if (!buttonCodesJson) {
    return "";
  }
  try {
    const values = JSON.parse(buttonCodesJson) as string[];
    return values.join(",");
  } catch {
    return buttonCodesJson;
  }
}

function parseButtonCodesText(value: string) {
  const tokens = value
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
  return tokens.length ? JSON.stringify(tokens) : undefined;
}
</script>

<template>
  <div class="governance-grid">
    <BaseCard class="menu-center-card">
      <NavigationAtlas
        :apps="menuAtlasApps"
        title="当前控制台导航图"
        description="这里展示的就是当前前端正在使用的真实应用菜单。点击任意应用或子菜单，会直接打开对应菜单配置。"
        compact
        @select="handleMenuAtlasSelect"
      />
    </BaseCard>

    <div class="menu-center-grid">
      <ProTable
        :columns="menuColumns"
        :data="menus"
        :loading="loading"
        selectable
        compact
        title="菜单与入口治理"
        subtitle="先维护菜单资源，再决定哪些维度可以看见它、操作它。"
      >
        <template #toolbar>
          <el-button
            v-if="canWrite"
            v-button-permission="{ code: 'iam_menu:create', fallback: 'iam:menu:write' }"
            type="primary"
            @click="openMenuCreate"
          >新增菜单</el-button>
        </template>
        <template #bulkActions="{ rows }">
          <el-button v-if="canWrite" type="primary" plain @click="batchEnableMenus(asMenuRows(rows))">批量启用</el-button>
          <el-button v-if="canWrite" plain @click="batchDisableMenus(asMenuRows(rows))">批量停用</el-button>
        </template>
        <template #status="{ row }">
          <BaseStatusTag :status="String(row.status)" :type="row.status === 'ENABLED' ? 'success' : 'danger'" />
        </template>
        <template #actions="{ row }">
          <el-button
            v-if="canWrite"
            v-button-permission="{ code: 'iam_menu:edit', fallback: 'iam:menu:write' }"
            link
            type="primary"
            @click="openMenuEdit(row)"
          >编辑</el-button>
          <el-button
            v-if="canWrite"
            v-button-permission="{ code: 'iam_menu:toggle', fallback: 'iam:menu:write' }"
            link
            @click="toggleMenu(row)"
          >{{ row.status === "ENABLED" ? "停用" : "启用" }}</el-button>
        </template>
      </ProTable>

      <ProTable
        :columns="menuPermissionColumns"
        :data="decoratedMenuPermissions"
        :loading="loading"
        selectable
        compact
        title="菜单权限矩阵"
        subtitle="按个人、部门、岗位、公司、租户、角色六个维度分配菜单可见范围，并可单独配置按钮动作。"
      >
        <template #toolbar>
          <el-button
            v-if="canWrite"
            v-button-permission="{ code: 'iam_menu_permission:create', fallback: 'iam:menu:write' }"
            type="primary"
            @click="openPermissionCreate"
          >新增菜单权限</el-button>
        </template>
        <template #bulkActions="{ rows }">
          <el-button v-if="canWrite" type="primary" plain @click="batchUpdatePermissions(asPermissionRows(rows), 'ENABLED')">批量启用</el-button>
          <el-button v-if="canWrite" plain @click="batchUpdatePermissions(asPermissionRows(rows), 'DISABLED')">批量停用</el-button>
        </template>
        <template #status="{ row }">
          <BaseStatusTag :status="String(row.status)" :type="row.status === 'ENABLED' ? 'success' : 'danger'" />
        </template>
        <template #actions="{ row }">
          <el-button
            v-if="canWrite"
            v-button-permission="{ code: 'iam_menu_permission:edit', fallback: 'iam:menu:write' }"
            link
            type="primary"
            @click="openPermissionEdit(row)"
          >编辑</el-button>
          <el-button
            v-if="canWrite"
            v-button-permission="{ code: 'iam_menu_permission:toggle', fallback: 'iam:menu:write' }"
            link
            @click="togglePermission(row)"
          >{{ row.status === "ENABLED" ? "停用" : "启用" }}</el-button>
        </template>
      </ProTable>
    </div>

    <el-dialog v-model="permissionDialog.visible" :title="permissionDialog.mode === 'create' ? '新增菜单权限' : '编辑菜单权限'" width="720px">
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="菜单">
            <el-select v-model="permissionForm.menuId" filterable style="width: 100%">
              <el-option v-for="item in menuOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="授权维度">
            <el-select v-model="permissionForm.subjectType" style="width: 100%">
              <el-option v-for="item in subjectTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="授权对象">
            <el-select v-model="permissionForm.subjectValue" filterable style="width: 100%">
              <el-option v-for="item in subjectValueOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="permissionForm.status" style="width: 100%">
              <el-option label="启用" value="ENABLED" />
              <el-option label="停用" value="DISABLED" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="按钮权限">
          <el-input
            v-model="permissionForm.buttonCodesText"
            placeholder="多个按钮用英文逗号分隔，例如 create,edit,delete,export"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="permissionForm.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="permissionDialog.visible = false">取消</el-button>
        <el-button v-button-permission="{ code: 'iam_menu_permission:save', fallback: 'iam:menu:write' }" type="primary" @click="submitPermission">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.menu-center-grid {
  display: grid;
  gap: 16px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.menu-center-card {
  grid-column: 1 / -1;
}

.form-grid {
  display: grid;
  gap: 16px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

@media (max-width: 1280px) {
  .menu-center-grid,
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
