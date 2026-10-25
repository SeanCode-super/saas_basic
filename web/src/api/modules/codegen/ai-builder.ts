import { http } from "@/api/http";
import type { ApiResponse } from "@/types/http";

export interface DynamicFieldItem {
  id?: number;
  fieldCode: string;
  fieldName: string;
  columnType: string;
  javaType: string;
  componentType: "input" | "select" | "date" | "datetime" | "number" | "textarea";
  isRequired: boolean;
  isQuery: boolean;
  queryType?: string;
  dictCode?: string;
  optionsJson?: string;
  sortNo: number;
}

export interface DynamicModelItem {
  id: number;
  tenantId: number;
  modelCode: string;
  modelName: string;
  tableName: string;
  category: string;
  icon: string;
  description: string;
  status: string;
  menuId?: number;
  configJson?: string;
  fields?: DynamicFieldItem[];
  createdAt: string;
}

export interface AiAppDesignResult {
  modelCode: string;
  modelName: string;
  tableName: string;
  category: string;
  icon: string;
  description: string;
  fields: DynamicFieldItem[];
  sampleData: Record<string, unknown>[];
}

export interface DynamicModelPublishPayload {
  modelCode: string;
  modelName: string;
  tableName: string;
  category?: string;
  icon?: string;
  description?: string;
  fields: DynamicFieldItem[];
  seedSampleData?: boolean;
  seedMockData?: boolean;
  sampleData?: Record<string, unknown>[];
}

export interface DynamicDataPagePayload {
  pageNum: number;
  pageSize: number;
  params?: Record<string, unknown>;
  sortField?: string;
  sortAsc?: boolean;
}

export interface DynamicPageResult<T = Record<string, unknown>> {
  records: T[];
  total: number;
  pageNum: number;
  pageSize: number;
}

export async function designAiApp(prompt: string, modelName?: string): Promise<ApiResponse<AiAppDesignResult>> {
  const res = await http.post<ApiResponse<AiAppDesignResult>>("/codegen/ai-builder/prompt-design", {
    prompt,
    modelName: modelName || "DeepSeek-V3"
  });
  return res.data;
}

export async function publishAiApp(payload: DynamicModelPublishPayload): Promise<ApiResponse<DynamicModelItem>> {
  const res = await http.post<ApiResponse<DynamicModelItem>>("/codegen/ai-builder/publish", payload);
  return res.data;
}

export async function listAiModels(): Promise<ApiResponse<DynamicModelItem[]>> {
  const res = await http.get<ApiResponse<DynamicModelItem[]>>("/codegen/ai-builder/models");
  return res.data;
}

export async function deleteAiModel(id: number): Promise<ApiResponse<null>> {
  const res = await http.delete<ApiResponse<null>>(`/codegen/ai-builder/models/${id}`);
  return res.data;
}

export async function fetchDynamicMeta(modelCode: string): Promise<ApiResponse<DynamicModelItem>> {
  const res = await http.get<ApiResponse<DynamicModelItem>>(`/dynamic/${modelCode}/meta`);
  return res.data;
}

export async function fetchDynamicPage(
  modelCode: string,
  payload: DynamicDataPagePayload
): Promise<ApiResponse<DynamicPageResult>> {
  const res = await http.post<ApiResponse<DynamicPageResult>>(`/dynamic/${modelCode}/page`, payload);
  return res.data;
}

export async function fetchDynamicRecord(modelCode: string, id: number): Promise<ApiResponse<Record<string, unknown>>> {
  const res = await http.get<ApiResponse<Record<string, unknown>>>(`/dynamic/${modelCode}/${id}`);
  return res.data;
}

export async function createDynamicRecord(
  modelCode: string,
  data: Record<string, unknown>
): Promise<ApiResponse<Record<string, unknown>>> {
  const res = await http.post<ApiResponse<Record<string, unknown>>>(`/dynamic/${modelCode}`, data);
  return res.data;
}

export async function updateDynamicRecord(
  modelCode: string,
  data: Record<string, unknown>
): Promise<ApiResponse<Record<string, unknown>>> {
  const url = data.id ? `/dynamic/${modelCode}/${data.id}` : `/dynamic/${modelCode}`;
  const res = await http.put<ApiResponse<Record<string, unknown>>>(url, data);
  return res.data;
}

export async function deleteDynamicRecord(modelCode: string, id: number): Promise<ApiResponse<null>> {
  const res = await http.delete<ApiResponse<null>>(`/dynamic/${modelCode}/${id}`);
  return res.data;
}

export async function batchDeleteDynamicRecords(modelCode: string, ids: number[]): Promise<ApiResponse<null>> {
  const res = await http.post<ApiResponse<null>>(`/dynamic/${modelCode}/batch-delete`, ids);
  return res.data;
}

export async function seedDynamicMock(modelCode: string): Promise<ApiResponse<number>> {
  const res = await http.post<ApiResponse<number>>(`/dynamic/${modelCode}/seed-mock`);
  return res.data;
}

export async function batchImportDynamicRecords(
  modelCode: string,
  records: Record<string, unknown>[]
): Promise<ApiResponse<number>> {
  const res = await http.post<ApiResponse<number>>(`/dynamic/${modelCode}/batch-import`, records);
  return res.data;
}
