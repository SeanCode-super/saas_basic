import { http } from "@/api/http";
import type { ApiResponse } from "@/types/http";
import type {
  AuditOverview,
  BlacklistCreatePayload,
  BlacklistItem,
  HighRiskCatalogItem,
  LoginLog,
  OperationLog,
  OperationLogDiffResponse,
  RiskEvaluatePayload,
  RiskEvaluateResult,
  RiskRuleItem
} from "./types";

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

export async function fetchOperationLogDiff(id: number | string): Promise<OperationLogDiffResponse> {
  const { data } = await http.get<ApiResponse<OperationLogDiffResponse>>(`/audit/operation-logs/${encodeURIComponent(String(id))}/diff`);
  return data?.data ?? (data as any);
}

export async function fetchSecurityBlacklist(): Promise<BlacklistItem[]> {
  const { data } = await http.get<ApiResponse<BlacklistItem[]>>("/audit/blacklist");
  return data?.data ?? (data as any) ?? [];
}

export async function createSecurityBlacklist(payload: BlacklistCreatePayload): Promise<BlacklistItem> {
  const { data } = await http.post<ApiResponse<BlacklistItem>>("/audit/blacklist", payload);
  return data?.data ?? (data as any);
}

export async function deleteSecurityBlacklist(id: number): Promise<void> {
  await http.delete(`/audit/blacklist/${id}`);
}

export async function fetchRiskRules(): Promise<RiskRuleItem[]> {
  const { data } = await http.get<ApiResponse<RiskRuleItem[]>>("/audit/risk-rules");
  return data?.data ?? (data as any) ?? [];
}

export async function updateRiskRuleStatus(id: number, enabled: boolean): Promise<RiskRuleItem> {
  const { data } = await http.patch<ApiResponse<RiskRuleItem>>(`/audit/risk-rules/${id}/status`, { enabled });
  return data?.data ?? (data as any);
}

export async function evaluateRiskRule(payload: RiskEvaluatePayload): Promise<RiskEvaluateResult> {
  const { data } = await http.post<ApiResponse<RiskEvaluateResult>>("/audit/risk-rules/evaluate", payload);
  return data?.data ?? (data as any);
}

export async function fetchHighRiskCatalog(): Promise<HighRiskCatalogItem[]> {
  const { data } = await http.get<ApiResponse<HighRiskCatalogItem[]>>("/audit/high-risk-catalog");
  return data?.data ?? (data as any) ?? [];
}
