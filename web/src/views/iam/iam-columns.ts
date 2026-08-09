import type { IamLoginPolicyRow, IamPasswordPolicyRow } from "@/api/modules/iam";
import type { ProTableColumn } from "@/components/pro/ProTable.vue";
import {
  localizeDataScope,
  localizeMenuType,
  localizeRoleType,
  localizeScopeType,
  localizeSubjectType,
  localizeUserType,
  summarizeLoginChannels,
  summarizePasswordRules
} from "./iam-meta";

export const departmentColumns: ProTableColumn[] = [
  { prop: "deptCode", label: "部门编码", minWidth: 140 },
  {
    prop: "deptName",
    label: "部门名称",
    minWidth: 220,
    formatter: (row) => `${"— ".repeat(Math.max(Number(row.treeLevel ?? 1) - 1, 0))}${String(row.deptName ?? "")}`
  },
  { prop: "deptFullName", label: "部门全称", minWidth: 220 },
  { prop: "leaderName", label: "负责人", minWidth: 140, formatter: (row) => String(row.leaderName ?? "-") },
  { prop: "childCount", label: "下级部门", minWidth: 100, formatter: (row) => String(Number(row.childCount ?? 0)) },
  { prop: "employeeCount", label: "在编人数", minWidth: 100, formatter: (row) => String(Number(row.employeeCount ?? 0)) },
  { prop: "sortNo", label: "排序", minWidth: 90 },
  { prop: "status", label: "状态", minWidth: 100, slot: "status" },
  { prop: "actions", label: "操作", minWidth: 240, slot: "actions" }
];

export const positionColumns: ProTableColumn[] = [
  { prop: "positionCode", label: "岗位编码", minWidth: 150 },
  { prop: "positionName", label: "岗位名称", minWidth: 180 },
  { prop: "positionLevel", label: "岗位等级", minWidth: 120 },
  { prop: "employeeCount", label: "在岗人数", minWidth: 100, formatter: (row) => String(Number(row.employeeCount ?? 0)) },
  { prop: "sortNo", label: "排序", minWidth: 90 },
  { prop: "status", label: "状态", minWidth: 100, slot: "status" },
  { prop: "actions", label: "操作", minWidth: 170, slot: "actions" }
];

export const employeeColumns: ProTableColumn[] = [
  { prop: "employeeNo", label: "员工编号", minWidth: 140 },
  { prop: "employeeName", label: "员工姓名", minWidth: 150 },
  { prop: "deptFullName", label: "所属部门", minWidth: 220, formatter: (row) => String(row.deptFullName ?? row.deptName ?? "-") },
  { prop: "positionName", label: "岗位", minWidth: 140 },
  { prop: "hireDate", label: "入职日期", minWidth: 120, formatter: (row) => String(row.hireDate ?? "-") },
  { prop: "boundUserCount", label: "账号绑定", minWidth: 100, formatter: (row) => String(Number(row.boundUserCount ?? 0)) },
  { prop: "employeeStatus", label: "员工状态", minWidth: 110, slot: "employeeStatus" },
  { prop: "actions", label: "操作", minWidth: 250, slot: "actions" }
];

export const userColumns: ProTableColumn[] = [
  { prop: "username", label: "用户名", minWidth: 160 },
  { prop: "nickname", label: "显示名", minWidth: 150 },
  { prop: "userType", label: "账号类型", minWidth: 120, formatter: (row) => localizeUserType(String(row.userType ?? "")) },
  { prop: "employeeId", label: "员工绑定", minWidth: 120, formatter: (row) => (Number(row.employeeId ?? 0) > 0 ? "已绑定" : "未绑定") },
  { prop: "status", label: "状态", minWidth: 100, slot: "status" },
  { prop: "actions", label: "操作", minWidth: 190, slot: "actions" }
];

export const roleColumns: ProTableColumn[] = [
  { prop: "roleCode", label: "角色编码", minWidth: 150 },
  { prop: "roleName", label: "角色名称", minWidth: 180 },
  { prop: "roleType", label: "角色类型", minWidth: 120, formatter: (row) => localizeRoleType(String(row.roleType ?? "")) },
  {
    prop: "dataScopeType",
    label: "数据范围",
    minWidth: 140,
    formatter: (row) => localizeDataScope(String(row.dataScopeType ?? ""))
  },
  { prop: "status", label: "状态", minWidth: 100, slot: "status" },
  { prop: "actions", label: "操作", minWidth: 170, slot: "actions" }
];

