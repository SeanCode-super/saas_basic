import { http } from "@/api/http";
import type { ApiResponse } from "@/types/http";
import type { AuthCurrentUser, AuthLoginRequest, AuthLoginResponse, OnlineSession } from "./types";

export async function loginByPassword(payload: AuthLoginRequest): Promise<AuthLoginResponse> {
  const response = await http.post<ApiResponse<AuthLoginResponse>>("/auth/login", payload);
  return response.data.data;
}

export async function fetchCurrentUser(): Promise<AuthCurrentUser> {
  const response = await http.get<ApiResponse<AuthCurrentUser>>("/auth/me");
  return response.data.data;
}

export async function logoutSession(): Promise<void> {
  await http.post<ApiResponse<null>>("/auth/logout");
}

export async function fetchOnlineSessions(): Promise<OnlineSession[]> {
  const response = await http.get<ApiResponse<OnlineSession[]>>("/auth/online-sessions");
  return response.data.data;
}

export async function kickoutOnlineSession(sessionNo: string): Promise<void> {
  await http.delete<ApiResponse<null>>(`/auth/online-sessions/${sessionNo}`);
}

export async function fetchSsoAuthorizeUrl(channel: string, redirectUri?: string): Promise<{ channel: string; authorizeUrl: string; state: string }> {
  const response = await http.get<ApiResponse<{ channel: string; authorizeUrl: string; state: string }>>("/auth/sso/authorize-url", {
    params: { channel, redirectUri }
  });
  return response.data.data;
}

export async function loginBySso(payload: { channel: string; authCode: string; state?: string; tenantCode?: string }): Promise<AuthLoginResponse> {
  const response = await http.post<ApiResponse<AuthLoginResponse>>("/auth/sso/callback", payload);
  return response.data.data;
}

