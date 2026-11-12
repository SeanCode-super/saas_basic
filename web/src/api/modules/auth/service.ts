import { http } from "@/api/http";
import type { ApiResponse } from "@/types/http";
import type {
  AuthCurrentUser,
  AuthLoginRequest,
  AuthLoginResponse,
  CaptchaChallenge,
  MfaSetupResponse,
  MfaStatusResponse,
  OnlineSession
} from "./types";

export async function fetchAuthCaptcha(): Promise<CaptchaChallenge> {
  const response = await http.get<ApiResponse<CaptchaChallenge>>("/auth/captcha");
  return response.data.data;
}

export async function fetchMfaStatus(): Promise<MfaStatusResponse> {
  const response = await http.get<ApiResponse<MfaStatusResponse>>("/auth/mfa/status");
  return response.data.data;
}

export async function setupMfa(): Promise<MfaSetupResponse> {
  const response = await http.post<ApiResponse<MfaSetupResponse>>("/auth/mfa/setup");
  return response.data.data;
}

export async function enableMfa(totpCode: string): Promise<MfaStatusResponse> {
  const response = await http.post<ApiResponse<MfaStatusResponse>>("/auth/mfa/enable", { totpCode });
  return response.data.data;
}

export async function disableMfa(password: string, totpCode: string): Promise<MfaStatusResponse> {
  const response = await http.post<ApiResponse<MfaStatusResponse>>("/auth/mfa/disable", { password, totpCode });
  return response.data.data;
}

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

export async function selectAuthContext(assignmentPublicId: string): Promise<AuthCurrentUser> {
  const response = await http.put<ApiResponse<AuthCurrentUser>>("/auth/context", { assignmentPublicId });
  return response.data.data;
}

export async function clearAuthContext(): Promise<AuthCurrentUser> {
  const response = await http.delete<ApiResponse<AuthCurrentUser>>("/auth/context");
  return response.data.data;
}

