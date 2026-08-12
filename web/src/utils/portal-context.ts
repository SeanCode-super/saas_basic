export interface PortalContext {
  clientId?: string;
  terminalCode?: string;
}

export interface PortalContextInput {
  clientId?: unknown;
  terminalCode?: unknown;
}

const CLIENT_ID_KEY = "sb-portal-client-id";
const TERMINAL_CODE_KEY = "sb-portal-terminal-code";

export function resolvePortalContext(input: PortalContextInput = {}): PortalContext {
  const stored = readStoredPortalContext();
  return {
    clientId: firstQueryValue(input.clientId) || stored.clientId,
    terminalCode: firstQueryValue(input.terminalCode) || stored.terminalCode
  };
}

export function rememberPortalContext(context: PortalContext): void {
  if (typeof window === "undefined") {
    return;
  }
  persistValue(CLIENT_ID_KEY, context.clientId);
  persistValue(TERMINAL_CODE_KEY, context.terminalCode);
}

export function buildPortalLoginQuery(redirect?: string, input: PortalContextInput = {}): Record<string, string> {
  const context = resolvePortalContext(input);
  return compactQuery({
    clientId: context.clientId,
    terminalCode: context.terminalCode,
    redirect
  });
}

export function buildPortalLoginUrl(redirect?: string, input: PortalContextInput = {}): string {
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
    terminalCode: normalizeValue(window.localStorage.getItem(TERMINAL_CODE_KEY))
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
