import { http } from "@/api/http";

export interface AppServiceItem {
  id: number;
  serviceCode: string;
  serviceName: string;
  serviceType: "JAR" | "NODE" | "DOCKER" | "SCRIPT";
  serviceGroup: string;
  driverType: "HOST_PROCESS" | "DOCKER";
  workDir: string;
  artifactPath?: string;
  startCmd: string;
  stopCmd?: string;
  port?: number;
  healthUrl?: string;
  logPath?: string;
  buildCmd?: string;
  autoRestart?: number;
  status: "RUNNING" | "STOPPED" | "STARTING" | "COMPILING" | "ERROR";
  currentPid?: number;
  uptimeSeconds?: number;
  lastStartedAt?: string;
  lastStoppedAt?: string;
  lastExitCode?: number;
  lastErrorMsg?: string;
  remark?: string;
  sortNo?: number;
  cpuUsagePercent?: number;
  memoryRssBytes?: number;
  threadCount?: number;
  portListening?: boolean;
  healthOk?: boolean;
}

export interface AppBuildRecordItem {
  id: number;
  serviceId: number;
  serviceCode: string;
  buildNo: string;
  buildCmd: string;
  status: "PENDING" | "RUNNING" | "SUCCESS" | "FAILED" | "TIMEOUT";
  triggerType: string;
  artifactGenerated?: string;
  artifactSizeBytes?: number;
  startTime?: string;
  endTime?: string;
  durationMs?: number;
  logExcerpt?: string;
  errorMessage?: string;
  createdBy?: string;
  createdAt?: string;
}

export interface AppArtifactItem {
  id: number;
  serviceId: number;
  serviceCode: string;
  versionTag: string;
  fileName: string;
  filePath: string;
  fileSizeBytes: number;
  fileMd5: string;
  isCurrent: number;
  remark?: string;
  createdAt: string;
}

export interface ActionResult {
  success: boolean;
  action: string;
  message: string;
  status: string;
  pid?: number;
}

export async function fetchAppServices(): Promise<AppServiceItem[]> {
  const res = await http.get<{ data: AppServiceItem[] }>("/infra/devops/services");
  return res.data.data;
}

export async function fetchAppService(id: number): Promise<AppServiceItem> {
  const res = await http.get<{ data: AppServiceItem }>(`/infra/devops/services/${id}`);
  return res.data.data;
}

export async function saveAppService(data: Partial<AppServiceItem>, id?: number): Promise<number> {
  if (id) {
    const res = await http.put<{ data: number }>(`/infra/devops/services/${id}`, data);
    return res.data.data;
  } else {
    const res = await http.post<{ data: number }>("/infra/devops/services", data);
    return res.data.data;
  }
}

export async function deleteAppService(id: number): Promise<void> {
  await http.delete(`/infra/devops/services/${id}`);
}

export async function startAppService(id: number): Promise<ActionResult> {
  const res = await http.post<{ data: ActionResult }>(`/infra/devops/services/${id}/start`);
  return res.data.data;
}

export async function stopAppService(id: number, timeoutSeconds = 15): Promise<ActionResult> {
  const res = await http.post<{ data: ActionResult }>(`/infra/devops/services/${id}/stop?timeoutSeconds=${timeoutSeconds}`);
  return res.data.data;
}

export async function restartAppService(id: number, timeoutSeconds = 15): Promise<ActionResult> {
  const res = await http.post<{ data: ActionResult }>(`/infra/devops/services/${id}/restart?timeoutSeconds=${timeoutSeconds}`);
  return res.data.data;
}

export async function killAppService(id: number): Promise<ActionResult> {
  const res = await http.post<{ data: ActionResult }>(`/infra/devops/services/${id}/kill`);
  return res.data.data;
}

export async function triggerAppBuild(id: number, customBuildCmd?: string): Promise<AppBuildRecordItem> {
  const res = await http.post<{ data: AppBuildRecordItem }>(`/infra/devops/services/${id}/build`, { customBuildCmd });
  return res.data.data;
}

export async function fetchAppBuildRecords(id: number): Promise<AppBuildRecordItem[]> {
  const res = await http.get<{ data: AppBuildRecordItem[] }>(`/infra/devops/services/${id}/build-records`);
  return res.data.data;
}

export async function fetchAppBuildLogs(recordId: number): Promise<string[]> {
  const res = await http.get<{ data: string[] }>(`/infra/devops/build-records/${recordId}/logs`);
  return res.data.data;
}

export async function fetchAppRecentLogs(id: number, lines = 300): Promise<string[]> {
  const res = await http.get<{ data: string[] }>(`/infra/devops/services/${id}/logs/recent?lines=${lines}`);
  return res.data.data;
}

export async function fetchAppArtifacts(id: number): Promise<AppArtifactItem[]> {
  const res = await http.get<{ data: AppArtifactItem[] }>(`/infra/devops/services/${id}/artifacts`);
  return res.data.data;
}

export async function uploadAppArtifact(id: number, file: File, remark?: string): Promise<AppArtifactItem> {
  const formData = new FormData();
  formData.append("file", file);
  if (remark) {
    formData.append("remark", remark);
  }
  const res = await http.post<{ data: AppArtifactItem }>(`/infra/devops/services/${id}/upload-artifact`, formData, {
    headers: { "Content-Type": "multipart/form-data" }
  });
  return res.data.data;
}

export async function rollbackAppArtifact(id: number, artifactId: number): Promise<ActionResult> {
  const res = await http.post<{ data: ActionResult }>(`/infra/devops/services/${id}/artifacts/${artifactId}/rollback`);
  return res.data.data;
}
