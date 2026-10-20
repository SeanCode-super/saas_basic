import type { IamLoginPolicyRow, IamMenuRow, IamPasswordPolicyRow } from "@/api/modules/iam";
import { resolveAppMeta } from "@/config/app-taxonomy";

export type IamSection =
  | "foundation"
  | "department"
  | "position"
  | "employee"
  | "user"
  | "role"
  | "api-resource"
  | "user-role"
  | "role-api"
  | "menu"
  | "data-scope"
  | "login-policy"
  | "password-policy";

export interface IamMenuAtlasNode {
  id: number;
  code: string;
  title: string;
  path: string;
  query: Record<string, string>;
  children: IamMenuAtlasNode[];
}

const IAM_APP_META = resolveAppMeta("iam");

export const IAM_SECTION_ORDER: IamSection[] = [
  "foundation",
  "department",
  "position",
  "employee",
  "user",
  "role",
  "api-resource",
  "menu",
  "data-scope",
  "login-policy",
  "password-policy",
  "user-role",
  "role-api"
];

export const IAM_SECTION_PATH_MAP: Record<IamSection, string> = {
  foundation: "/iam/foundation",
  department: "/iam/department",
  position: "/iam/position",
  employee: "/iam/employee",
  user: "/iam/user",
  role: "/iam/role",
  "api-resource": "/iam/api-resource",
  menu: "/iam/menu",
  "data-scope": "/iam/data-scope",
  "login-policy": "/iam/login-policy",
  "password-policy": "/iam/password-policy",
  "user-role": "/iam/user-role",
  "role-api": "/iam/role-api"
};

const IAM_SECTION_ALIASES: Record<string, IamSection> = {
  foundation: "foundation",
  department: "department",
  position: "position",
  employee: "employee",
  user: "user",
  role: "role",
  "api-resource": "api-resource",
  userRole: "user-role",
  "user-role": "user-role",
  roleApi: "role-api",
  "role-api": "role-api",
  menus: "menu",
  menu: "menu",
  dataScopes: "data-scope",
  "data-scope": "data-scope",
  loginPolicies: "login-policy",
  "login-policy": "login-policy",
  passwordPolicies: "password-policy",
  "password-policy": "password-policy"
};

const IAM_SECTION_PERMISSION_MAP: Partial<Record<IamSection, string>> = {
  department: "iam:user:query",
  position: "iam:user:query",
  employee: "iam:user:query",
  user: "iam:user:query",
  role: "iam:role:query",
  "api-resource": "iam:api-resource:query",
  menu: "iam:menu:query",
  "data-scope": "iam:data-scope:query",
  "login-policy": "iam:login-policy:query",
  "password-policy": "iam:password-policy:query",
  "user-role": "iam:user-role:query",
  "role-api": "iam:role-api:query"
};

const ROLE_TYPE_LABELS: Record<string, string> = {
  PLATFORM: "平台角色",
  TENANT: "租户角色",
  SYSTEM: "系统角色",
  CUSTOM: "自定义角色"
};

const DATA_SCOPE_LABELS: Record<string, string> = {
  ALL: "全部数据",
  DEPARTMENT: "本部门",
  DEPARTMENT_AND_CHILDREN: "本部门及子部门",
  SELF: "仅本人",
  CUSTOM: "自定义范围"
};

const SCOPE_TYPE_LABELS: Record<string, string> = {
  ALL_ACCESS: "全企业全部数据",
  ALL: "全企业全部数据",
  TENANT: "全企业全部数据",
  PERSONAL: "仅限本人经办",
  ORG_TREE: "本部门及所有下级",
  DEPARTMENT: "仅限本部门",
  DEPT_ONLY: "仅限本部门",
  DEPT_AND_CHILDREN: "本部门及所有下级",
  DEPARTMENT_AND_CHILDREN: "本部门及所有下级",
  ORG_AND_CHILDREN: "本公司及所有分支",
  ORG_ONLY: "仅限本公司",
  COMPANY: "仅限本公司",
  SELF_AND_SUBORDINATES: "本人及直属下级",
  POSITION: "仅限所属岗位",
  ROLE: "所属角色范围",
  SELF_ONLY: "仅限本人经办",
  SELF: "仅限本人经办",
  CUSTOM: "自定义勾选范围"
};

