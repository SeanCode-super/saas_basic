<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import ControlSurface from "@/components/platform/ControlSurface.vue";
import BaseRefreshButton from "@/components/base/BaseRefreshButton.vue";
import { useAuthStore } from "@/stores/modules/auth";
import IamEditorDialogs from "./IamEditorDialogs.vue";
import {
  apiColumns,
  dataPermissionRuleColumns,
  dataScopeColumns,
  departmentColumns,
  employeeColumns,
  loginPolicyColumns,
  menuColumns,
  menuPermissionColumns,
  passwordPolicyColumns,
  positionColumns,
  roleColumns,
  userColumns
} from "./iam-columns";
import {
  buildMenuAtlas,
  IAM_POLICY_ROWS,
  localizeEmployeeStatus,
  normalizeIamSection,
  pathForIamSection,
  resolveIamControlCopy,
  resolveIamSectionFromPath,
  summarizeLoginChannels,
  summarizePasswordRules
} from "./iam-meta";
import type { IamSection } from "./iam-meta";
import { useIamAssignments } from "./useIamAssignments";
import { useIamCatalog } from "./useIamCatalog";
import { useIamEditors } from "./useIamEditors";
import IamAccountsSection from "./sections/IamAccountsSection.vue";
import IamAssignmentSection from "./sections/IamAssignmentSection.vue";
import IamDataScopeSection from "./sections/IamDataScopeSection.vue";
import IamFoundationSection from "./sections/IamFoundationSection.vue";
import IamLoginPolicySection from "./sections/IamLoginPolicySection.vue";
import IamMenuSection from "./sections/IamMenuSection.vue";
import IamOrganizationSection from "./sections/IamOrganizationSection.vue";
import IamPasswordPolicySection from "./sections/IamPasswordPolicySection.vue";
import IamPermissionsSection from "./sections/IamPermissionsSection.vue";
const props = withDefaults(
  defineProps<{
    section?: string;
  }>(),
  {
    section: ""
  }
);

const authStore = useAuthStore();
const route = useRoute();
const router = useRouter();
const activeTab = ref<IamSection>("foundation");
const {
  loading,
  overview,
  departments,
  positions,
  employees,
  users,
  roles,
  apiResources,
  menus,
  menuPermissions,
  dataPermissionRules,
  dataScopes,
  loginPolicies,
  passwordPolicies,
  loadData
} =
  useIamCatalog();
const {
  assigningUser,
  assigningRole,
  selectedUserId,
  selectedRoleId,
  selectedUserRoleIds,
  selectedRoleApiIds,
  roleOptions,
  apiResourceOptions,
  syncSelection,
  selectUser,
  selectRole,
  loadUserRoles,
  loadRoleApis,
  handleSaveUserRoles,
  handleSaveRoleApis,
  batchApplyUserRoles,
  batchApplyRoleApis
} = useIamAssignments({
  users,
  roles,
  apiResources,
  getCurrentTenantId
});

const departmentOptions = computed(() => [
  { label: "顶级部门", value: 0 },
  ...departments.value.map((item) => ({ label: item.deptFullName, value: item.id }))
]);

const menuParentOptions = computed(() => [
  { label: "顶级菜单", value: 0 },
  ...menus.value.map((item) => ({
    label: `${item.menuName} (${item.menuCode})`,
    value: item.id
  }))
]);

const menuAtlasApps = computed(() => buildMenuAtlas(menus.value));
const currentTenantId = computed(() => authStore.currentUser?.tenantId ?? 0);

const userOptions = computed(() => [
  { label: "未指定", value: 0 },
  ...users.value.map((item) => ({ label: `${item.nickname} (${item.username})`, value: item.id }))
]);

const positionOptions = computed(() =>
  positions.value.map((item) => ({ label: `${item.positionName} (${item.positionCode})`, value: item.id }))
);

const employeeOptions = computed(() => [
  { label: "未绑定", value: 0 },
  ...employees.value.map((item) => ({ label: `${item.employeeName} (${item.employeeNo})`, value: item.id }))
]);

function getCurrentTenantId() {
  const tenantId = authStore.currentUser?.tenantId;
  if (!tenantId) {
    throw new Error("当前登录态缺少租户信息");
  }
  return tenantId;
}