export const apiColumns: ProTableColumn[] = [
  { prop: "resourceCode", label: "权限编码", minWidth: 180 },
  { prop: "resourceName", label: "资源名称", minWidth: 180 },
  { prop: "httpMethod", label: "方法", minWidth: 90 },
  { prop: "urlPattern", label: "接口规则", minWidth: 220 },
  { prop: "status", label: "状态", minWidth: 100, slot: "status" },
  { prop: "actions", label: "操作", minWidth: 170, slot: "actions" }
];

export const menuColumns: ProTableColumn[] = [
  { prop: "menuName", label: "菜单名称", minWidth: 180 },
  { prop: "menuCode", label: "菜单编码", minWidth: 180 },
  { prop: "menuType", label: "类型", minWidth: 110, formatter: (row) => localizeMenuType(String(row.menuType ?? "")) },
  { prop: "routePath", label: "路由", minWidth: 180 },
  { prop: "permissionCode", label: "权限码", minWidth: 200 },
  { prop: "status", label: "状态", minWidth: 100, slot: "status" },
  { prop: "actions", label: "操作", minWidth: 170, slot: "actions" }
];

export const dataScopeColumns: ProTableColumn[] = [
  { prop: "scopeCode", label: "范围编码", minWidth: 160 },
  { prop: "scopeName", label: "范围名称", minWidth: 180 },
  { prop: "scopeType", label: "范围类型", minWidth: 140, formatter: (row) => localizeScopeType(String(row.scopeType ?? "")) },
  { prop: "scopeRuleJson", label: "规则表达式", minWidth: 240, formatter: (row) => String(row.scopeRuleJson ?? "-") },
  { prop: "status", label: "状态", minWidth: 100, slot: "status" },
  { prop: "actions", label: "操作", minWidth: 170, slot: "actions" }
];

export const menuPermissionColumns: ProTableColumn[] = [
  { prop: "menuCode", label: "菜单编码", minWidth: 180 },
  { prop: "subjectType", label: "授权维度", minWidth: 110, formatter: (row) => localizeSubjectType(String(row.subjectType ?? "")) },
  { prop: "subjectLabel", label: "授权对象", minWidth: 180 },
  { prop: "buttonCodesSummary", label: "按钮权限", minWidth: 220 },
  { prop: "status", label: "状态", minWidth: 100, slot: "status" },
  { prop: "actions", label: "操作", minWidth: 170, slot: "actions" }
];

export const dataPermissionRuleColumns: ProTableColumn[] = [
  { prop: "resourceCode", label: "资源编码", minWidth: 180 },
  { prop: "subjectType", label: "授权维度", minWidth: 110, formatter: (row) => localizeSubjectType(String(row.subjectType ?? "")) },
  { prop: "subjectLabel", label: "授权对象", minWidth: 180 },
  { prop: "scopeType", label: "数据范围", minWidth: 140, formatter: (row) => localizeScopeType(String(row.scopeType ?? "")) },
  { prop: "configSummary", label: "自定义配置", minWidth: 240 },
  { prop: "status", label: "状态", minWidth: 100, slot: "status" },
  { prop: "actions", label: "操作", minWidth: 170, slot: "actions" }
];

export const loginPolicyColumns: ProTableColumn[] = [
  { prop: "policyCode", label: "策略编码", minWidth: 160 },
  { prop: "policyName", label: "策略名称", minWidth: 180 },
  {
    prop: "channels",
    label: "登录方式",
    minWidth: 190,
    formatter: (row) => summarizeLoginChannels(row as unknown as IamLoginPolicyRow)
  },
  { prop: "sessionTimeoutMinutes", label: "会话超时(分)", minWidth: 130 },
  { prop: "maxFailedCount", label: "失败阈值", minWidth: 110 },
  { prop: "status", label: "状态", minWidth: 100, slot: "status" },
  { prop: "actions", label: "操作", minWidth: 170, slot: "actions" }
];

export const passwordPolicyColumns: ProTableColumn[] = [
  { prop: "policyCode", label: "策略编码", minWidth: 160 },
  { prop: "policyName", label: "策略名称", minWidth: 180 },
  {
    prop: "minLength",
    label: "口令规则",
    minWidth: 240,
    formatter: (row) => summarizePasswordRules(row as unknown as IamPasswordPolicyRow)
  },
  { prop: "passwordExpireDays", label: "过期天数", minWidth: 110 },
  { prop: "passwordHistoryLimit", label: "历史记忆", minWidth: 110 },
  { prop: "status", label: "状态", minWidth: 100, slot: "status" },
  { prop: "actions", label: "操作", minWidth: 170, slot: "actions" }
];
