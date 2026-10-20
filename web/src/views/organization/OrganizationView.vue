<script setup lang="ts">
import { computed, onMounted } from "vue";
import { Plus, RefreshRight, Suitcase, User } from "@element-plus/icons-vue";
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
  roles: computed(() => []),
  menus,
  users,
  departments,
  positions,
  employees
});

const companyCount = computed(() => departments.value.filter((d) => Number(d.parentId ?? 0) <= 0).length);

onMounted(() => {
  loadData();
});
</script>

<template>
  <div class="organization-view">
    <!-- 顶部工作台精简控制条 (40px) -->
    <div class="org-toolbar-strip">
      <div class="strip-left">
        <span class="strip-title">组织与人员中心</span>
        <div class="divider"></div>
        <div class="metric-tags">
          <div class="metric-tag">
            <span class="label">法人主体</span>
            <span class="value">{{ companyCount }}</span>
          </div>
          <div class="metric-tag">
            <span class="label">部门单元</span>
            <span class="value">{{ departments.length }}</span>
          </div>
          <div class="metric-tag">
            <span class="label">在岗人员</span>
            <span class="value">{{ employees.length }}</span>
          </div>
          <div class="metric-tag">
            <span class="label">标准岗位</span>
            <span class="value">{{ positions.length }}</span>
          </div>
        </div>
      </div>

      <div class="strip-right">
        <el-button
          size="small"
          :icon="RefreshRight"
          :loading="loading"
          plain
          @click="loadData()"
        >
          刷新
        </el-button>
        <el-button
          v-button-permission="{ code: 'iam_position:create', fallback: 'iam:user:write' }"
          size="small"
          :icon="Suitcase"
          plain
          @click="editors.openPositionCreate()"
        >
          新建岗位
        </el-button>
        <el-button
          v-button-permission="{ code: 'iam_department:create', fallback: 'iam:user:write' }"
          size="small"
          :icon="Plus"
          plain
          @click="editors.openDepartmentCreate(0)"
        >
          新建根部门
        </el-button>
        <el-button
          v-button-permission="{ code: 'iam_employee:create', fallback: 'iam:user:write' }"
          size="small"
          type="primary"
          :icon="User"
          @click="editors.openEmployeeCreate()"
        >
          录入人员
        </el-button>
      </div>
    </div>

    <!-- 可视化架构工作台 -->
    <section class="org-main-section">
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
    </section>

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
  </div>
</template>

<style scoped lang="scss">
.organization-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 0;
}

.org-toolbar-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: #ffffff;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);

  .strip-left {
    display: flex;
    align-items: center;
    gap: 16px;
    flex-wrap: wrap;
  }

  .strip-title {
    font-size: 15px;
    font-weight: 700;
    color: #0f172a;
  }

  .divider {
    width: 1px;
    height: 14px;
    background: #e2e8f0;
  }

  .metric-tags {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .metric-tag {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 3px 10px;
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 6px;
    font-size: 12px;

    .label {
      color: #64748b;
    }

    .value {
      font-weight: 700;
      color: #0f172a;
      font-variant-numeric: tabular-nums;
    }
  }

  .strip-right {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }
}

.org-main-section {
  width: 100%;
}
</style>
