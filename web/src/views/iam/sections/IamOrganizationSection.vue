<script setup lang="ts">
import BaseStatusTag from "@/components/base/BaseStatusTag.vue";
import ProTable from "@/components/pro/ProTable.vue";
import type { ProTableColumn } from "@/components/pro/ProTable.vue";
import type { IamDepartmentRow, IamEmployeeRow, IamPositionRow } from "@/api/modules/iam";

defineProps<{
  mode?: "all" | "department" | "position" | "employee";
  departmentColumns: ProTableColumn[];
  positionColumns: ProTableColumn[];
  employeeColumns: ProTableColumn[];
  departments: IamDepartmentRow[];
  positions: IamPositionRow[];
  employees: IamEmployeeRow[];
  loading: boolean;
  localizeEmployeeStatus: (value: string) => string;
  openDepartmentCreate: () => void;
  openDepartmentEdit: (row: IamDepartmentRow) => void;
  toggleDepartment: (row: IamDepartmentRow) => void;
  batchEnableDepartments: (rows: IamDepartmentRow[]) => void | Promise<void>;
  batchDisableDepartments: (rows: IamDepartmentRow[]) => void | Promise<void>;
  openPositionCreate: () => void;
  openPositionEdit: (row: IamPositionRow) => void;
  togglePosition: (row: IamPositionRow) => void;
  batchEnablePositions: (rows: IamPositionRow[]) => void | Promise<void>;
  batchDisablePositions: (rows: IamPositionRow[]) => void | Promise<void>;
  openEmployeeCreate: () => void;
  openEmployeeEdit: (row: IamEmployeeRow) => void;
  openEmployeeTransfer: (row: IamEmployeeRow) => void;
  toggleEmployee: (row: IamEmployeeRow) => void;
  batchEnableEmployees: (rows: IamEmployeeRow[]) => void | Promise<void>;
  batchDisableEmployees: (rows: IamEmployeeRow[]) => void | Promise<void>;
}>();

function asDepartmentRows(rows: object[]) {
  return rows as IamDepartmentRow[];
}

function asPositionRows(rows: object[]) {
  return rows as IamPositionRow[];
}

function asEmployeeRows(rows: object[]) {
  return rows as IamEmployeeRow[];
}
</script>

<template>
  <div class="module-section-stack">
    <ProTable
      v-if="!mode || mode === 'all' || mode === 'department'"
      :columns="departmentColumns"
      :data="departments"
      :loading="loading"
      selectable
      compact
      title="部门台账"
      subtitle="部门主数据决定组织树、汇报链和数据权限边界，是企业底座的主干结构。"
    >
      <template #toolbar>
        <el-button v-button-permission="{ code: 'iam_department:create', fallback: 'iam:user:write' }" type="primary" @click="openDepartmentCreate">新增部门</el-button>
      </template>
      <template #bulkActions="{ rows }">
        <el-button type="primary" plain @click="batchEnableDepartments(asDepartmentRows(rows))">批量启用</el-button>
        <el-button plain @click="batchDisableDepartments(asDepartmentRows(rows))">批量停用</el-button>
      </template>
      <template #status="{ row }">
        <BaseStatusTag :status="String(row.status)" :type="row.status === 'ENABLED' ? 'success' : 'danger'" />
      </template>
      <template #actions="{ row }">
        <el-button v-button-permission="{ code: 'iam_department:edit', fallback: 'iam:user:write' }" link type="primary" @click="openDepartmentEdit(row)">编辑</el-button>
        <el-button v-button-permission="{ code: 'iam_department:toggle', fallback: 'iam:user:write' }" link @click="toggleDepartment(row)">{{ row.status === "ENABLED" ? "停用" : "启用" }}</el-button>
      </template>
    </ProTable>

    <ProTable
      v-if="!mode || mode === 'all' || mode === 'position'"
      :columns="positionColumns"
      :data="positions"
      :loading="loading"
      selectable
      compact
      title="岗位台账"
      subtitle="岗位用于表达责任和授权承接点，不能直接被角色和用户替代。"
    >
      <template #toolbar>
        <el-button v-button-permission="{ code: 'iam_position:create', fallback: 'iam:user:write' }" type="primary" @click="openPositionCreate">新增岗位</el-button>
      </template>
      <template #bulkActions="{ rows }">
        <el-button type="primary" plain @click="batchEnablePositions(asPositionRows(rows))">批量启用</el-button>
        <el-button plain @click="batchDisablePositions(asPositionRows(rows))">批量停用</el-button>
      </template>
      <template #status="{ row }">
        <BaseStatusTag :status="String(row.status)" :type="row.status === 'ENABLED' ? 'success' : 'danger'" />
      </template>
      <template #actions="{ row }">
        <el-button v-button-permission="{ code: 'iam_position:edit', fallback: 'iam:user:write' }" link type="primary" @click="openPositionEdit(row)">编辑</el-button>
        <el-button v-button-permission="{ code: 'iam_position:toggle', fallback: 'iam:user:write' }" link @click="togglePosition(row)">{{ row.status === "ENABLED" ? "停用" : "启用" }}</el-button>
      </template>
    </ProTable>

    <ProTable
      v-if="!mode || mode === 'all' || mode === 'employee'"
      :columns="employeeColumns"
      :data="employees"
      :loading="loading"
      selectable
      compact
      title="员工台账"
      subtitle="员工档案承接部门、岗位和账号的映射，是组织主数据和认证主数据之间的桥梁。"
    >
      <template #toolbar>
        <el-button v-button-permission="{ code: 'iam_employee:create', fallback: 'iam:user:write' }" type="primary" @click="openEmployeeCreate">新增员工</el-button>
      </template>
      <template #bulkActions="{ rows }">
        <el-button type="primary" plain @click="batchEnableEmployees(asEmployeeRows(rows))">批量启用</el-button>
        <el-button plain @click="batchDisableEmployees(asEmployeeRows(rows))">批量停用</el-button>
      </template>
      <template #employeeStatus="{ row }">
        <BaseStatusTag
          :status="localizeEmployeeStatus(String(row.employeeStatus))"
          :type="row.employeeStatus === 'ACTIVE' ? 'success' : 'warning'"
        />
      </template>
      <template #actions="{ row }">
        <el-button v-button-permission="{ code: 'iam_employee:edit', fallback: 'iam:user:write' }" link type="primary" @click="openEmployeeEdit(row)">编辑</el-button>
        <el-button v-button-permission="{ code: 'iam_employee:transfer', fallback: 'iam:user:write' }" link type="primary" @click="openEmployeeTransfer(row)">组织异动</el-button>
        <el-button v-button-permission="{ code: 'iam_employee:toggle', fallback: 'iam:user:write' }" link @click="toggleEmployee(row)">{{ row.employeeStatus === "ACTIVE" ? "停用" : "启用" }}</el-button>
      </template>
    </ProTable>
  </div>
</template>
