import { http } from "@/api/http";
import type { ApiResponse } from "@/types/http";
import type {
  IamApiResourceRow,
  IamApiResourceSavePayload,
  IamDataPermissionRuleRow,
  IamDataPermissionRuleSavePayload,
  IamDataScopeRow,
  IamDataScopeSavePayload,
  IamDepartmentRow,
  IamDepartmentSavePayload,
  IamEmployeeRow,
  IamEmployeeSavePayload,
  IamEmployeeTransferPayload,
  IamLoginPolicyRow,
  IamLoginPolicySavePayload,
  IamMenuRow,
  IamMenuSavePayload,
  IamMenuPermissionRow,
  IamMenuPermissionSavePayload,
  IamMenuSubjectGrantSavePayload,
  IamOverview,
  IamPasswordPolicyRow,
  IamPasswordPolicySavePayload,
  IamPositionRow,
  IamPositionSavePayload,
  IamRoleRow,
  IamRoleSavePayload,
  IamUserRow,
  IamUserSavePayload,
  RoleApiAssignment,
  StatusPayload,
  UserRoleAssignment
} from "./types";

export async function fetchIamOverview(): Promise<IamOverview> {
  const response = await http.get<ApiResponse<IamOverview>>("/iam/overview");
  return response.data.data;
}

export async function fetchIamUsers(): Promise<IamUserRow[]> {
  const response = await http.get<ApiResponse<IamUserRow[]>>("/iam/users");
  return response.data.data;
}

export async function fetchIamMenus(options?: { timeout?: number }): Promise<IamMenuRow[]> {
  const response = await http.get<ApiResponse<IamMenuRow[]>>("/iam/menus", {
    timeout: options?.timeout
  });
  return response.data.data;
}

export async function fetchCurrentIamMenus(options?: { timeout?: number }): Promise<IamMenuRow[]> {
  const response = await http.get<ApiResponse<IamMenuRow[]>>("/iam/menus/current", {
    timeout: options?.timeout
  });
  return response.data.data;
}

export async function createIamMenu(payload: IamMenuSavePayload): Promise<IamMenuRow> {
  const response = await http.post<ApiResponse<IamMenuRow>>("/iam/menus", payload);
  return response.data.data;
}

export async function updateIamMenu(id: number, payload: IamMenuSavePayload): Promise<IamMenuRow> {
  const response = await http.put<ApiResponse<IamMenuRow>>(`/iam/menus/${id}`, payload);
  return response.data.data;
}

export async function updateIamMenuStatus(id: number, payload: StatusPayload): Promise<IamMenuRow> {
  const response = await http.patch<ApiResponse<IamMenuRow>>(`/iam/menus/${id}/status`, payload);
  return response.data.data;
}

export async function replaceIamMenuSubjectGrants(
  payload: IamMenuSubjectGrantSavePayload
): Promise<IamMenuPermissionRow[]> {
  const response = await http.put<ApiResponse<IamMenuPermissionRow[]>>(
    "/iam/menu-permissions/subject-grants",
    payload
  );
  return response.data.data;
}

export async function fetchIamDepartments(): Promise<IamDepartmentRow[]> {
  const response = await http.get<ApiResponse<IamDepartmentRow[]>>("/iam/departments");
  return response.data.data;
}

export async function createIamDepartment(payload: IamDepartmentSavePayload): Promise<IamDepartmentRow> {
  const response = await http.post<ApiResponse<IamDepartmentRow>>("/iam/departments", payload);
  return response.data.data;
}

export async function updateIamDepartment(id: number, payload: IamDepartmentSavePayload): Promise<IamDepartmentRow> {
  const response = await http.put<ApiResponse<IamDepartmentRow>>(`/iam/departments/${id}`, payload);
  return response.data.data;
}

export async function updateIamDepartmentStatus(id: number, payload: StatusPayload): Promise<IamDepartmentRow> {
  const response = await http.patch<ApiResponse<IamDepartmentRow>>(`/iam/departments/${id}/status`, payload);
  return response.data.data;
}

export async function fetchIamPositions(): Promise<IamPositionRow[]> {
  const response = await http.get<ApiResponse<IamPositionRow[]>>("/iam/positions");
  return response.data.data;
}

export async function createIamPosition(payload: IamPositionSavePayload): Promise<IamPositionRow> {
  const response = await http.post<ApiResponse<IamPositionRow>>("/iam/positions", payload);
  return response.data.data;
}

export async function updateIamPosition(id: number, payload: IamPositionSavePayload): Promise<IamPositionRow> {
  const response = await http.put<ApiResponse<IamPositionRow>>(`/iam/positions/${id}`, payload);
  return response.data.data;
}

export async function updateIamPositionStatus(id: number, payload: StatusPayload): Promise<IamPositionRow> {
  const response = await http.patch<ApiResponse<IamPositionRow>>(`/iam/positions/${id}/status`, payload);
  return response.data.data;
}

export async function fetchIamEmployees(): Promise<IamEmployeeRow[]> {
  const response = await http.get<ApiResponse<IamEmployeeRow[]>>("/iam/employees");
  return response.data.data;
}