export function summarizeScopeRule(scopeType: string, ruleJson?: string): string {
  switch (scopeType) {
    case "ALL":
    case "ALL_ACCESS":
    case "TENANT":
      return "全企业所有业务数据，跨部门全量可见";
    case "DEPT_AND_CHILDREN":
    case "DEPARTMENT_AND_CHILDREN":
    case "ORG_TREE":
      return "所属部门及所有下属分支子部门数据";
    case "DEPT_ONLY":
    case "DEPARTMENT":
      return "仅限直接归属的部门（不含下级部门）";
    case "SELF_AND_SUBORDINATES":
      return "本人经办以及汇报线上下属人员的数据";
    case "SELF":
    case "SELF_ONLY":
    case "PERSONAL":
      return "仅限本人经办或录入的业务数据";
    case "ORG_AND_CHILDREN":
    case "COMPANY":
      return "所属独立公司主体及所有下属机构数据";
    case "CUSTOM":
      if (ruleJson) {
        try {
          const parsed = JSON.parse(ruleJson);
          const parts: string[] = [];
          if (parsed.departmentIds?.length) parts.push(`部门(${parsed.departmentIds.length})`);
          if (parsed.userIds?.length) parts.push(`成员(${parsed.userIds.length})`);
          if (parsed.positionIds?.length) parts.push(`岗位(${parsed.positionIds.length})`);
          if (parts.length) return `自定义指定: ${parts.join("、")}`;
        } catch {
          // ignore
        }
      }
      return "按可视化勾选的部门/成员自定义范围";
    default:
      return ruleJson || "系统内置通用规则";
  }
}

const SUBJECT_TYPE_LABELS: Record<string, string> = {
  USER: "个人",
  PERSONAL: "个人",
  DEPARTMENT: "部门",
  POSITION: "岗位",
  COMPANY: "公司",
  TENANT: "租户",
  ROLE: "角色"
};

const MENU_TYPE_LABELS: Record<string, string> = {
  DIRECTORY: "目录",
  MENU: "页面",
  BUTTON: "页面操作",
  LINK: "外部链接"
};

const USER_TYPE_LABELS: Record<string, string> = {
  PLATFORM: "平台账号",
  TENANT: "租户账号",
  EMPLOYEE: "员工账号",
  EXTERNAL: "外部账号"
};

const EMPLOYEE_STATUS_LABELS: Record<string, string> = {
  ACTIVE: "在职",
  INACTIVE: "停用",
  ONBOARDING: "待入职",
  OFFBOARDING: "离职流程中"
};

export const IAM_POLICY_ROWS = [
  {
    title: "登录控制基线",
    owner: "认证策略",
    baseline: "失败锁定、服务端会话、设备信任、IP 白名单、租户识别",
    source: "auth/session"
  },
  {
    title: "密码生命周期",
    owner: "密码策略",
    baseline: "复杂度校验、历史密码、过期检查、临时密码时效、默认密码重置",
    source: "auth/password"
  },
  {
    title: "组织主数据治理",
    owner: "组织策略",
    baseline: "部门树、岗位等级、员工编制、账号绑定、岗位归属",
    source: "iam/org"
  },
  {
    title: "导航与资源注册",
    owner: "菜单中心",
    baseline: "目录、菜单、按钮、权限码、路由组件、可见性与缓存策略",
    source: "iam/menu"
  },
  {
    title: "数据范围编排",
    owner: "数据权限",
    baseline: "组织树、本人、全量、自定义 JSON 规则、角色映射",
    source: "iam/data-scope"
  },
  {
    title: "权限注册与分配",
    owner: "授权策略",
    baseline: "角色、API 资源、用户角色、角色权限、接口鉴权",
    source: "iam/authorization"
  }
] as const;

export function localizeRoleType(value: string) {
  return ROLE_TYPE_LABELS[value] ?? value;
}

export function localizeDataScope(value: string) {
  return DATA_SCOPE_LABELS[value] ?? value;
}

export function localizeScopeType(value: string) {
  return SCOPE_TYPE_LABELS[value] ?? value;
}

export function localizeSubjectType(value: string) {
  return SUBJECT_TYPE_LABELS[value] ?? value;
}

export function localizeMenuType(value: string) {
  return MENU_TYPE_LABELS[value] ?? value;
}

export function localizeUserType(value: string) {
  return USER_TYPE_LABELS[value] ?? value;
}

