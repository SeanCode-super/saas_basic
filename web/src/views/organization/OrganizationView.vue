<script setup lang="ts">
import { computed, onMounted } from "vue";
import { Plus, Suitcase, User } from "@element-plus/icons-vue";
import BaseRefreshButton from "@/components/base/BaseRefreshButton.vue";
import ControlSurface from "@/components/platform/ControlSurface.vue";
import ModuleWorkbench from "@/components/platform/ModuleWorkbench.vue";
import { useAuthStore } from "@/stores/modules/auth";
import { useIamCatalog } from "@/views/iam/useIamCatalog";
import { useIamEditors } from "@/views/iam/useIamEditors";
import {
  departmentColumns,
  employeeColumns,
  positionColumns
} from "@/views/iam/iam-columns";
import { localizeEmployeeStatus } from "@/views/iam/iam-meta";
import IamOrganizationSection from "@/views/iam/sections/IamOrganizationSection.vue";
import IamEditorDialogs from "@/views/iam/IamEditorDialogs.vue";

const authStore = useAuthStore();
const currentTenantId = computed(() => authStore.currentUser?.tenantId ?? 0);

const {
  loading,
  departments,
  positions,
  employees,
  users,
  roles,
  menus,
  loadData
} = useIamCatalog();

const departmentOptions = computed(() => [
  { label: "顶级部门", value: 0 },
  ...departments.value.map((item) => ({ label: item.deptFullName, value: item.id }))
]);

const userOptions = computed(() => [
  { label: "未指定", value: 0 },
  ...users.value.map((item) => ({ label: `${item.nickname} (${item.username})`, value: item.id }))
]);

const positionOptions = computed(() =>
  positions.value.map((item) => ({ label: `${item.positionName} (${item.positionCode})`, value: item.id }))
);

const employeeOptions = computed(() =>
  employees.value.map((item) => ({ label: `${item.employeeName} (${item.employeeNo})`, value: item.id }))
);

const menuParentOptions = computed(() => [
  { label: "顶级菜单", value: 0 },
  ...menus.value.filter((item) => item.menuType !== "BUTTON").map((item) => ({
    label: `${item.menuName} (${item.menuCode})`,
    value: item.id
  }))
]);

const editors = useIamEditors({
  loadData,
  getCurrentTenantId: () => currentTenantId.value,
  roles,
  menus,
  users,
  departments,
  positions,
  employees
});

const companyCount = computed(() => departments.value.filter((d) => Number(d.parentId ?? 0) <= 0).length);
const boundEmployeeCount = computed(
  () => employees.value.filter((item) => Number(item.boundUserCount ?? 0) > 0).length
);
const accountBindRate = computed(() =>
  employees.value.length > 0
    ? `${Math.round((boundEmployeeCount.value / employees.value.length) * 100)}%`
    : "0%"
);

const orgSummary = computed(() => [
  { label: "顶级法人 / 根组织", value: companyCount.value },
  { label: "部门组织单元", value: departments.value.length },
  { label: "在册员工总数", value: employees.value.length },
  { label: "账号已开通率", value: `${boundEmployeeCount.value}/${employees.value.length} (${accountBindRate.value})` }
]);

onMounted(() => {
  void loadData();
});
</script>

<template>
  <ControlSurface
    badge="组织主数据"
    title="组织与成员中心"
    description="集团法人主体、组织树、岗位体系与「员工入职一表通」（建档同步开通账号并授予角色）。"
  >
    <template #actions>
      <BaseRefreshButton :loading="loading" @click="loadData()" />
      <el-button
        v-button-permission="{ code: 'iam_position:create', fallback: 'iam:user:write' }"
        :icon="Suitcase"
        @click="editors.openPositionCreate()"
      >
        新建岗位
      </el-button>
      <el-button
        v-button-permission="{ code: 'iam_department:create', fallback: 'iam:user:write' }"
        :icon="Plus"
        @click="editors.openDepartmentCreate(0)"
      >
        新建根组织
      </el-button>
      <el-button
        v-button-permission="{ code: 'iam_employee:create', fallback: 'iam:user:write' }"
        type="primary"
        :icon="User"
        @click="editors.openEmployeeCreate()"
      >
        + 员工入职一表通
      </el-button>
    </template>

    <ModuleWorkbench
      eyebrow="组织与成员中心"
      title="组织架构与人岗编制总览"
      description="统一维护公司、部门、岗位及人员账号绑定关系。"
      :summary="orgSummary"
    >
      <IamOrganizationSection
        mode="all"
        :department-columns="departmentColumns"
        :position-columns="positionColumns"
        :employee-columns="employeeColumns"
        :departments="departments"
        :positions="positions"
        :employees="employees"
        :loading="loading"
        :localize-employee-status="localizeEmployeeStatus"
        :open-department-create="editors.openDepartmentCreate"
        :open-department-edit="editors.openDepartmentEdit"
        :toggle-department="editors.toggleDepartment"
        :batch-enable-departments="editors.batchEnableDepartments"
        :batch-disable-departments="editors.batchDisableDepartments"
        :open-position-create="editors.openPositionCreate"
        :open-position-edit="editors.openPositionEdit"
        :toggle-position="editors.togglePosition"
        :batch-enable-positions="editors.batchEnablePositions"
        :batch-disable-positions="editors.batchDisablePositions"
        :open-employee-create="editors.openEmployeeCreate"
        :open-employee-edit="editors.openEmployeeEdit"
        :open-employee-transfer="editors.openEmployeeTransfer"
        :toggle-employee="editors.toggleEmployee"
        :batch-enable-employees="editors.batchEnableEmployees"
        :batch-disable-employees="editors.batchDisableEmployees"
      />
    </ModuleWorkbench>

    <!-- 弹窗统一挂载 -->
    <IamEditorDialogs
      :submitting="editors.submitting.value"
      :department-dialog="editors.departmentDialog"
      :position-dialog="editors.positionDialog"
      :employee-dialog="editors.employeeDialog"
      :user-dialog="editors.userDialog"
      :role-dialog="editors.roleDialog"
      :api-dialog="editors.apiDialog"
      :menu-dialog="editors.menuDialog"
      :data-scope-dialog="editors.dataScopeDialog"
      :login-policy-dialog="editors.loginPolicyDialog"
      :password-policy-dialog="editors.passwordPolicyDialog"
      :department-form="editors.departmentForm"
      :position-form="editors.positionForm"
      :employee-form="editors.employeeForm"
      :user-form="editors.userForm"
      :role-form="editors.roleForm"
      :api-form="editors.apiForm"
      :menu-form="editors.menuForm"
      :data-scope-form="editors.dataScopeForm"
      :login-policy-form="editors.loginPolicyForm"
      :password-policy-form="editors.passwordPolicyForm"
      :department-options="departmentOptions"
      :user-options="userOptions"
      :position-options="positionOptions"
      :employee-options="employeeOptions"
      :menu-parent-options="menuParentOptions"
      :departments="departments"
      :menus="menus"
      :positions="positions"
      :roles="roles"
      :submit-department="editors.submitDepartment"
      :submit-position="editors.submitPosition"
      :submit-employee="editors.submitEmployee"
      :submit-user="editors.submitUser"
      :submit-role="editors.submitRole"
      :submit-api-resource="editors.submitApiResource"
      :submit-menu="editors.submitMenu"
      :submit-data-scope="editors.submitDataScope"
      :submit-login-policy="editors.submitLoginPolicy"
      :submit-password-policy="editors.submitPasswordPolicy"
    />
  </ControlSurface>
</template>