export async function createIamEmployee(payload: IamEmployeeSavePayload): Promise<IamEmployeeRow> {
  const response = await http.post<ApiResponse<IamEmployeeRow>>("/iam/employees", payload);
  return response.data.data;
}

export async function updateIamEmployee(id: number, payload: IamEmployeeSavePayload): Promise<IamEmployeeRow> {
  const response = await http.put<ApiResponse<IamEmployeeRow>>(`/iam/employees/${id}`, payload);
  return response.data.data;
}

export async function transferIamEmployee(id: number, payload: IamEmployeeTransferPayload): Promise<IamEmployeeRow> {
  const response = await http.post<ApiResponse<IamEmployeeRow>>(`/iam/employees/${id}/transfer`, payload);
  return response.data.data;
}

export async function updateIamEmployeeStatus(id: number, payload: StatusPayload): Promise<IamEmployeeRow> {
  const response = await http.patch<ApiResponse<IamEmployeeRow>>(`/iam/employees/${id}/status`, payload);
  return response.data.data;
}

export async function fetchIamRoles(): Promise<IamRoleRow[]> {
  const response = await http.get<ApiResponse<IamRoleRow[]>>("/iam/roles");
  return response.data.data;
}

export async function createIamUser(payload: IamUserSavePayload): Promise<IamUserRow> {
  const response = await http.post<ApiResponse<IamUserRow>>("/iam/users", payload);
  return response.data.data;
}

export async function updateIamUser(id: number, payload: IamUserSavePayload): Promise<IamUserRow> {
  const response = await http.put<ApiResponse<IamUserRow>>(`/iam/users/${id}`, payload);
  return response.data.data;
}

export async function updateIamUserStatus(id: number, payload: StatusPayload): Promise<IamUserRow> {
  const response = await http.patch<ApiResponse<IamUserRow>>(`/iam/users/${id}/status`, payload);
  return response.data.data;
}

export async function createIamRole(payload: IamRoleSavePayload): Promise<IamRoleRow> {
  const response = await http.post<ApiResponse<IamRoleRow>>("/iam/roles", payload);
  return response.data.data;
}

export async function updateIamRole(id: number, payload: IamRoleSavePayload): Promise<IamRoleRow> {
  const response = await http.put<ApiResponse<IamRoleRow>>(`/iam/roles/${id}`, payload);
  return response.data.data;
}

export async function updateIamRoleStatus(id: number, payload: StatusPayload): Promise<IamRoleRow> {
  const response = await http.patch<ApiResponse<IamRoleRow>>(`/iam/roles/${id}/status`, payload);
  return response.data.data;
}

export async function fetchIamApiResources(): Promise<IamApiResourceRow[]> {
  const response = await http.get<ApiResponse<IamApiResourceRow[]>>("/iam/api-resources");
  return response.data.data;
}

export async function createIamApiResource(payload: IamApiResourceSavePayload): Promise<IamApiResourceRow> {
  const response = await http.post<ApiResponse<IamApiResourceRow>>("/iam/api-resources", payload);
  return response.data.data;
}

export async function updateIamApiResource(id: number, payload: IamApiResourceSavePayload): Promise<IamApiResourceRow> {
  const response = await http.put<ApiResponse<IamApiResourceRow>>(`/iam/api-resources/${id}`, payload);
  return response.data.data;
}

export async function updateIamApiResourceStatus(id: number, payload: StatusPayload): Promise<IamApiResourceRow> {
  const response = await http.patch<ApiResponse<IamApiResourceRow>>(`/iam/api-resources/${id}/status`, payload);
  return response.data.data;
}

export async function fetchIamDataScopes(): Promise<IamDataScopeRow[]> {
  const response = await http.get<ApiResponse<IamDataScopeRow[]>>("/iam/data-scopes");
  return response.data.data;
}

export async function fetchIamMenuPermissions(): Promise<IamMenuPermissionRow[]> {
  const response = await http.get<ApiResponse<IamMenuPermissionRow[]>>("/iam/menu-permissions");
  return response.data.data;
}

export async function createIamMenuPermission(payload: IamMenuPermissionSavePayload): Promise<IamMenuPermissionRow> {
  const response = await http.post<ApiResponse<IamMenuPermissionRow>>("/iam/menu-permissions", payload);
  return response.data.data;
}

export async function updateIamMenuPermission(id: number, payload: IamMenuPermissionSavePayload): Promise<IamMenuPermissionRow> {
  const response = await http.put<ApiResponse<IamMenuPermissionRow>>(`/iam/menu-permissions/${id}`, payload);
  return response.data.data;
}

export async function updateIamMenuPermissionStatus(id: number, payload: StatusPayload): Promise<IamMenuPermissionRow> {
  const response = await http.patch<ApiResponse<IamMenuPermissionRow>>(`/iam/menu-permissions/${id}/status`, payload);
  return response.data.data;
}

export async function fetchIamDataPermissionRules(): Promise<IamDataPermissionRuleRow[]> {
  const response = await http.get<ApiResponse<IamDataPermissionRuleRow[]>>("/iam/data-permission-rules");
  return response.data.data;
}

