import { request } from "../utils/request";

export function apiGetTenants() {
  return request<any>({
    url: "/api/tenants",
    method: "GET",
    silent: true
  });
}
