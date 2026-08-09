<script setup lang="ts">
import type { IamRoleRow, IamUserRow } from "@/api/modules/iam";
import BaseCard from "@/components/base/BaseCard.vue";
import BaseStatusTag from "@/components/base/BaseStatusTag.vue";
import ProTable from "@/components/pro/ProTable.vue";
import type { ProTableColumn } from "@/components/pro/ProTable.vue";

defineProps<{
  mode?: "all" | "user-role" | "role-api";
  userColumns: ProTableColumn[];
  roleColumns: ProTableColumn[];
  users: IamUserRow[];
  roles: IamRoleRow[];
  loading: boolean;
  assigningUser: boolean;
  assigningRole: boolean;
  roleOptions: Array<{ label: string; value: number }>;
  apiResourceOptions: Array<{ label: string; value: number }>;
  openUserEdit: (row: IamUserRow) => void;
  toggleUser: (row: IamUserRow) => void;
  batchEnableUsers: (rows: IamUserRow[]) => void | Promise<void>;
  batchDisableUsers: (rows: IamUserRow[]) => void | Promise<void>;
  selectUser: (userId: number | null) => void;
  openRoleEdit: (row: IamRoleRow) => void;
  toggleRole: (row: IamRoleRow) => void;
  batchEnableRoles: (rows: IamRoleRow[]) => void | Promise<void>;
  batchDisableRoles: (rows: IamRoleRow[]) => void | Promise<void>;
  selectRole: (roleId: number | null) => void;
  handleSaveUserRoles: () => void;
  handleSaveRoleApis: () => void;
  batchApplyUserRoles: (rows: IamUserRow[]) => void | Promise<void>;
  batchApplyRoleApis: (rows: IamRoleRow[]) => void | Promise<void>;
}>();

const selectedUserIdModel = defineModel<number | null>("selectedUserId");
const selectedRoleIdModel = defineModel<number | null>("selectedRoleId");
const selectedUserRoleIdsModel = defineModel<number[]>("selectedUserRoleIds", { default: [] });
const selectedRoleApiIdsModel = defineModel<number[]>("selectedRoleApiIds", { default: [] });

function asUserRows(rows: object[]) {
  return rows as IamUserRow[];
}

function asRoleRows(rows: object[]) {
  return rows as IamRoleRow[];
}
</script>

