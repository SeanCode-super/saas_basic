export interface PortalContext {
  clientId?: string;
  terminalCode?: string;
  tenantCode?: string;
}

export interface PortalContextInput {
  clientId?: unknown;
  terminalCode?: unknown;
  tenantCode?: unknown;
  tenant?: unknown;
}

const CLIENT_ID_KEY = "sb-portal-client-id";
const TERMINAL_CODE_KEY = "sb-portal-terminal-code";
const TENANT_CODE_KEY = "sb-portal-tenant-code";

/**
 * 从当前访问的二级域名中解析租户编码 (SaaS 行业标准物理隔离)
 * 例如:
 *  - p0mvp.localhost:9527 => "p0mvp"
 *  - ce.localhost:9527 => "ce"
 *  - p0mvp.tianshu.com => "p0mvp"
 *  - platform.localhost:9527 => "platform"
 *  - localhost:9527 => undefined (由入参或默认策略决定)
 */
export function extractSubdomainTenant(): string | undefined {
  if (typeof window === "undefined") {
    return undefined;
  }
  const host = window.location.hostname.toLowerCase();

  // IPv4 / IPv6 原生地址或纯根域 localhost 不包含租户子域
  if (/^(\d{1,3}\.){3}\d{1,3}$/.test(host) || host === "localhost" || host === "127.0.0.1") {
    return undefined;
  }

  const parts = host.split(".");
  if (parts.length >= 2) {
    const sub = parts[0];
    if (sub === "platform" || sub === "admin") {
      return "platform";
    }
    // 排除系统保留前缀
    if (!["localhost", "127", "www", "api", "static", "cdn"].includes(sub)) {
      return sub;
    }
  }
  return undefined;
}

/**
 * 根据租户编码构建专属二级子域名 URL (实现浏览器 Storage/Cookie 强隔离)
 */
export function buildTenantSubdomainUrl(tenantCode?: string, redirect?: string): string {
  if (typeof window === "undefined") {
    const search = tenantCode ? `?tenantCode=${encodeURIComponent(tenantCode)}` : "";
    return `/login${search}`;
  }

  const protocol = window.location.protocol;
  const port = window.location.port ? `:${window.location.port}` : "";
  const host = window.location.hostname;

  // 纯 IP 地址无法解析泛子域名，降级为 Query 参数
  if (/^(\d{1,3}\.){3}\d{1,3}$/.test(host)) {
    const params = new URLSearchParams();
    if (tenantCode) params.set("tenantCode", tenantCode);
    params.set("terminalCode", "web");
    if (redirect) params.set("redirect", redirect);
    const search = params.toString();
    return `${protocol}//${host}${port}/login${search ? `?${search}` : ""}`;
  }

  // 计算基础主域
  let baseDomain = host;
  if (host.endsWith(".localhost") || host === "localhost") {
    baseDomain = "localhost";
  } else {
    const parts = host.split(".");
    if (parts.length > 2) {
      baseDomain = parts.slice(1).join(".");
    }
  }

  const cleanTenant = (tenantCode || "").trim();
  const search = redirect ? `?redirect=${encodeURIComponent(redirect)}` : "";

  // 平台管理入口
  if (!cleanTenant || cleanTenant === "platform") {
    return `${protocol}//localhost${port}/login${search}`;
  }

  // 租户专属子域名
  return `${protocol}//${cleanTenant}.${baseDomain}${port}/login${search}`;
}

export function resolvePortalContext(input: PortalContextInput = {}): PortalContext {
  const subdomainTenant = extractSubdomainTenant();
  const stored = readStoredPortalContext();
  const inputTenant = firstQueryValue(input.tenantCode) || firstQueryValue(input.tenant);

  // 子域名在多租户物理隔离中拥有最高优先级！
  // 若用户通过 p0mvp.localhost 访问，则直接物理锚定为 p0mvp
  const tenantCode = subdomainTenant || inputTenant || stored.tenantCode;

  return {
    clientId: firstQueryValue(input.clientId) || stored.clientId,
    terminalCode: firstQueryValue(input.terminalCode) || stored.terminalCode,
    tenantCode
  };
}

export function rememberPortalContext(context: PortalContext): void {
  if (typeof window === "undefined") {
    return;
  }
  persistValue(CLIENT_ID_KEY, context.clientId);
  persistValue(TERMINAL_CODE_KEY, context.terminalCode);
  persistValue(TENANT_CODE_KEY, context.tenantCode);
}

export function buildPortalLoginQuery(redirect?: string, input: PortalContextInput = {}): Record<string, string> {
  const context = resolvePortalContext(input);
  return compactQuery({
    tenantCode: context.tenantCode,
    clientId: context.clientId,
    terminalCode: context.terminalCode,
    redirect
  });
}

export function buildPortalLoginUrl(redirect?: string, input: PortalContextInput = {}): string {
  const subdomain = extractSubdomainTenant();
  if (subdomain) {
    // 已在子域名中，不需要在 URL query 中重复携带冗余的 tenantCode
    const params = new URLSearchParams();
    if (redirect) params.set("redirect", redirect);
    const search = params.toString();
    return search ? `/login?${search}` : "/login";
  }
  const query = new URLSearchParams(buildPortalLoginQuery(redirect, input));
  const search = query.toString();
  return search ? `/login?${search}` : "/login";
}

function readStoredPortalContext(): PortalContext {
  if (typeof window === "undefined") {
    return {};
  }
  return {
    clientId: normalizeValue(window.localStorage.getItem(CLIENT_ID_KEY)),
    terminalCode: normalizeValue(window.localStorage.getItem(TERMINAL_CODE_KEY)),
    tenantCode: normalizeValue(window.localStorage.getItem(TENANT_CODE_KEY))
  };
}

function firstQueryValue(value: unknown): string | undefined {
  return normalizeValue(Array.isArray(value) ? value[0] : value);
}

function normalizeValue(value: unknown): string | undefined {
  if (typeof value !== "string") {
    return undefined;
  }
  const normalized = value.trim();
  return normalized || undefined;
}

function compactQuery(values: Record<string, string | undefined>): Record<string, string> {
  return Object.fromEntries(Object.entries(values).filter((entry): entry is [string, string] => Boolean(entry[1])));
}

function persistValue(key: string, value?: string): void {
  if (value) {
    window.localStorage.setItem(key, value);
  } else {
    window.localStorage.removeItem(key);
  }
}
