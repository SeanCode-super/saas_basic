import { http } from "@/api/http";
import type { ApiResponse, PageResponse } from "@/types/http";
import type {
  TenantPackageRow,
  TenantPackageSavePayload,
  TenantPlanUpdatePayload,
  TenantQuotaOverview,
  TenantRow,
  TenantSavePayload,
  TenantMeteringSummary,
  TenantBillRow,
  TenantSnapshotExportResult,
  TenantSnapshotRestoreResult
} from "./types";

export async function fetchTenantPage(): Promise<TenantRow[]> {
  const response = await http.get<ApiResponse<PageResponse<TenantRow>>>("/tenants");
  return response.data.data.records;
}

export async function createTenant(payload: TenantSavePayload): Promise<TenantRow> {
  const response = await http.post<ApiResponse<TenantRow>>("/tenants", payload);
  return response.data.data;
}

export async function updateTenant(id: number, payload: TenantSavePayload): Promise<TenantRow> {
  const response = await http.put<ApiResponse<TenantRow>>(`/tenants/${id}`, payload);
  return response.data.data;
}

export async function updateTenantStatus(id: number, status: string): Promise<TenantRow> {
  const response = await http.patch<ApiResponse<TenantRow>>(`/tenants/${id}/status`, { status });
  return response.data.data;
}

export async function updateTenantPlan(id: number, payload: TenantPlanUpdatePayload): Promise<TenantRow> {
  const response = await http.put<ApiResponse<TenantRow>>(`/tenants/${id}/plan`, payload);
  return response.data.data;
}

export async function fetchTenantQuotas(id: number): Promise<TenantQuotaOverview> {
  const response = await http.get<ApiResponse<TenantQuotaOverview>>(`/tenants/${id}/quotas`);
  return response.data.data;
}

export async function fetchTenantPackages(): Promise<TenantPackageRow[]> {
  const response = await http.get<ApiResponse<TenantPackageRow[]>>("/tenant-packages");
  return response.data.data;
}

export async function createTenantPackage(payload: TenantPackageSavePayload): Promise<TenantPackageRow> {
  const response = await http.post<ApiResponse<TenantPackageRow>>("/tenant-packages", payload);
  return response.data.data;
}

export async function updateTenantPackage(id: number, payload: TenantPackageSavePayload): Promise<TenantPackageRow> {
  const response = await http.put<ApiResponse<TenantPackageRow>>(`/tenant-packages/${id}`, payload);
  return response.data.data;
}

export async function deleteTenantPackage(id: number): Promise<void> {
  await http.delete(`/tenant-packages/${id}`);
}

export async function fetchTenantMeteringSummary(): Promise<TenantMeteringSummary> {
  const response = await http.get<ApiResponse<TenantMeteringSummary>>("/tenant/metering/summary");
  return response.data.data;
}

export async function fetchTenantBills(): Promise<TenantBillRow[]> {
  const response = await http.get<ApiResponse<TenantBillRow[]>>("/tenant/billing/bills");
  return response.data.data;
}

export async function exportTenantSnapshot(tenantId?: number): Promise<TenantSnapshotExportResult> {
  const response = await http.post<ApiResponse<TenantSnapshotExportResult>>("/tenant/snapshot/export", null, {
    params: tenantId ? { tenantId } : {}
  });
  return response.data.data;
}

export async function restoreTenantSnapshot(file: File, tenantId?: number): Promise<TenantSnapshotRestoreResult> {
  const formData = new FormData();
  formData.append("file", file);
  const response = await http.post<ApiResponse<TenantSnapshotRestoreResult>>("/tenant/snapshot/restore", formData, {
    params: tenantId ? { tenantId } : {},
    headers: { "Content-Type": "multipart/form-data" }
  });
  return response.data.data;
}
