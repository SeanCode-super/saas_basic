import { computed, ref, watch, type Ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  fetchRoleApiAssignment,
  fetchUserRoleAssignment,
  saveRoleApiAssignment,
  saveUserRoleAssignment
} from "@/api/modules/iam";
import type { IamApiResourceRow, IamRoleRow, IamUserRow } from "@/api/modules/iam";

interface UseIamAssignmentsParams {
  users: Ref<IamUserRow[]>;
  roles: Ref<IamRoleRow[]>;
  apiResources: Ref<IamApiResourceRow[]>;
  getCurrentTenantId: () => number;
}

export function useIamAssignments(params: UseIamAssignmentsParams) {
  const assigningUser = ref(false);
  const assigningRole = ref(false);
  const selectedUserId = ref<number | null>(null);
  const selectedRoleId = ref<number | null>(null);
  const selectedUserRoleIds = ref<number[]>([]);
  const selectedRoleApiIds = ref<number[]>([]);

  const roleOptions = computed(() =>
    params.roles.value.map((role) => ({
      label: `${role.roleName} (${role.roleCode})`,
      value: role.id
    }))
  );

  const apiResourceOptions = computed(() =>
    params.apiResources.value.map((resource) => ({
      label: `${resource.resourceCode} · ${resource.httpMethod}`,
      value: resource.id
    }))
  );

  function resolveTenantId(entity: { tenantId?: number | null }) {
    return entity.tenantId ?? params.getCurrentTenantId();
  }

  function syncSelection(next: { selectedUserId: number | null; selectedRoleId: number | null }) {
    selectedUserId.value = next.selectedUserId;
    selectedRoleId.value = next.selectedRoleId;
  }

  function selectUser(userId: number | null) {
    selectedUserId.value = userId;
  }

  function selectRole(roleId: number | null) {
    selectedRoleId.value = roleId;
  }

  async function loadUserRoles() {
    const user = params.users.value.find((item) => item.id === selectedUserId.value);
    if (!user) {
      selectedUserRoleIds.value = [];
      return;
    }
    const assignment = await fetchUserRoleAssignment(user.id, resolveTenantId(user));
    selectedUserRoleIds.value = assignment.roleIds;
  }

  async function loadRoleApis() {
    const role = params.roles.value.find((item) => item.id === selectedRoleId.value);
    if (!role) {
      selectedRoleApiIds.value = [];
      return;
    }
    const assignment = await fetchRoleApiAssignment(role.id, resolveTenantId(role));
    selectedRoleApiIds.value = assignment.apiResourceIds;
  }

  async function handleSaveUserRoles() {
    const user = params.users.value.find((item) => item.id === selectedUserId.value);
    if (!user) {
      return;
    }
    assigningUser.value = true;
    try {
      await saveUserRoleAssignment({
        userId: user.id,
        tenantId: resolveTenantId(user),
        roleIds: selectedUserRoleIds.value
      });
      await loadUserRoles();
      ElMessage.success("用户角色分配已更新");
    } catch (error: any) {
      ElMessage.error(error?.response?.data?.message ?? "保存用户角色失败");
    } finally {
      assigningUser.value = false;
    }
  }

  async function handleSaveRoleApis() {
    const role = params.roles.value.find((item) => item.id === selectedRoleId.value);
    if (!role) {
      return;
    }
    assigningRole.value = true;
    try {
      await saveRoleApiAssignment({
        roleId: role.id,
        tenantId: resolveTenantId(role),
        apiResourceIds: selectedRoleApiIds.value
      });
      await loadRoleApis();
      ElMessage.success("角色权限分配已更新");
    } catch (error: any) {
      ElMessage.error(error?.response?.data?.message ?? "保存角色权限失败");
    } finally {
      assigningRole.value = false;
    }
  }

  async function batchApplyUserRoles(users: IamUserRow[]) {
    if (!users.length) {
      ElMessage.warning("请先选择要分配角色的账号");
      return;
    }
    if (!selectedUserRoleIds.value.length) {
      ElMessage.warning("请先在右侧编排器选择角色组合");
      return;
    }

    try {
      await ElMessageBox.confirm(
        `将当前角色组合批量应用到 ${users.length} 个账号，这会覆盖这些账号的角色绑定，是否继续？`,
        "批量分配角色",
        {
          type: "warning",
          confirmButtonText: "确认分配",
          cancelButtonText: "取消"
        }
      );
    } catch {
      return;
    }

    assigningUser.value = true;
    try {
      await Promise.all(
        users.map((user) =>
          saveUserRoleAssignment({
            userId: user.id,
            tenantId: resolveTenantId(user),
            roleIds: selectedUserRoleIds.value
          })
        )
      );
      if (users.some((user) => user.id === selectedUserId.value)) {
        await loadUserRoles();
      }
      ElMessage.success("已批量更新账号角色绑定");
    } catch (error: any) {
      ElMessage.error(error?.response?.data?.message ?? "批量分配账号角色失败");
    } finally {
      assigningUser.value = false;
    }
  }

  async function batchApplyRoleApis(roles: IamRoleRow[]) {
    if (!roles.length) {
      ElMessage.warning("请先选择要分配权限的角色");
      return;
    }
    if (!selectedRoleApiIds.value.length) {
      ElMessage.warning("请先在右侧编排器选择权限资源组合");
      return;
    }

    try {
      await ElMessageBox.confirm(
        `将当前权限资源组合批量应用到 ${roles.length} 个角色，这会覆盖这些角色的资源授权，是否继续？`,
        "批量分配权限",
        {
          type: "warning",
          confirmButtonText: "确认分配",
          cancelButtonText: "取消"
        }
      );
    } catch {
      return;
    }

    assigningRole.value = true;
    try {
      await Promise.all(
        roles.map((role) =>
          saveRoleApiAssignment({
            roleId: role.id,
            tenantId: resolveTenantId(role),
            apiResourceIds: selectedRoleApiIds.value
          })
        )
      );
      if (roles.some((role) => role.id === selectedRoleId.value)) {
        await loadRoleApis();
      }
      ElMessage.success("已批量更新角色权限绑定");
    } catch (error: any) {
      ElMessage.error(error?.response?.data?.message ?? "批量分配角色权限失败");
    } finally {
      assigningRole.value = false;
    }
  }

  watch(
    () => params.users.value,
    (nextUsers) => {
      if (!nextUsers.length) {
        selectedUserId.value = null;
        selectedUserRoleIds.value = [];
        return;
      }
      if (!nextUsers.some((item) => item.id === selectedUserId.value)) {
        selectedUserId.value = nextUsers[0].id;
      }
    },
    { immediate: true }
  );

  watch(
    () => params.roles.value,
    (nextRoles) => {
      if (!nextRoles.length) {
        selectedRoleId.value = null;
        selectedRoleApiIds.value = [];
        return;
      }
      if (!nextRoles.some((item) => item.id === selectedRoleId.value)) {
        selectedRoleId.value = nextRoles[0].id;
      }
    },
    { immediate: true }
  );

  return {
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
  };
}