const {
  submitting,
  departmentDialog,
  positionDialog,
  employeeDialog,
  userDialog,
  roleDialog,
  apiDialog,
  menuDialog,
  dataScopeDialog,
  loginPolicyDialog,
  passwordPolicyDialog,
  departmentForm,
  positionForm,
  employeeForm,
  userForm,
  roleForm,
  apiForm,
  menuForm,
  dataScopeForm,
  loginPolicyForm,
  passwordPolicyForm,
  openDepartmentCreate,
  openDepartmentEdit,
  openPositionCreate,
  openPositionEdit,
  openEmployeeCreate,
  openEmployeeEdit,
  openEmployeeTransfer,
  openUserCreate,
  openUserEdit,
  openRoleCreate,
  openRoleEdit,
  openApiCreate,
  openApiEdit,
  openMenuCreate,
  openMenuEdit,
  openDataScopeCreate,
  openDataScopeEdit,
  openLoginPolicyCreate,
  openLoginPolicyEdit,
  openPasswordPolicyCreate,
  openPasswordPolicyEdit,
  submitDepartment,
  submitPosition,
  submitEmployee,
  submitUser,
  submitRole,
  submitApiResource,
  submitMenu,
  submitDataScope,
  submitLoginPolicy,
  submitPasswordPolicy,
  toggleDepartment,
  togglePosition,
  toggleEmployee,
  toggleUser,
  toggleRole,
  toggleApiResource,
  toggleMenu,
  toggleDataScope,
  toggleLoginPolicy,
  togglePasswordPolicy,
  batchEnableDepartments,
  batchDisableDepartments,
  batchEnablePositions,
  batchDisablePositions,
  batchEnableEmployees,
  batchDisableEmployees,
  batchEnableUsers,
  batchDisableUsers,
  batchEnableRoles,
  batchDisableRoles,
  batchEnableApiResources,
  batchDisableApiResources,
  batchEnableMenus,
  batchDisableMenus,
  batchEnableDataScopes,
  batchDisableDataScopes,
  batchEnableLoginPolicies,
  batchDisableLoginPolicies,
  batchEnablePasswordPolicies,
  batchDisablePasswordPolicies
} = useIamEditors({
  getCurrentTenantId,
  loadData: refreshData,
  departments,
  positions,
  employees,
  users,
  roles,
  menus
});

const activeControlCopy = computed(() => resolveIamControlCopy(activeTab.value));
const policyRows = [...IAM_POLICY_ROWS];

async function refreshData() {
  await loadData({
    selectedUserId: selectedUserId.value,
    selectedRoleId: selectedRoleId.value,
    onSelectionSync: syncSelection
  });
}

function handleMenuAtlasSelect(item: { id: number | string }) {
  const target = menus.value.find((menu) => menu.id === Number(item.id));
  if (!target) {
    return;
  }
  openMenuEdit(target);
}

watch(selectedUserId, () => {
  void loadUserRoles();
});

watch(selectedRoleId, () => {
  void loadRoleApis();
});

watch(
  () => props.section,
  (value) => {
    if (!value) {
      return;
    }
    const next = normalizeIamSection(value, authStore.hasPermission);
    if (activeTab.value !== next) {
      activeTab.value = next;
    }
  },
  { immediate: true }
);

watch(
  () => route.path,
  () => {
    if (props.section) {
      return;
    }
    const next = resolveIamSectionFromPath(route.path, authStore.hasPermission) ?? "foundation";
    if (activeTab.value !== next) {
      activeTab.value = next;
    }

    const canonicalPath = pathForIamSection(next);
    if (route.path !== canonicalPath) {
      void router.replace({ path: canonicalPath });
    }
  },
  { immediate: true }
);

watch(activeTab, async (value) => {
  if (props.section) {
    return;
  }
  const current = resolveIamSectionFromPath(route.path, authStore.hasPermission) ?? "foundation";
  if (value === current) {
    return;
  }
  await router.replace({ path: pathForIamSection(value) });
});

onMounted(async () => {
  await refreshData();
  await Promise.all([loadUserRoles(), loadRoleApis()]);
});
</script>

