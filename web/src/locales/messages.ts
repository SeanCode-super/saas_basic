export type AppLocale = "zh-CN" | "en-US";

type MessageTree = Record<string, string>;

const messages: Record<AppLocale, MessageTree> = {
  "zh-CN": {
    "shell.brandTitle": "SaaS Basic",
    "shell.directory": "功能索引",
    "cluster.overview": "平台总览",
    "cluster.foundation": "基础治理",
    "cluster.runtime": "运行支撑",
    "cluster.engineering": "工程交付",
    "cluster.assurance": "审计风控"
  },
  "en-US": {
    "shell.brandTitle": "SaaS Basic",
    "shell.directory": "Functions",
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
