import { http } from "@/api/http";
import type { ApiResponse } from "@/types/http";

export interface TrashItem {
  id: string;
  appCode: string;
  appName: string;
  tableName: string;
  moduleName: string;
  recordId: number;
  title: string;
  deletedAt?: string;
  remark: string;
  hasCascade: boolean;
}

export interface TrashAppSummary {
  appCode: string;
  appName: string;
  icon?: string;
  count: number;
}

export interface TrashRestoreResult {
  tableName: string;
  recordId: number;
  restoredCount: number;
  cascadeRestoredCount: number;
  message: string;
}

export async function fetchTrashList(params?: {
  appCode?: string;
  tableName?: string;
  keyword?: string;
}): Promise<ApiResponse<TrashItem[]>> {
  const res = await http.get<ApiResponse<TrashItem[]>>("/system/trash", { params });
  return res.data;
}

export async function fetchTrashAppSummaries(): Promise<ApiResponse<TrashAppSummary[]>> {
  const res = await http.get<ApiResponse<TrashAppSummary[]>>("/system/trash/apps");
  return res.data;
}

export async function restoreTrashItem(
  tableName: string,
  recordId: number,
  cascade = true
): Promise<ApiResponse<TrashRestoreResult>> {
  const res = await http.post<ApiResponse<TrashRestoreResult>>("/system/trash/restore", {
    tableName,
    recordId,
    cascade
  });
  return res.data;
}

export async function purgeTrashItem(
  tableName: string,
  recordId: number
): Promise<ApiResponse<void>> {
  const res = await http.post<ApiResponse<void>>("/system/trash/purge", {
    tableName,
    recordId
  });
  return res.data;
}