<template>
  <ControlSurface
    eyebrow="身份与权限控制面"
    :title="activeControlCopy.title"
    :description="activeControlCopy.description"
  >
    <template #actions>
      <BaseRefreshButton :loading="loading" @click="refreshData" />
    </template>
    <IamFoundationSection v-if="activeTab === 'foundation'" :overview="overview" :policy-rows="policyRows" />

    <IamOrganizationSection
      v-else-if="activeTab === 'department' || activeTab === 'position' || activeTab === 'employee'"
      :mode="activeTab"
      :department-columns="departmentColumns"
      :position-columns="positionColumns"
      :employee-columns="employeeColumns"
      :departments="departments"
      :positions="positions"
      :employees="employees"
      :loading="loading"
      :localize-employee-status="localizeEmployeeStatus"
      :open-department-create="openDepartmentCreate"
      :open-department-edit="openDepartmentEdit"
      :toggle-department="toggleDepartment"
      :batch-enable-departments="batchEnableDepartments"
      :batch-disable-departments="batchDisableDepartments"
      :open-position-create="openPositionCreate"
      :open-position-edit="openPositionEdit"
      :toggle-position="togglePosition"
      :batch-enable-positions="batchEnablePositions"
      :batch-disable-positions="batchDisablePositions"
      :open-employee-create="openEmployeeCreate"
      :open-employee-edit="openEmployeeEdit"
      :open-employee-transfer="openEmployeeTransfer"
      :toggle-employee="toggleEmployee"
      :batch-enable-employees="batchEnableEmployees"
      :batch-disable-employees="batchDisableEmployees"
    />

    <IamAccountsSection
      v-else-if="activeTab === 'user' || activeTab === 'role'"
      :mode="activeTab"
      :user-columns="userColumns"
      :role-columns="roleColumns"
      :users="users"
      :roles="roles"
      :loading="loading"
      :open-user-create="openUserCreate"
      :open-user-edit="openUserEdit"
      :toggle-user="toggleUser"
      :batch-enable-users="batchEnableUsers"
      :batch-disable-users="batchDisableUsers"
      :open-role-create="openRoleCreate"
      :open-role-edit="openRoleEdit"
      :toggle-role="toggleRole"
      :batch-enable-roles="batchEnableRoles"
      :batch-disable-roles="batchDisableRoles"
    />

    <IamPermissionsSection
      v-else-if="activeTab === 'api-resource'"
      :api-columns="apiColumns"
      :api-resources="apiResources"
      :loading="loading"
      :open-api-create="openApiCreate"
      :open-api-edit="openApiEdit"
      :toggle-api-resource="toggleApiResource"
      :batch-enable-api-resources="batchEnableApiResources"
      :batch-disable-api-resources="batchDisableApiResources"
    />

    <IamMenuSection
      v-else-if="activeTab === 'menu'"
      :menu-atlas-apps="menuAtlasApps"
      :menu-columns="menuColumns"
      :menus="menus"
      :menu-permission-columns="menuPermissionColumns"
      :menu-permissions="menuPermissions"
      :loading="loading"
      :can-write="authStore.hasPermission('iam:menu:write')"
      :current-tenant-id="currentTenantId"
      :users="users"
      :roles="roles"
      :departments="departments"
      :positions="positions"
      :handle-menu-atlas-select="handleMenuAtlasSelect"
      :open-menu-create="openMenuCreate"
      :open-menu-edit="openMenuEdit"
      :toggle-menu="toggleMenu"
      :batch-enable-menus="batchEnableMenus"
      :batch-disable-menus="batchDisableMenus"
      :refresh-data="refreshData"
    />

    <IamDataScopeSection
      v-else-if="activeTab === 'data-scope'"
      :data-scope-columns="dataScopeColumns"
      :data-permission-rule-columns="dataPermissionRuleColumns"
      :data-scopes="dataScopes"
      :data-permission-rules="dataPermissionRules"
      :api-resources="apiResources"
      :users="users"
      :roles="roles"
      :departments="departments"
      :positions="positions"
      :loading="loading"
      :can-write="authStore.hasPermission('iam:data-scope:write')"
      :current-tenant-id="currentTenantId"
      :open-data-scope-create="openDataScopeCreate"
      :open-data-scope-edit="openDataScopeEdit"
      :toggle-data-scope="toggleDataScope"
      :batch-enable-data-scopes="batchEnableDataScopes"
      :batch-disable-data-scopes="batchDisableDataScopes"
      :refresh-data="refreshData"
    />

    <IamLoginPolicySection
      v-else-if="activeTab === 'login-policy'"
      :login-policy-columns="loginPolicyColumns"
      :login-policies="loginPolicies"
      :loading="loading"
      :can-write="authStore.hasPermission('iam:login-policy:write')"
      :summarize-login-channels="summarizeLoginChannels"
      :open-login-policy-create="openLoginPolicyCreate"
      :open-login-policy-edit="openLoginPolicyEdit"
      :toggle-login-policy="toggleLoginPolicy"
      :batch-enable-login-policies="batchEnableLoginPolicies"
      :batch-disable-login-policies="batchDisableLoginPolicies"
    />

    <IamPasswordPolicySection
      v-else-if="activeTab === 'password-policy'"
      :password-policy-columns="passwordPolicyColumns"
      :password-policies="passwordPolicies"
      :loading="loading"
      :can-write="authStore.hasPermission('iam:password-policy:write')"
      :summarize-password-rules="summarizePasswordRules"
      :open-password-policy-create="openPasswordPolicyCreate"
      :open-password-policy-edit="openPasswordPolicyEdit"
      :toggle-password-policy="togglePasswordPolicy"
      :batch-enable-password-policies="batchEnablePasswordPolicies"
      :batch-disable-password-policies="batchDisablePasswordPolicies"
    />

    <IamAssignmentSection
      v-else-if="activeTab === 'user-role' || activeTab === 'role-api'"
      :mode="activeTab"
      v-model:selected-user-id="selectedUserId"
      v-model:selected-role-id="selectedRoleId"
      v-model:selected-user-role-ids="selectedUserRoleIds"
      v-model:selected-role-api-ids="selectedRoleApiIds"
      :user-columns="userColumns"
      :role-columns="roleColumns"
      :users="users"
      :roles="roles"
      :loading="loading"
      :assigning-user="assigningUser"
      :assigning-role="assigningRole"
      :role-options="roleOptions"
      :api-resource-options="apiResourceOptions"
      :open-user-edit="openUserEdit"
      :toggle-user="toggleUser"
      :batch-enable-users="batchEnableUsers"
      :batch-disable-users="batchDisableUsers"
      :open-role-edit="openRoleEdit"
      :toggle-role="toggleRole"
      :batch-enable-roles="batchEnableRoles"
      :batch-disable-roles="batchDisableRoles"
      :select-user="selectUser"
      :select-role="selectRole"
      :handle-save-user-roles="handleSaveUserRoles"
      :handle-save-role-apis="handleSaveRoleApis"
      :batch-apply-user-roles="batchApplyUserRoles"
      :batch-apply-role-apis="batchApplyRoleApis"
    />
    <IamFoundationSection v-else :overview="overview" :policy-rows="policyRows" />
    <IamEditorDialogs
      :submitting="submitting"
      :department-dialog="departmentDialog"
      :position-dialog="positionDialog"
      :employee-dialog="employeeDialog"
      :user-dialog="userDialog"
      :role-dialog="roleDialog"
      :api-dialog="apiDialog"
      :menu-dialog="menuDialog"
      :data-scope-dialog="dataScopeDialog"
      :login-policy-dialog="loginPolicyDialog"
      :password-policy-dialog="passwordPolicyDialog"
      :department-form="departmentForm"
      :position-form="positionForm"
      :employee-form="employeeForm"
      :user-form="userForm"
      :role-form="roleForm"
      :api-form="apiForm"
      :menu-form="menuForm"
      :data-scope-form="dataScopeForm"
      :login-policy-form="loginPolicyForm"
      :password-policy-form="passwordPolicyForm"
      :department-options="departmentOptions"
      :user-options="userOptions"
      :position-options="positionOptions"
      :employee-options="employeeOptions"
      :menu-parent-options="menuParentOptions"
      :departments="departments"
      :positions="positions"
      :submit-department="submitDepartment"
      :submit-position="submitPosition"
      :submit-employee="submitEmployee"
      :submit-user="submitUser"
      :submit-role="submitRole"
      :submit-api-resource="submitApiResource"
      :submit-menu="submitMenu"
      :submit-data-scope="submitDataScope"
      :submit-login-policy="submitLoginPolicy"
      :submit-password-policy="submitPasswordPolicy"
    />
  </ControlSurface>
</template>