export async function createIamDataPermissionRule(payload: IamDataPermissionRuleSavePayload): Promise<IamDataPermissionRuleRow> {
  const response = await http.post<ApiResponse<IamDataPermissionRuleRow>>("/iam/data-permission-rules", payload);
  return response.data.data;
}

export async function updateIamDataPermissionRule(
  id: number,
  payload: IamDataPermissionRuleSavePayload
): Promise<IamDataPermissionRuleRow> {
  const response = await http.put<ApiResponse<IamDataPermissionRuleRow>>(`/iam/data-permission-rules/${id}`, payload);
  return response.data.data;
}

export async function updateIamDataPermissionRuleStatus(
  id: number,
  payload: StatusPayload
): Promise<IamDataPermissionRuleRow> {
  const response = await http.patch<ApiResponse<IamDataPermissionRuleRow>>(`/iam/data-permission-rules/${id}/status`, payload);
  return response.data.data;
}

export async function createIamDataScope(payload: IamDataScopeSavePayload): Promise<IamDataScopeRow> {
  const response = await http.post<ApiResponse<IamDataScopeRow>>("/iam/data-scopes", payload);
  return response.data.data;
}

export async function updateIamDataScope(id: number, payload: IamDataScopeSavePayload): Promise<IamDataScopeRow> {
  const response = await http.put<ApiResponse<IamDataScopeRow>>(`/iam/data-scopes/${id}`, payload);
  return response.data.data;
}

export async function updateIamDataScopeStatus(id: number, payload: StatusPayload): Promise<IamDataScopeRow> {
  const response = await http.patch<ApiResponse<IamDataScopeRow>>(`/iam/data-scopes/${id}/status`, payload);
  return response.data.data;
}

export async function fetchIamLoginPolicies(): Promise<IamLoginPolicyRow[]> {
  const response = await http.get<ApiResponse<IamLoginPolicyRow[]>>("/iam/login-policies");
  return response.data.data;
}

export async function createIamLoginPolicy(payload: IamLoginPolicySavePayload): Promise<IamLoginPolicyRow> {
  const response = await http.post<ApiResponse<IamLoginPolicyRow>>("/iam/login-policies", payload);
  return response.data.data;
}

export async function updateIamLoginPolicy(id: number, payload: IamLoginPolicySavePayload): Promise<IamLoginPolicyRow> {
  const response = await http.put<ApiResponse<IamLoginPolicyRow>>(`/iam/login-policies/${id}`, payload);
  return response.data.data;
}

export async function updateIamLoginPolicyStatus(id: number, payload: StatusPayload): Promise<IamLoginPolicyRow> {
  const response = await http.patch<ApiResponse<IamLoginPolicyRow>>(`/iam/login-policies/${id}/status`, payload);
  return response.data.data;
}

export async function fetchIamPasswordPolicies(): Promise<IamPasswordPolicyRow[]> {
  const response = await http.get<ApiResponse<IamPasswordPolicyRow[]>>("/iam/password-policies");
  return response.data.data;
}

export async function createIamPasswordPolicy(payload: IamPasswordPolicySavePayload): Promise<IamPasswordPolicyRow> {
  const response = await http.post<ApiResponse<IamPasswordPolicyRow>>("/iam/password-policies", payload);
  return response.data.data;
}

export async function updateIamPasswordPolicy(id: number, payload: IamPasswordPolicySavePayload): Promise<IamPasswordPolicyRow> {
  const response = await http.put<ApiResponse<IamPasswordPolicyRow>>(`/iam/password-policies/${id}`, payload);
  return response.data.data;
}

export async function updateIamPasswordPolicyStatus(id: number, payload: StatusPayload): Promise<IamPasswordPolicyRow> {
  const response = await http.patch<ApiResponse<IamPasswordPolicyRow>>(`/iam/password-policies/${id}/status`, payload);
  return response.data.data;
}

export async function fetchUserRoleAssignment(userId: number, tenantId: number): Promise<UserRoleAssignment> {
  const response = await http.get<ApiResponse<UserRoleAssignment>>(`/iam/users/${userId}/roles`, {
    params: { tenantId }
  });
  return response.data.data;
}

export async function saveUserRoleAssignment(payload: UserRoleAssignment): Promise<UserRoleAssignment> {
  const response = await http.put<ApiResponse<UserRoleAssignment>>(
    `/iam/users/${payload.userId}/roles`,
    { tenantId: payload.tenantId, roleIds: payload.roleIds }
  );
  return response.data.data;
}

export async function fetchRoleApiAssignment(roleId: number, tenantId: number): Promise<RoleApiAssignment> {
  const response = await http.get<ApiResponse<RoleApiAssignment>>(`/iam/roles/${roleId}/api-resources`, {
    params: { tenantId }
  });
  return response.data.data;
}

export async function saveRoleApiAssignment(payload: RoleApiAssignment): Promise<RoleApiAssignment> {
  const response = await http.put<ApiResponse<RoleApiAssignment>>(
    `/iam/roles/${payload.roleId}/api-resources`,
    { tenantId: payload.tenantId, apiResourceIds: payload.apiResourceIds }
  );
  return response.data.data;
}
