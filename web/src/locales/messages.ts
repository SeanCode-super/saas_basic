export type AppLocale = "zh-CN" | "en-US";

export const messages: Record<AppLocale, Record<string, string>> = {
  "zh-CN": {
    "shell.brandTitle": "天枢协同云平台",
    "shell.brandSubtitle": "企业一体化协同管理中枢",
    "shell.directory": "功能索引",
    "shell.searchPlaceholder": "搜索菜单、路由、权限或快捷操作...",
    "shell.switchTheme": "切换界面主题",
    "shell.switchLocale": "切换显示语言",
    "shell.logout": "安全退出登录",
    "cluster.overview": "平台总览",
    "cluster.foundation": "基础治理",
    "cluster.runtime": "运行支撑",
    "cluster.engineering": "工程交付",
    "cluster.assurance": "审计风控",
    "common.confirm": "确认",
    "common.cancel": "取消",
    "common.save": "保存配置",
    "common.refresh": "刷新数据",
    "common.export": "导出数据",
    "common.enabled": "已启用",
    "common.disabled": "已停用",
    "security.transportTitle": "全链路可控传输加密",
    "security.modeOff": "关闭加密",
    "security.modeOptional": "兼容加密",
    "security.modeRequired": "强制加密",
    "security.mfaTitle": "多因子认证 (TOTP MFA)",
    "security.baselineTitle": "账号与密码安全基线",
    "monitor.apiStatsTitle": "全量接口调用与 P95/P99 时延统计",
    "monitor.alertsTitle": "阈值告警规则引擎与触发事件",
    "audit.diffTitle": "字段级变更对照 (Before / After Diff)",
    "tenant.snapshotTitle": "租户灾备 ZIP 快照与校验还原",
    "pay.webhookTitle": "支付通道签名回调与 Outbox 核销",
    "ai.copilotTitle": "AI 数据副驾驶 (NL2SQL 驾驶舱)",
    "ai.commandPaletteTitle": "⌘K 全局 AI 智能命令面板",
    "export.centerTitle": "企业数据导出与下载中心"
  },
  "en-US": {
    "shell.brandTitle": "Tianshu Cloud Platform",
    "shell.brandSubtitle": "Enterprise Multi-Tenant Cloud",
    "shell.directory": "Functions",
    "shell.searchPlaceholder": "Search menus, routes, permissions or actions...",
    "shell.switchTheme": "Switch UI Theme",
    "shell.switchLocale": "Switch Language",
    "shell.logout": "Sign Out",
    "cluster.overview": "Overview",
    "cluster.foundation": "Foundation",
    "cluster.runtime": "Runtime",
    "cluster.engineering": "Engineering",
    "cluster.assurance": "Assurance",
    "common.confirm": "Confirm",
    "common.cancel": "Cancel",
    "common.save": "Save",
    "common.refresh": "Refresh",
    "common.export": "Export",
    "common.enabled": "Enabled",
    "common.disabled": "Disabled",
    "security.transportTitle": "End-to-End Transport Encryption",
    "security.modeOff": "Disabled",
    "security.modeOptional": "Compatible",
    "security.modeRequired": "Required",
    "security.mfaTitle": "Two-Factor Authentication (TOTP MFA)",
    "security.baselineTitle": "Account & Password Security Baseline",
    "monitor.apiStatsTitle": "API Traffic & P95/P99 Latency Telemetry",
    "monitor.alertsTitle": "Threshold Alert Rules & Triggered Events",
    "audit.diffTitle": "Field-Level Before / After Audit Diff",
    "tenant.snapshotTitle": "Tenant ZIP Backup & Checksum Restore",
    "pay.webhookTitle": "Signed Payment Webhook & Outbox Billing",
    "ai.copilotTitle": "AI Data Copilot (NL2SQL Cockpit)",
    "ai.commandPaletteTitle": "⌘K Global AI Command Palette",
    "export.centerTitle": "Enterprise Data Export & Download Center"
  }
};

export function translate(
  locale: AppLocale,
  key: string,
  params: Record<string, string | number> = {}
): string {
  const catalog = messages[locale] ?? messages["zh-CN"];
  const raw = catalog[key] ?? messages["zh-CN"][key] ?? key;
  return Object.entries(params).reduce(
    (result, [paramKey, value]) => result.split(`{${paramKey}}`).join(String(value)),
    raw
  );
}
