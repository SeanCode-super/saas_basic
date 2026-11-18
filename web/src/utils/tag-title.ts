import { APP_TAXONOMY, resolveSectionMeta } from "@/config/app-taxonomy";
import type { MenuNavItem } from "@/stores/modules/menu";

function normalizeQuery(query?: Record<string, unknown>): Record<string, string> {
  if (!query) return {};
  const result: Record<string, string> = {};
  for (const [key, raw] of Object.entries(query)) {
    if (Array.isArray(raw)) {
      if (raw[0] != null) result[key] = String(raw[0]);
    } else if (raw != null && raw !== "") {
      result[key] = String(raw);
    }
  }
  return result;
}

function queriesMatch(expected: Record<string, string>, actual: Record<string, string>): boolean {
  const keys = Object.keys(expected);
  if (keys.length === 0) return true;
  return keys.every((key) => actual[key] === expected[key]);
}

function findMenuMatch(
  navigation: MenuNavItem[],
  cleanPath: string,
  actualQuery: Record<string, string>
): { app?: MenuNavItem; section?: MenuNavItem } | null {
  for (const app of navigation) {
    for (const child of app.children || []) {
      const childPath = child.path?.replace(/\/+$/, "") || "";
      if (childPath === cleanPath && queriesMatch(child.query || {}, actualQuery)) {
        return { app, section: child };
      }
    }
    const appPath = app.path?.replace(/\/+$/, "") || "";
    if (appPath === cleanPath && queriesMatch(app.query || {}, actualQuery)) {
      return { app };
    }
  }
  return null;
}

const APP_PATH_PREFIX_ALIAS: Record<string, string> = {
  "server-monitor": "system",
  "ai-copilot": "ai_center",
  "knowledge-base": "ai_center",
  "agent-studio": "ai_center",
  "ai-models": "ai_center",
  "finetune-studio": "ai_center",
  "print-center": "report_print"
};

export function resolveRouteTagTitle(params: {
  path: string;
  query?: Record<string, unknown>;
  routeTitle?: string;
  navigation?: MenuNavItem[];
}): string {
  const cleanPath = (params.path || "").replace(/\/+$/, "") || "/";
  if (cleanPath === "/dashboard") {
    return "控制台首页";
  }

  const normalizedQuery = normalizeQuery(params.query);
  const queryTab = normalizedQuery.view || normalizedQuery.tab;

  // 1. 优先查模块 Taxonomy（识别模块简称 · 子区名）
  const segments = cleanPath.replace(/^\/+/, "").split("/").filter(Boolean);
  const firstSegment = segments[0] || "";
  const candidateAppCode = APP_PATH_PREFIX_ALIAS[firstSegment] || firstSegment;
  const taxonomy = APP_TAXONOMY[candidateAppCode];

  if (taxonomy) {
    const hasSubSection = segments.length > 1 || Boolean(queryTab);
    if (hasSubSection) {
      const secMeta = resolveSectionMeta({
        appCode: candidateAppCode,
        fallbackTitle: params.routeTitle,
        query: normalizedQuery,
        path: cleanPath
      });
      const subLabel = secMeta.label;
      if (subLabel && subLabel !== "控制面" && subLabel !== taxonomy.title) {
        if (subLabel.includes(taxonomy.badge) || subLabel.includes(taxonomy.title.slice(0, 2))) {
          return subLabel;
        }
        return `${taxonomy.badge} · ${subLabel}`;
      }
    }
  }

  // 2. 尝试动态菜单树匹配父模块 + 子功能名
  if (params.navigation?.length) {
    const match = findMenuMatch(params.navigation, cleanPath, normalizedQuery);
    if (match?.section?.title) {
      const parentPrefix = taxonomy?.badge || match.app?.title;
      const childTitle = match.section.title;
      if (parentPrefix && childTitle !== parentPrefix && !childTitle.includes(parentPrefix)) {
        return `${parentPrefix} · ${childTitle}`;
      }
      return childTitle;
    }
    if (match?.app?.title) {
      return match.app.title;
    }
  }

  return params.routeTitle || "新标签";
}
