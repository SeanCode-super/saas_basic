import { http } from "@/api/http";
import type { ApiResponse } from "@/types/http";
import type {
  CodegenProjectRow,
  CodegenProjectSavePayload,
  DbTableItem,
  CodegenColumnModel,
  CodegenTableModel,
  CodegenPreviewFile
} from "./types";

export async function fetchCodegenProjects(): Promise<ApiResponse<CodegenProjectRow[]>> {
  const res = await http.get<ApiResponse<CodegenProjectRow[]>>("/codegen/projects");
  return res.data;
}

export async function fetchCodegenProjectDetail(id: number): Promise<ApiResponse<CodegenProjectRow>> {
  const res = await http.get<ApiResponse<CodegenProjectRow>>(`/codegen/projects/${id}`);
  return res.data;
}

export async function createCodegenProject(payload: CodegenProjectSavePayload): Promise<ApiResponse<CodegenProjectRow>> {
  const res = await http.post<ApiResponse<CodegenProjectRow>>("/codegen/projects", payload);
  return res.data;
}

export async function updateCodegenProject(id: number, payload: CodegenProjectSavePayload): Promise<ApiResponse<CodegenProjectRow>> {
  const res = await http.put<ApiResponse<CodegenProjectRow>>(`/codegen/projects/${id}`, payload);
  return res.data;
}

export async function fetchDbTables(): Promise<ApiResponse<DbTableItem[]>> {
  const res = await http.get<ApiResponse<DbTableItem[]>>("/codegen/db-tables");
  return res.data;
}

export async function fetchDbTableColumns(tableName: string): Promise<ApiResponse<CodegenColumnModel[]>> {
  const res = await http.get<ApiResponse<CodegenColumnModel[]>>(`/codegen/db-tables/${tableName}/columns`);
  return res.data;
}

export async function previewCodegen(payload: CodegenTableModel): Promise<ApiResponse<CodegenPreviewFile[]>> {
  const res = await http.post<ApiResponse<CodegenPreviewFile[]>>("/codegen/preview", payload);
  return res.data;
}

export async function downloadCodegenZip(payload: CodegenTableModel): Promise<void> {
  const response = await http.post("/codegen/download-zip", payload, {
    responseType: "blob"
  });
  const blob = new Blob([response as unknown as BlobPart], { type: "application/zip" });
  const downloadUrl = window.URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = downloadUrl;
  link.download = `${payload.moduleName || "codegen"}-bundle.zip`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  window.URL.revokeObjectURL(downloadUrl);
}

export async function writeCodegenToDisk(payload: CodegenTableModel): Promise<ApiResponse<{ projectRoot: string; count: number; files: string[] }>> {
  const res = await http.post<ApiResponse<{ projectRoot: string; count: number; files: string[] }>>("/codegen/write-to-disk", payload);
  return res.data;
}
