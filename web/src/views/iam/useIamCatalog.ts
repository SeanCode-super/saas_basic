import { ref } from "vue";
import {
  fetchIamApiResources,
  fetchIamDataPermissionRules,
  fetchIamDataScopes,
  fetchIamDepartments,
  fetchIamEmployees,
  fetchIamLoginPolicies,
  fetchIamMenus,
  fetchIamMenuPermissions,
  fetchIamOverview,
  fetchIamPasswordPolicies,
  fetchIamPositions,
  fetchIamRoles,
  fetchIamUsers
} from "@/api/modules/iam";
import type {
  IamApiResourceRow,
  IamDataPermissionRuleRow,
  IamDataScopeRow,
  IamDepartmentRow,
  IamEmployeeRow,
  IamLoginPolicyRow,
  IamMenuRow,
  IamMenuPermissionRow,
  IamOverview,
  IamPasswordPolicyRow,
  IamPositionRow,
  IamRoleRow,
  IamUserRow
} from "@/api/modules/iam";

export function useIamCatalog() {
  const loading = ref(false);
  const overview = ref<IamOverview | null>(null);
  const departments = ref<IamDepartmentRow[]>([]);
  const positions = ref<IamPositionRow[]>([]);
  const employees = ref<IamEmployeeRow[]>([]);
  const users = ref<IamUserRow[]>([]);
  const roles = ref<IamRoleRow[]>([]);
  const apiResources = ref<IamApiResourceRow[]>([]);
  const menus = ref<IamMenuRow[]>([]);
  const menuPermissions = ref<IamMenuPermissionRow[]>([]);
  const dataPermissionRules = ref<IamDataPermissionRuleRow[]>([]);
  const dataScopes = ref<IamDataScopeRow[]>([]);
  const loginPolicies = ref<IamLoginPolicyRow[]>([]);
  const passwordPolicies = ref<IamPasswordPolicyRow[]>([]);

  async function loadData(params?: {
    selectedUserId?: number | null;
    selectedRoleId?: number | null;
    onSelectionSync?: (next: { selectedUserId: number | null; selectedRoleId: number | null }) => void;
  }) {
    loading.value = true;
    try {
      const [
        nextOverview,
        nextDepartments,
        nextPositions,
        nextEmployees,
        nextUsers,
        nextRoles,
        nextApiResources,
        nextMenus,
        nextMenuPermissions,
        nextDataPermissionRules,
        nextDataScopes,
        nextLoginPolicies,
        nextPasswordPolicies
      ] = await Promise.all([
        fetchIamOverview(),
        fetchIamDepartments(),
        fetchIamPositions(),
        fetchIamEmployees(),
        fetchIamUsers(),
        fetchIamRoles(),
        fetchIamApiResources(),
        fetchIamMenus(),
        fetchIamMenuPermissions(),
        fetchIamDataPermissionRules(),
        fetchIamDataScopes(),
        fetchIamLoginPolicies(),
        fetchIamPasswordPolicies()
      ]);

      overview.value = nextOverview;
      departments.value = nextDepartments;
      positions.value = nextPositions;
      employees.value = nextEmployees;
      users.value = nextUsers;
      roles.value = nextRoles;
      apiResources.value = nextApiResources;
      menus.value = nextMenus;
      menuPermissions.value = nextMenuPermissions;
      dataPermissionRules.value = nextDataPermissionRules;
      dataScopes.value = nextDataScopes;
      loginPolicies.value = nextLoginPolicies;
      passwordPolicies.value = nextPasswordPolicies;

      if (params?.onSelectionSync) {
        const nextUserId =
          params.selectedUserId && nextUsers.some((item) => item.id === params.selectedUserId)
            ? params.selectedUserId
            : (nextUsers[0]?.id ?? null);
        const nextRoleId =
          params.selectedRoleId && nextRoles.some((item) => item.id === params.selectedRoleId)
            ? params.selectedRoleId
            : (nextRoles[0]?.id ?? null);
        params.onSelectionSync({
          selectedUserId: nextUserId,
          selectedRoleId: nextRoleId
        });
      }
    } finally {
      loading.value = false;
    }
  }

  return {
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
  };
}
