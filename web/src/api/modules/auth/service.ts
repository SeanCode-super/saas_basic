import { http } from "@/api/http";
import type { ApiResponse } from "@/types/http";
import type { AuthCurrentUser, AuthLoginRequest, AuthLoginResponse } from "./types";

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
