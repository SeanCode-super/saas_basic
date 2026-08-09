export type AppLocale = "zh-CN" | "en-US";

type MessageTree = Record<string, string>;

const messages: Record<AppLocale, MessageTree> = {
  "zh-CN": {
    "shell.brandEyebrow": "平台导航",
    "shell.brandTitle": "SaaS 基础底座",
    "shell.directory": "功能索引",
    "shell.directoryHint": "查看全部应用与控制面",
    "shell.navMode": "导航模式",
    "shell.currentApp": "当前应用",
    "shell.realtime": "实时导航",
    "shell.refreshing": "实时刷新中",
    "shell.unavailable": "导航不可用",
    "atlas.eyebrow": "功能索引",
    "atlas.apps": "应用域",
    "atlas.sections": "控制面",
    "atlas.appCount": "{count} 个应用",
    "atlas.sectionCount": "{count} 个控制面",
    "base.eyebrow": "控制台页面",
    "control.eyebrow": "控制面",
    "table.eyebrow": "台账表格",
    "table.subtitle": "面向高密度企业控制台场景的统一表格基座。",
    "table.count": "当前记录",
    "table.scope": "统一口径",
    "table.scopeHint": "所有主数据、策略和注册项必须进入同一控制台台账，不再散落到业务页面。",
    "table.noData": "暂无数据",
    "table.emptyTitle": "当前暂无记录",
    "table.emptyDescription": "先完善筛选条件或新增主数据，统一台账会在这里持续沉淀。",
    "cluster.overview": "平台总览",
    "cluster.foundation": "基础治理",
    "cluster.runtime": "运行支撑",
    "cluster.engineering": "工程交付",
    "cluster.assurance": "审计风控"
  },
  "en-US": {
    "shell.brandEyebrow": "Navigation",
    "shell.brandTitle": "SaaS Foundation",
    "shell.directory": "Atlas",
    "shell.directoryHint": "Browse all apps and control surfaces",
    "shell.navMode": "Navigation Mode",
    "shell.currentApp": "Current App",
    "shell.realtime": "Live",
    "shell.refreshing": "Refreshing",
    "shell.unavailable": "Unavailable",
    "atlas.eyebrow": "Navigation Atlas",
    "atlas.apps": "Apps",
    "atlas.sections": "Surfaces",
    "atlas.appCount": "{count} apps",
    "atlas.sectionCount": "{count} surfaces",
    "base.eyebrow": "Workspace",
    "control.eyebrow": "Control Surface",
    "table.eyebrow": "Registry Grid",
    "table.subtitle": "Unified data grid for high-density enterprise consoles.",
    "table.count": "Records",
    "table.scope": "Governance Scope",
    "table.scopeHint": "Master data, policies, and registry items should stay in one control ledger.",
    "table.noData": "No Data",
    "table.emptyTitle": "No records",
    "table.emptyDescription": "Adjust filters or create data to populate this ledger.",
    "cluster.overview": "Overview",
    "cluster.foundation": "Foundation",
    "cluster.runtime": "Runtime",
    "cluster.engineering": "Engineering",
    "cluster.assurance": "Assurance"
  }
};

export function translate(locale: AppLocale, key: string, params?: Record<string, string | number>) {
  const catalog = messages[locale] ?? messages["zh-CN"];
  const raw = catalog[key] ?? messages["zh-CN"][key] ?? key;
  if (!params) {
    return raw;
  }
  return Object.entries(params).reduce((result, [paramKey, value]) => result.split(`{${paramKey}}`).join(String(value)), raw);
}
