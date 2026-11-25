import { request } from "../utils/request";

export interface PortalEntry {
  clientId: string;
  clientName: string;
  tenantCode: string;
  portalTitle: string;
  welcomeTitle: string;
  welcomeText: string;
  themeCode?: string;
  backgroundColor?: string;
  backgroundImageUrl?: string;
  terminal?: {
    terminalCode: string;
    terminalName: string;
    terminalType: string;
  };
}

export function apiGetPortalEntry(params?: { tenantCode?: string; clientId?: string; terminalCode?: string }) {
  return request<PortalEntry>({
    url: "/api/portal/entry",
    method: "GET",
    params
  });
}

export function apiLogin(payload: { tenantCode?: string; clientId?: string; terminalCode?: string; username: string; password: string; totpCode?: string }) {
  return request<{ accessToken: string; refreshToken: string; currentUser?: any; user?: any }>({
    url: "/api/auth/login",
    method: "POST",
    data: payload
  });
}

export function apiGetUserInfo() {
  return request<any>({
    url: "/api/auth/me",
    method: "GET"
  });
}

export function apiLogout() {
  return request<void>({ url: "/api/auth/logout", method: "POST", silent: true });
}

