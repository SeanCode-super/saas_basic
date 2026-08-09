<script setup lang="ts">
import BaseStatusTag from "@/components/base/BaseStatusTag.vue";
import ProTable from "@/components/pro/ProTable.vue";
import type { ProTableColumn } from "@/components/pro/ProTable.vue";
import type { IamRoleRow, IamUserRow } from "@/api/modules/iam";

defineProps<{
  mode?: "all" | "user" | "role";
  userColumns: ProTableColumn[];
  roleColumns: ProTableColumn[];
  users: IamUserRow[];
  roles: IamRoleRow[];
  loading: boolean;
  openUserCreate: () => void;
  openUserEdit: (row: IamUserRow) => void;
  toggleUser: (row: IamUserRow) => void;
  batchEnableUsers: (rows: IamUserRow[]) => void | Promise<void>;
  batchDisableUsers: (rows: IamUserRow[]) => void | Promise<void>;
  openRoleCreate: () => void;
  openRoleEdit: (row: IamRoleRow) => void;
  toggleRole: (row: IamRoleRow) => void;
  batchEnableRoles: (rows: IamRoleRow[]) => void | Promise<void>;
  batchDisableRoles: (rows: IamRoleRow[]) => void | Promise<void>;
}>();

function asUserRows(rows: object[]) {
  return rows as IamUserRow[];
}

function asRoleRows(rows: object[]) {
  return rows as IamRoleRow[];
}
</script>

<template>
  <div class="module-section-stack">
    <ProTable
      v-if="!mode || mode === 'all' || mode === 'user'"
      :columns="userColumns"
      :data="users"
      :loading="loading"
      selectable
      compact
      title="账号目录"
      subtitle="统一治理登录账号、可用状态和账户入口，确保账号与员工档案解耦。"
    >
      <template #toolbar>
        <el-button v-button-permission="{ code: 'iam_user:create', fallback: 'iam:user:write' }" type="primary" @click="openUserCreate">新增用户</el-button>
      </template>
      <template #bulkActions="{ rows }">
        <el-button type="primary" plain @click="batchEnableUsers(asUserRows(rows))">批量启用</el-button>
        <el-button plain @click="batchDisableUsers(asUserRows(rows))">批量停用</el-button>
      </template>
      <template #status="{ row }">
        <BaseStatusTag :status="String(row.status)" :type="row.status === 'ENABLED' ? 'success' : 'danger'" />
      </template>
      <template #actions="{ row }">
        <el-button v-button-permission="{ code: 'iam_user:edit', fallback: 'iam:user:write' }" link type="primary" @click="openUserEdit(row)">编辑</el-button>
        <el-button v-button-permission="{ code: 'iam_user:toggle', fallback: 'iam:user:write' }" link @click="toggleUser(row)">{{ row.status === "ENABLED" ? "停用" : "启用" }}</el-button>
      </template>
    </ProTable>

    <ProTable
      v-if="!mode || mode === 'all' || mode === 'role'"
      :columns="roleColumns"
      :data="roles"
      :loading="loading"
      selectable
      compact
      title="角色注册表"
      subtitle="角色只负责能力编排，不直接承载组织属性和业务参数。"
    >
      <template #toolbar>
        <el-button v-button-permission="{ code: 'iam_role:create', fallback: 'iam:role:write' }" type="primary" @click="openRoleCreate">新增角色</el-button>
      </template>
      <template #bulkActions="{ rows }">
        <el-button type="primary" plain @click="batchEnableRoles(asRoleRows(rows))">批量启用</el-button>
        <el-button plain @click="batchDisableRoles(asRoleRows(rows))">批量停用</el-button>
      </template>
      <template #status="{ row }">
        <BaseStatusTag :status="String(row.status)" :type="row.status === 'ENABLED' ? 'success' : 'danger'" />
      </template>
      <template #actions="{ row }">
        <el-button v-button-permission="{ code: 'iam_role:edit', fallback: 'iam:role:write' }" link type="primary" @click="openRoleEdit(row)">编辑</el-button>
        <el-button v-button-permission="{ code: 'iam_role:toggle', fallback: 'iam:role:write' }" link @click="toggleRole(row)">{{ row.status === "ENABLED" ? "停用" : "启用" }}</el-button>
      </template>
    </ProTable>
  </div>
</template>
