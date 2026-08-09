export interface IamOverview {
  modelLayers: string[];
  capabilities: string[];
  counters: Record<string, number>;
}

export interface IamMenuRow {
  id: number;
  tenantId: number;
  parentId: number;
  menuType: string;
  menuCode: string;
  menuName: string;
  routePath?: string;
  componentPath?: string;
  permissionCode?: string;
  icon?: string;
  visible: boolean;
  keepAlive: boolean;
  sortNo: number;
  status: string;
  metaJson?: string;
  remark?: string;
}

export interface IamMenuSavePayload {
  tenantId: number;
  parentId?: number;
  menuType: string;
  menuCode: string;
  menuName: string;
  routePath?: string;
  componentPath?: string;
  permissionCode?: string;
  icon?: string;
  visible: boolean;
  keepAlive: boolean;
  sortNo?: number;
  status: string;
  metaJson?: string;
  remark?: string;
}

export interface IamDepartmentRow {
  id: number;
  tenantId: number;
  parentId: number;
  deptCode: string;
  deptName: string;
  deptFullName: string;
  treePath?: string;
  treeLevel?: number;
  leaderUserId: number;
  leaderName?: string;
  childCount?: number;
  employeeCount?: number;
  status: string;
  sortNo: number;
  remark?: string;
}

export interface IamDepartmentSavePayload {
  tenantId: number;
  parentId: number;
  deptCode: string;
  deptName: string;
  deptFullName: string;
  leaderUserId: number;
  status: string;
  sortNo: number;
  remark?: string;
}

export interface IamPositionRow {
  id: number;
  tenantId: number;
  positionCode: string;
  positionName: string;
  positionLevel: string;
  employeeCount?: number;
  status: string;
  sortNo: number;
  remark?: string;
}

export interface IamPositionSavePayload {
  tenantId: number;
  positionCode: string;
  positionName: string;
  positionLevel?: string;
  status: string;
  sortNo: number;
  remark?: string;
}

export interface IamEmployeeRow {
  id: number;
  tenantId: number;
  employeeNo: string;
  employeeName: string;
  deptId: number;
  deptName: string;
  deptFullName?: string;
  positionId: number;
  positionName: string;
  mobile?: string;
  email?: string;
  gender?: string;
  hireDate?: string;
  boundUserCount?: number;
  employeeStatus: string;
  remark?: string;
}

export interface IamEmployeeSavePayload {
  tenantId: number;
  employeeNo: string;
  employeeName: string;
  deptId: number;
  positionId: number;
  mobile?: string;
  email?: string;
  gender?: string;
  hireDate?: string;
  employeeStatus: string;
  remark?: string;
}

export interface IamEmployeeTransferPayload {
  deptId: number;
  positionId: number;
  remark?: string;
}

export interface IamUserRow {
  id: number;
  tenantId: number;
  userCode: string;
  username: string;
  nickname: string;
  employeeId: number;
  userType: string;
  status: string;
  mobile?: string;
  email?: string;
  remark?: string;
}

export interface IamUserSavePayload {
  tenantId: number;
  userCode: string;
  username: string;
  nickname: string;
  employeeId?: number;
  userType: string;
  status: string;
  mobile?: string;
  email?: string;
  password?: string;
  needResetPassword?: boolean;
  remark?: string;
}

export interface IamRoleRow {
  id: number;
  tenantId: number;
  roleGroupId: number;
  roleCode: string;
  roleName: string;
  roleType: string;
  dataScopeType: string;
  status: string;
  system: boolean;
  sortNo: number;
  remark?: string;
}

export interface IamRoleSavePayload {
  tenantId: number;
  roleGroupId?: number;
  roleCode: string;
  roleName: string;
  roleType: string;
  dataScopeType: string;
  status: string;
  system?: boolean;
  sortNo?: number;
  remark?: string;
}

export interface IamApiResourceRow {
  id: number;
  tenantId: number;
  resourceCode: string;
  resourceName: string;
  httpMethod: string;
  urlPattern: string;
  authRequired: boolean;
  status: string;
  remark?: string;
}

