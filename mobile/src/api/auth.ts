import { request } from "../utils/request";

export function apiLogin(payload: { tenantCode: string; username: string; password: string }) {
  return request<{ accessToken: string; refreshToken: string; user: any }>({
    url: "/api/auth/login",
    method: "POST",
    data: payload
  });
}

export function apiGetUserInfo() {
  return request<any>({
    url: "/api/iam/users/me",
    method: "GET"
  });
}
