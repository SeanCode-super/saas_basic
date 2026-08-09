export interface AppSectionMeta {
  label: string;
  description: string;
}

export interface AppMeta {
  title: string;
  cluster: "foundation" | "runtime" | "engineering" | "assurance" | "overview";
  badge: string;
  summary: string;
  description: string;
  sections: Record<string, AppSectionMeta>;
}

export const APP_CLUSTER_LABELS: Record<AppMeta["cluster"], string> = {
  overview: "平台总览",
  foundation: "基础治理",
  runtime: "运行支撑",
  engineering: "工程交付",
  assurance: "审计风控"
};

export const APP_TAXONOMY: Record<string, AppMeta> = {
  dashboard: {
    title: "平台总览",
    cluster: "overview",
    badge: "总览",
    summary: "看全局，不做配置入口堆砌。",
    description: "平台地图、控制域、运行上下文和建设重点统一收口在这里。",
    sections: {
      dashboard: {
        label: "平台总览",
        description: "查看整个平台底座的地图、治理域和当前建设状态。"
      }
    }
  },
  tenant: {
    title: "租户中心",
    cluster: "foundation",
    badge: "租户",
    summary: "租户开通、套餐、隔离和台账治理。",
    description: "统一处理租户注册、审批、配额、隔离升级和生命周期治理。",
    sections: {
      overview: { label: "租户总览", description: "从平台视角看租户分布、试用窗口和隔离模型。" },
      onboarding: { label: "开通治理", description: "配置注册方式、审批模式、试用期和控制台开通规则。" },
      package: { label: "套餐配额", description: "配置套餐、席位、存储与 API 限流的默认基线。" },
      isolation: { label: "隔离升级", description: "规划共享、独 Schema、独库和读写分离升级路径。" },
      ledger: { label: "租户台账", description: "查看具体租户对象、状态、套餐和隔离模式。" }
    }
  },
  iam: {
    title: "身份与权限中心",
    cluster: "foundation",
    badge: "IAM",
    summary: "组织、账号、角色、菜单和认证策略中心。",
    description: "统一管理组织主数据、账号体系、权限资源、菜单编排和认证策略。",
    sections: {
      foundation: { label: "总览与基线", description: "查看身份治理五层模型、策略基线和运行态概况。" },
      department: { label: "部门管理", description: "维护组织树主数据、上下级关系和部门状态。" },
      position: { label: "岗位管理", description: "维护岗位编码、职责等级和岗位启停状态。" },
      employee: { label: "员工档案", description: "维护员工主数据以及部门、岗位归属关系。" },
      user: { label: "用户管理", description: "管理登录账号、账号状态和员工绑定关系。" },
      role: { label: "角色管理", description: "维护角色注册表、角色类型和数据范围基线。" },
      "api-resource": { label: "权限资源", description: "维护接口级权限资源和资源编码口径。" },
      menu: { label: "菜单中心", description: "直接治理当前控制台的应用菜单、子菜单和入口编排。" },
      "data-scope": { label: "数据权限", description: "配置数据范围策略和访问边界模型。" },
      "login-policy": { label: "登录策略", description: "定义登录方式、锁定阈值、白名单和设备信任。" },
      "password-policy": { label: "密码策略", description: "定义密码复杂度、历史记忆和过期周期。" },
      "user-role": { label: "用户角色", description: "管理账号与角色的最终分配关系。" },
      "role-api": { label: "角色权限", description: "管理角色与权限资源的最终分配关系。" }
    }
  },
  system: {
    title: "系统中心",
    cluster: "foundation",
    badge: "系统",
    summary: "安全、国际化、编号和配置注册表。",
    description: "平台公共行为的统一约束中心，收口安全、语言字典、序列规则和参数注册。",
    sections: {
      overview: { label: "系统总览", description: "查看系统基础策略、语言和编号的全局状态。" },
      security: { label: "安全策略", description: "统一密码复杂度、会话、MFA 和历史密码控制。" },
      locale: { label: "语言字典", description: "统一默认语言、回退语言、字典缓存和发布策略。" },
      sequence: { label: "序列规则", description: "统一租户编码、业务编号和周期重置规则。" },
      registry: { label: "配置注册表", description: "管理所有系统级参数的分组、键名和值类型。" }
    }
  },
  integration: {
    title: "集成中心",
    cluster: "runtime",
    badge: "集成",
    summary: "数据源、连接治理、回调和健康检测。",
    description: "收口数据源、HTTP 集成、Webhook 回调、凭证轮换和集成健康信号。",
    sections: {
      overview: { label: "集成总览", description: "查看集成底座的连接安全、回调策略和接入规模。" },
      connection: { label: "连接治理", description: "管理超时、网络边界、检测要求和凭证加密。" },
      callback: { label: "回调策略", description: "管理签名、重试、去重和死信队列策略。" },
      health: { label: "健康检测", description: "查看连通性、凭证异常和回调健康信号。" },
      registry: { label: "接入台账", description: "查看并管理纳入治理的集成对象台账。" }
    }
  },
  file: {
    title: "文件中心",
    cluster: "runtime",
    badge: "文件",
    summary: "存储、上传、生命周期和访问审计。",
    description: "统一文件底座的对象存储、上传链路、生命周期和访问留痕能力。",
    sections: {
      overview: { label: "文件总览", description: "查看文件底座在存储、上传和审计上的整体能力。" },
      storage: { label: "存储策略", description: "统一对象存储适配、版本、桶策略和冷热分层。" },
      upload: { label: "上传治理", description: "管理分片、直传、压缩和恶意文件扫描策略。" },
      lifecycle: { label: "生命周期", description: "管理保留、归档、去重、清理和恢复规则。" },
      audit: { label: "访问审计", description: "统一上传、下载、分享和预览的审计留痕。" }
    }
  },
  scheduler: {
    title: "调度中心",
    cluster: "runtime",
    badge: "调度",
    summary: "执行策略、告警和日志治理。",
    description: "统一任务调度的执行链路、告警策略、异常补偿和日志留存。",
    sections: {
      overview: { label: "调度总览", description: "查看执行、告警和日志的整体治理状态。" },
      strategy: { label: "执行策略", description: "管理重试、Misfire、并发和路由策略。" },
      pipeline: { label: "执行链路", description: "查看任务从定义到归档的完整执行路径。" },
      alarm: { label: "告警基线", description: "统一失败、超时和漏触发的告警策略。" },
      log: { label: "日志治理", description: "统一执行日志保留、查询和审计关联。" }
    }
  },
  codegen: {
    title: "代码生成中心",
    cluster: "engineering",
    badge: "生成",
    summary: "元数据驱动、SQL 产物和交付门禁。",
    description: "通过统一元数据和模板，输出 SQL、后端、前端、菜单和权限骨架。",
    sections: {
      overview: { label: "生成总览", description: "查看生成器的元数据能力和交付范围。" },
      metadata: { label: "元数据建模", description: "定义表、字段、索引和字典等统一模型。" },
      template: { label: "模板基线", description: "统一 Java、Vue、SQL 和菜单骨架模板。" },
      gate: { label: "交付门禁", description: "以强约束保障生成物符合企业级规范。" },
      artifact: { label: "产物矩阵", description: "查看 SQL、后端、前端和权限骨架输出范围。" }
    }
  },
  audit: {
    title: "审计中心",
    cluster: "assurance",
    badge: "审计",
    summary: "留痕、风险、黑名单和高危目录。",
    description: "统一登录审计、操作留痕、风险识别、黑名单策略和高危动作目录。",
    sections: {
      overview: { label: "审计总览", description: "查看留痕、风险和黑名单治理的整体结构。" },
      trail: { label: "审计留痕", description: "统一登录、权限、配置和导出等留痕目录。" },
      risk: { label: "风险识别", description: "定义异常登录、提权和批量导出预警规则。" },
      blacklist: { label: "黑名单", description: "统一账号、设备、IP 和租户级封禁策略。" },
      catalog: { label: "高危目录", description: "定义必须被审计和预警的动作集合。" }
    }
  }
};

