import { http } from "@/api/http";
import type { ApiResponse } from "@/types/http";
import type { AuditOverview, LoginLog, OperationLog, OperationLogDiffResponse } from "./types";

export async function fetchAuditOverview(): Promise<AuditOverview> {
  const { data } = await http.get<ApiResponse<AuditOverview>>("/audit/overview");
  return data?.data ?? (data as any);
}

export async function fetchLoginLogs(params?: { username?: string; success?: boolean }): Promise<LoginLog[]> {
  const { data } = await http.get<ApiResponse<LoginLog[]>>("/audit/login-logs", { params });
  return data?.data ?? (data as any) ?? [];
}

export async function fetchOperationLogs(params?: { bizModule?: string; operationType?: string }): Promise<OperationLog[]> {
  const { data } = await http.get<ApiResponse<OperationLog[]>>("/audit/operation-logs", { params });
  return data?.data ?? (data as any) ?? [];
}

export async function fetchOperationLogDiff(id: number): Promise<OperationLogDiffResponse> {
  const { data } = await http.get<ApiResponse<OperationLogDiffResponse>>(`/audit/operation-logs/${id}/diff`);
  return data?.data ?? (data as any);
}
