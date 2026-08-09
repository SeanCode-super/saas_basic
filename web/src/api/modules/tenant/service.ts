import { http } from "@/api/http";
import type { ApiResponse, PageResponse } from "@/types/http";
import type { TenantRow, TenantSavePayload } from "./types";

export async function fetchTenantPage(): Promise<TenantRow[]> {
  const response = await http.get<ApiResponse<PageResponse<TenantRow>>>("/tenants");
  return response.data.data.records;
}

export async function createTenant(payload: TenantSavePayload): Promise<TenantRow> {
  const response = await http.post<ApiResponse<TenantRow>>("/tenants", payload);
  return response.data.data;
}
