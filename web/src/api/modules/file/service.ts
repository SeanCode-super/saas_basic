import { http } from "@/api/http";
import type { ApiResponse } from "@/types/http";
import type {
  FileAccessLogCreatePayload,
  FileAccessLogRow,
  FileCapability,
  FileLifecyclePolicyRow,
  FileLifecyclePolicySavePayload,
  FileObjectRow,
  FileStorageRow,
  FileStorageSavePayload,
  FileStorageTestResult,
  FileUploadRouteRow,
  FileUploadRouteSavePayload,
  FileUploadSessionCompletePayload,
  FileUploadSessionCreatePayload,
  FileUploadSessionRow,
  RawUploadResult,
  StorageAvailability
} from "./types";

export function resolveAuthorizedFileUrl(url?: string | null, token?: string | null): string {
  if (!url) return "";
  if (!token || !url.includes("/api/file/raw-download/")) {
    return url;
  }
  const sep = url.includes("?") ? "&" : "?";
  if (url.includes("token=")) return url;
  return `${url}${sep}token=${encodeURIComponent(token)}`;
}

export async function fetchFileStorageStatus(bizType = "COMMON"): Promise<StorageAvailability> {
  const response = await http.get<ApiResponse<StorageAvailability>>("/file/storage-status", {
    params: { bizType }
  });
  return response.data.data;
}

export async function uploadRawFile(file: File, bizType = "COMMON"): Promise<RawUploadResult> {
  const form = new FormData();
  form.append("file", file);
  form.append("bizType", bizType);
  const response = await http.post<ApiResponse<RawUploadResult>>("/file/raw-upload", form);
  return response.data.data;
}

export async function fetchFileCapability(): Promise<FileCapability> {
  const response = await http.get<ApiResponse<FileCapability>>("/files/capability");
  return response.data.data;
}

export async function fetchFileStorages(): Promise<FileStorageRow[]> {
  const response = await http.get<ApiResponse<FileStorageRow[]>>("/files/storages");
  return response.data.data;
}

export async function createFileStorage(payload: FileStorageSavePayload): Promise<FileStorageRow> {
  const response = await http.post<ApiResponse<FileStorageRow>>("/files/storages", payload);
  return response.data.data;
}

export async function updateFileStorage(id: number, payload: FileStorageSavePayload): Promise<FileStorageRow> {
  const response = await http.put<ApiResponse<FileStorageRow>>(`/files/storages/${id}`, payload);
  return response.data.data;
}

export async function setDefaultFileStorage(id: number): Promise<FileStorageRow> {
  const response = await http.post<ApiResponse<FileStorageRow>>(`/files/storages/${id}/set-default`);
  return response.data.data;
}

export async function testFileStorage(id: number): Promise<FileStorageTestResult> {
  const response = await http.post<ApiResponse<FileStorageTestResult>>(`/files/storages/${id}/test`);
  return response.data.data;
}

export async function deleteFileStorage(id: number): Promise<void> {
  await http.delete(`/files/storages/${id}`);
}

export async function fetchFileUploadRoutes(): Promise<FileUploadRouteRow[]> {
  const response = await http.get<ApiResponse<FileUploadRouteRow[]>>("/files/upload-routes");
  return response.data.data;
}

export async function createFileUploadRoute(payload: FileUploadRouteSavePayload): Promise<FileUploadRouteRow> {
  const response = await http.post<ApiResponse<FileUploadRouteRow>>("/files/upload-routes", payload);
  return response.data.data;
}

export async function updateFileUploadRoute(id: number, payload: FileUploadRouteSavePayload): Promise<FileUploadRouteRow> {
  const response = await http.put<ApiResponse<FileUploadRouteRow>>(`/files/upload-routes/${id}`, payload);
  return response.data.data;
}

export async function deleteFileUploadRoute(id: number): Promise<void> {
  await http.delete(`/files/upload-routes/${id}`);
}

export async function fetchFileObjects(): Promise<FileObjectRow[]> {
  const response = await http.get<ApiResponse<FileObjectRow[]>>("/files/objects");
  return response.data.data;
}

export async function fetchFileUploadSessions(): Promise<FileUploadSessionRow[]> {
  const response = await http.get<ApiResponse<FileUploadSessionRow[]>>("/files/upload-sessions");
  return response.data.data;
}

export async function createFileUploadSession(payload: FileUploadSessionCreatePayload): Promise<FileUploadSessionRow> {
  const response = await http.post<ApiResponse<FileUploadSessionRow>>("/files/upload-sessions", payload);
  return response.data.data;
}

export async function completeFileUploadSession(id: number, payload: FileUploadSessionCompletePayload): Promise<FileObjectRow> {
  const response = await http.post<ApiResponse<FileObjectRow>>(`/files/upload-sessions/${id}/complete`, payload);
  return response.data.data;
}

export async function fetchFileLifecyclePolicies(): Promise<FileLifecyclePolicyRow[]> {
  const response = await http.get<ApiResponse<FileLifecyclePolicyRow[]>>("/files/lifecycle-policies");
  return response.data.data;
}

export async function createFileLifecyclePolicy(payload: FileLifecyclePolicySavePayload): Promise<FileLifecyclePolicyRow> {
  const response = await http.post<ApiResponse<FileLifecyclePolicyRow>>("/files/lifecycle-policies", payload);
  return response.data.data;
}

export async function updateFileLifecyclePolicy(id: number, payload: FileLifecyclePolicySavePayload): Promise<FileLifecyclePolicyRow> {
  const response = await http.put<ApiResponse<FileLifecyclePolicyRow>>(`/files/lifecycle-policies/${id}`, payload);
  return response.data.data;
}

export async function fetchFileAccessLogs(): Promise<FileAccessLogRow[]> {
  const response = await http.get<ApiResponse<FileAccessLogRow[]>>("/files/access-logs");
  return response.data.data;
}

export async function createFileAccessLog(payload: FileAccessLogCreatePayload): Promise<FileAccessLogRow> {
  const response = await http.post<ApiResponse<FileAccessLogRow>>("/files/access-logs", payload);
  return response.data.data;
}