<template>
  <div class="assignment-stack">
    <div v-if="!mode || mode === 'all' || mode === 'user-role'" class="binding-grid">
      <ProTable
        v-if="!mode || mode === 'all' || mode === 'user-role'"
        :columns="userColumns"
        :data="users"
        :loading="loading"
        selectable
        compact
        title="账号与角色绑定"
        subtitle="账号承接最终访问身份，角色只作为授权组合项进入分配链路。"
        @row-click="selectUser(($event as IamUserRow).id)"
      >
        <template #bulkActions="{ rows }">
          <el-button type="primary" plain @click="batchEnableUsers(asUserRows(rows))">批量启用</el-button>
          <el-button plain @click="batchDisableUsers(asUserRows(rows))">批量停用</el-button>
          <el-button v-button-permission="{ code: 'iam_user_role:assign', fallback: 'iam:user-role:write' }" type="primary" @click="batchApplyUserRoles(asUserRows(rows))">按当前编排分配角色</el-button>
        </template>
        <template #status="{ row }">
          <BaseStatusTag :status="String(row.status)" :type="row.status === 'ENABLED' ? 'success' : 'danger'" />
        </template>
        <template #actions="{ row }">
          <el-button v-button-permission="{ code: 'iam_user:edit', fallback: 'iam:user:write' }" link type="primary" @click="openUserEdit(row)">编辑</el-button>
          <el-button v-button-permission="{ code: 'iam_user:toggle', fallback: 'iam:user:write' }" link @click="toggleUser(row)">{{ row.status === "ENABLED" ? "停用" : "启用" }}</el-button>
        </template>
      </ProTable>

      <BaseCard v-if="!mode || mode === 'all' || mode === 'user-role'">
        <template #header>
          <div class="header-stack">
            <h3>用户角色编排</h3>
            <p>把账号映射到角色，角色再驱动整个底座的资源访问范围。</p>
          </div>
        </template>

        <el-form label-position="top">
          <el-form-item label="账号">
            <el-select v-model="selectedUserIdModel" placeholder="请选择账号">
              <el-option
                v-for="user in users"
                :key="user.id"
                :label="`${user.nickname} (${user.username})`"
                :value="user.id"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="角色">
            <el-select v-model="selectedUserRoleIdsModel" multiple placeholder="请选择角色">
              <el-option
                v-for="item in roleOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>

          <el-button v-button-permission="{ code: 'iam_user_role:save', fallback: 'iam:user-role:write' }" type="primary" :loading="assigningUser" @click="handleSaveUserRoles">保存用户角色</el-button>
          <p class="assignment-hint">右侧当前角色组合可直接批量应用到左侧选中的账号。</p>
        </el-form>
      </BaseCard>
    </div>

    <div v-if="!mode || mode === 'all' || mode === 'role-api'" class="binding-grid">
      <ProTable
        v-if="!mode || mode === 'all' || mode === 'role-api'"
        :columns="roleColumns"
        :data="roles"
        :loading="loading"
        selectable
        compact
        title="角色与权限绑定"
        subtitle="角色负责聚合资源能力，再把接口和菜单权限回写到统一授权关系中。"
        @row-click="selectRole(($event as IamRoleRow).id)"
      >
        <template #bulkActions="{ rows }">
          <el-button type="primary" plain @click="batchEnableRoles(asRoleRows(rows))">批量启用</el-button>
          <el-button plain @click="batchDisableRoles(asRoleRows(rows))">批量停用</el-button>
          <el-button v-button-permission="{ code: 'iam_role_api:assign', fallback: 'iam:role-api:write' }" type="primary" @click="batchApplyRoleApis(asRoleRows(rows))">按当前编排分配权限</el-button>
        </template>
        <template #status="{ row }">
          <BaseStatusTag :status="String(row.status)" :type="row.status === 'ENABLED' ? 'success' : 'danger'" />
        </template>
        <template #actions="{ row }">
          <el-button v-button-permission="{ code: 'iam_role:edit', fallback: 'iam:role:write' }" link type="primary" @click="openRoleEdit(row)">编辑</el-button>
          <el-button v-button-permission="{ code: 'iam_role:toggle', fallback: 'iam:role:write' }" link @click="toggleRole(row)">{{ row.status === "ENABLED" ? "停用" : "启用" }}</el-button>
        </template>
      </ProTable>

      <BaseCard v-if="!mode || mode === 'all' || mode === 'role-api'">
        <template #header>
          <div class="header-stack">
            <h3>角色权限编排</h3>
            <p>接口权限由资源注册表驱动，角色只负责组合，不直接硬编码到业务里。</p>
          </div>
        </template>

        <el-form label-position="top">
          <el-form-item label="角色">
            <el-select v-model="selectedRoleIdModel" placeholder="请选择角色">
              <el-option
                v-for="item in roleOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="权限资源">
            <el-select v-model="selectedRoleApiIdsModel" multiple placeholder="请选择权限资源">
              <el-option
                v-for="item in apiResourceOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>

          <el-button v-button-permission="{ code: 'iam_role_api:save', fallback: 'iam:role-api:write' }" type="primary" :loading="assigningRole" @click="handleSaveRoleApis">保存角色权限</el-button>
          <p class="assignment-hint">右侧当前权限组合可直接批量应用到左侧选中的角色。</p>
        </el-form>
      </BaseCard>
    </div>
  </div>
</template>

<style scoped lang="scss">
.assignment-stack {
  display: grid;
  gap: 16px;
}

.assignment-hint {
  margin: 10px 0 0;
  color: var(--sb-text-secondary);
  font-size: 12px;
  line-height: 1.6;
}
</style>