export function localizeEmployeeStatus(value: string) {
  return EMPLOYEE_STATUS_LABELS[value] ?? value;
}

export function summarizeLoginChannels(row: IamLoginPolicyRow) {
  const channels = [];
  if (row.allowPasswordLogin) {
    channels.push("密码");
  }
  if (row.allowSmsLogin) {
    channels.push("短信");
  }
  if (row.allowEmailLogin) {
    channels.push("邮箱");
  }
  if (row.allowSocialLogin) {
    channels.push("社交");
  }
  return channels.length > 0 ? channels.join(" / ") : "未开放";
}

export function summarizePasswordRules(row: IamPasswordPolicyRow) {
  const rules = [];
  if (row.requireUppercase) {
    rules.push("大写");
  }
  if (row.requireLowercase) {
    rules.push("小写");
  }
  if (row.requireNumber) {
    rules.push("数字");
  }
  if (row.requireSpecial) {
    rules.push("特殊字符");
  }
  return `${row.minLength}-${row.maxLength} 位，${rules.length > 0 ? rules.join(" + ") : "无强制字符约束"}`;
}

export function canAccessIamSection(section: IamSection, hasPermission: (permissionCode?: string) => boolean) {
  return hasPermission(IAM_SECTION_PERMISSION_MAP[section]);
}

export function firstAccessibleIamSection(hasPermission: (permissionCode?: string) => boolean) {
  return IAM_SECTION_ORDER.find((section) => canAccessIamSection(section, hasPermission)) ?? "foundation";
}

export function normalizeIamSection(
  value: string | undefined,
  hasPermission: (permissionCode?: string) => boolean
): IamSection {
  const next = IAM_SECTION_ALIASES[value ?? ""] ?? value ?? "foundation";
  const candidate = IAM_SECTION_ORDER.includes(next as IamSection) ? (next as IamSection) : "foundation";
  return canAccessIamSection(candidate, hasPermission) ? candidate : firstAccessibleIamSection(hasPermission);
}

export function resolveIamSectionFromPath(
  path: string,
  hasPermission: (permissionCode?: string) => boolean
): IamSection | undefined {
  if (path === "/iam") {
    return "foundation";
  }
  if (!path.startsWith("/iam/")) {
    return undefined;
  }
  return normalizeIamSection(path.slice("/iam/".length), hasPermission);
}

export function resolveIamSectionFromRoute(params: {
  path: string;
  hasPermission: (permissionCode?: string) => boolean;
}) {
  return resolveIamSectionFromPath(params.path, params.hasPermission) ?? firstAccessibleIamSection(params.hasPermission);
}

export function pathForIamSection(section: IamSection) {
  return IAM_SECTION_PATH_MAP[section] ?? IAM_SECTION_PATH_MAP.foundation;
}

export function resolveIamControlCopy(section: IamSection) {
  const meta = IAM_APP_META.sections[section];
  return {
    title: meta?.label ?? "总览与基线",
    description: meta?.description ?? "当前控制面的治理说明待补充。"
  };
}

export function parseMenuRoute(routePath?: string | null) {
  if (!routePath) {
    return { path: "", query: {} as Record<string, string> };
  }
  const url = new URL(routePath, "https://saas-basics.local");
  const query: Record<string, string> = {};
  url.searchParams.forEach((value, key) => {
    query[key] = value;
  });
  return {
    path: url.pathname,
    query
  };
}

export function buildMenuAtlas(source: IamMenuRow[]): IamMenuAtlasNode[] {
  const sorted = [...source].sort((left, right) => {
    const sortDiff = left.sortNo - right.sortNo;
    return sortDiff !== 0 ? sortDiff : left.id - right.id;
  });

  const map = new Map<number, IamMenuAtlasNode>();

  for (const menu of sorted) {
    if (!menu.visible || menu.menuType === "BUTTON") {
      continue;
    }
    const target = parseMenuRoute(menu.routePath);
    map.set(menu.id, {
      id: menu.id,
      code: menu.menuCode,
      title: menu.menuName,
      path: target.path,
      query: target.query,
      children: []
    });
  }

  const roots: IamMenuAtlasNode[] = [];

  for (const menu of sorted) {
    const current = map.get(menu.id);
    if (!current) {
      continue;
    }
    const parent = map.get(menu.parentId);
    if (parent) {
      parent.children.push(current);
    } else {
      roots.push(current);
    }
  }

  return roots;
}