export interface IamApiResourceSavePayload {
  tenantId: number;
  resourceCode: string;
  resourceName: string;
  httpMethod: string;
  urlPattern: string;
  authRequired: boolean;
  status: string;
  remark?: string;
}

export interface IamDataScopeRow {
  id: number;
  tenantId: number;
  scopeCode: string;
  scopeName: string;
  scopeType: string;
  scopeRuleJson?: string;
  status: string;
  remark?: string;
}

export interface IamMenuPermissionRow {
  id: number;
  tenantId: number;
  menuId: number;
  menuCode: string;
  subjectType: string;
  subjectValue: string;
  buttonCodesJson?: string;
  status: string;
  remark?: string;
}

export interface IamMenuPermissionSavePayload {
  tenantId: number;
  menuId: number;
  menuCode?: string;
  subjectType: string;
  subjectValue: string;
  buttonCodesJson?: string;
  status: string;
  remark?: string;
}

export interface IamDataPermissionRuleRow {
  id: number;
  tenantId: number;
  resourceCode: string;
  resourceName: string;
  subjectType: string;
  subjectValue: string;
  scopeType: string;
  configJson?: string;
  status: string;
  remark?: string;
}

export interface IamDataPermissionRuleSavePayload {
  tenantId: number;
  resourceCode: string;
  resourceName: string;
  subjectType: string;
  subjectValue: string;
  scopeType: string;
  configJson?: string;
  status: string;
  remark?: string;
}

export interface IamDataScopeSavePayload {
  tenantId: number;
  scopeCode: string;
  scopeName: string;
  scopeType: string;
  scopeRuleJson?: string;
  status: string;
  remark?: string;
}

export interface IamLoginPolicyRow {
  id: number;
  tenantId: number;
  policyCode: string;
  policyName: string;
  allowPasswordLogin: boolean;
  allowSmsLogin: boolean;
  allowEmailLogin: boolean;
  allowSocialLogin: boolean;
  forceMfa: boolean;
  sessionTimeoutMinutes: number;
  maxFailedCount: number;
  lockMinutes: number;
  ipAllowlistJson?: string;
  deviceTrustDays: number;
  status: string;
  remark?: string;
}

export interface IamLoginPolicySavePayload {
  tenantId: number;
  policyCode: string;
  policyName: string;
  allowPasswordLogin: boolean;
  allowSmsLogin: boolean;
  allowEmailLogin: boolean;
  allowSocialLogin: boolean;
  forceMfa: boolean;
  sessionTimeoutMinutes: number;
  maxFailedCount: number;
  lockMinutes: number;
  ipAllowlistJson?: string;
  deviceTrustDays: number;
  status: string;
  remark?: string;
}

export interface IamPasswordPolicyRow {
  id: number;
  tenantId: number;
  policyCode: string;
  policyName: string;
  minLength: number;
  maxLength: number;
  requireUppercase: boolean;
  requireLowercase: boolean;
  requireNumber: boolean;
  requireSpecial: boolean;
  passwordHistoryLimit: number;
  passwordExpireDays: number;
  tempPasswordExpireHours: number;
  status: string;
  remark?: string;
}

export interface IamPasswordPolicySavePayload {
  tenantId: number;
  policyCode: string;
  policyName: string;
  minLength: number;
  maxLength: number;
  requireUppercase: boolean;
  requireLowercase: boolean;
  requireNumber: boolean;
  requireSpecial: boolean;
  passwordHistoryLimit: number;
  passwordExpireDays: number;
  tempPasswordExpireHours: number;
  status: string;
  remark?: string;
}

export interface StatusPayload {
  status: string;
}

export interface UserRoleAssignment {
  userId: number;
  tenantId: number;
  roleIds: number[];
}

export interface RoleApiAssignment {
  roleId: number;
  tenantId: number;
  apiResourceIds: number[];
}
