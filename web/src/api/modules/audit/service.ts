import { http } from "@/api/http";
import type { AuditOverview, LoginLog, OperationLog } from "./types";

export async function fetchAuditOverview(): Promise<AuditOverview> {
  const { data } = await http.get<AuditOverview>("/audit/overview");
  return data;
}

export async function fetchLoginLogs(params?: { username?: string; success?: boolean }): Promise<LoginLog[]> {
  const { data } = await http.get<LoginLog[]>("/audit/login-logs", { params });
  return data;
}

export async function fetchOperationLogs(params?: { bizModule?: string; operationType?: string }): Promise<OperationLog[]> {
  const { data } = await http.get<OperationLog[]>("/audit/operation-logs", { params });
  return data;
}