export function resolveAppMeta(code?: string, fallbackTitle?: string): AppMeta {
  return (
    (code ? APP_TAXONOMY[code] : undefined) ?? {
      title: fallbackTitle ?? code ?? "应用",
      cluster: "foundation",
      badge: "应用",
      summary: "未定义应用说明。",
      description: "该应用尚未补充平台信息架构说明。",
      sections: {}
    }
  );
}

export function resolveSectionMeta(params: {
  appCode?: string;
  fallbackTitle?: string;
  query?: Record<string, string>;
  path?: string;
}): AppSectionMeta {
  const meta = resolveAppMeta(params.appCode, params.fallbackTitle);
  const key = params.query?.view ?? params.query?.tab ?? deriveSectionKey(params.path, params.appCode);
  return meta.sections[key] ?? { label: params.fallbackTitle ?? "控制面", description: "当前控制面的治理说明待补充。" };
}

function deriveSectionKey(path?: string, appCode?: string): string {
  if (!path) {
    return appCode ?? "";
  }
  const normalized = path.replace(/^\/+|\/+$/g, "");
  if (!normalized) {
    return appCode ?? "";
  }
  const segments = normalized.split("/");
  if (segments.length === 1) {
    return segments[0];
  }
  return segments[segments.length - 1] ?? appCode ?? "";
}
