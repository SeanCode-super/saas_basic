export type DialogMode = "create" | "edit" | "transfer";

export interface IamDialogState {
  visible: boolean;
  mode: DialogMode;
  id: number;
}

export interface DepartmentFormState {
  parentId: number;
  deptCode: string;
  deptName: string;
  deptFullName: string;
  leaderUserId: number;
  status: string;
  sortNo: number;
  remark: string;
}

export interface PositionFormState {
  positionCode: string;
  positionName: string;
  positionLevel: string;
  status: string;
  sortNo: number;
  remark: string;
}

export interface EmployeeFormState {
  employeeNo: string;
  employeeName: string;
  deptId: number;
  positionId: number;
  mobile: string;
  email: string;
  gender: string;
  hireDate: string;
  employeeStatus: string;
  remark: string;
}

export interface UserFormState {
  userCode: string;
  username: string;
  nickname: string;
  employeeId: number;
  userType: string;
  status: string;
  mobile: string;
  email: string;
  password: string;
  needResetPassword: boolean;
  remark: string;
}

export interface RoleFormState {
  roleGroupId: number;
  roleCode: string;
  roleName: string;
  roleType: string;
  dataScopeType: string;
  status: string;
  system: boolean;
  sortNo: number;
  remark: string;
}

export interface ApiFormState {
  resourceCode: string;
  resourceName: string;
  httpMethod: string;
  urlPattern: string;
  authRequired: boolean;
  status: string;
  remark: string;
}

export interface MenuFormState {
  parentId: number;
  menuType: string;
  menuCode: string;
  menuName: string;
  routePath: string;
  componentPath: string;
  permissionCode: string;
  icon: string;
  visible: boolean;
  keepAlive: boolean;
  sortNo: number;
  status: string;
  metaJson: string;
  remark: string;
}

export interface DataScopeFormState {
  scopeCode: string;
  scopeName: string;
  scopeType: string;
  scopeRuleJson: string;
  status: string;
  remark: string;
}

export interface LoginPolicyFormState {
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
  ipAllowlistJson: string;
  deviceTrustDays: number;
  status: string;
  remark: string;
}

export interface PasswordPolicyFormState {
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
  remark: string;
}

export interface SelectOption {
  label: string;
  value: number;
}
