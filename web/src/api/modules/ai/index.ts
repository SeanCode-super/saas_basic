import { http } from "@/api/http";
import type { ApiResponse } from "@/types/http";
import type {
  AiProviderRow,
  AiProviderSavePayload,
  AiModelConfigRow,
  AiModelConfigSavePayload,
  AiTestConnectionPayload,
  AiTestConnectionResult
} from "./types";

export * from "./types";

/** 获取所有 AI 服务商列表 */
export async function fetchAiProviders(): Promise<AiProviderRow[]> {
  const response = await http.get<ApiResponse<AiProviderRow[]>>("/ai/manage/providers");
  return response.data.data;
}

/** 新增或编辑服务商 */
export async function saveAiProvider(payload: AiProviderSavePayload): Promise<number> {
  const response = await http.post<ApiResponse<number>>("/ai/manage/providers", payload);
  return response.data.data;
}

/** 删除服务商 */
export async function deleteAiProvider(id: number): Promise<boolean> {
  const response = await http.delete<ApiResponse<boolean>>(`/ai/manage/providers/${id}`);
  return response.data.data;
}

/** 获取模型目录列表 */
export async function fetchAiModels(providerId?: number, status?: string): Promise<AiModelConfigRow[]> {
  const response = await http.get<ApiResponse<AiModelConfigRow[]>>("/ai/manage/models", {
    params: { providerId, status }
  });
  return response.data.data;
}

/** 保存模型配置 */
export async function saveAiModel(payload: AiModelConfigSavePayload): Promise<number> {
  const response = await http.post<ApiResponse<number>>("/ai/manage/models", payload);
  return response.data.data;
}

/** 删除模型配置 */
export async function deleteAiModel(id: number): Promise<boolean> {
  const response = await http.delete<ApiResponse<boolean>>(`/ai/manage/models/${id}`);
  return response.data.data;
}

/** 连通性测试 (握手/时延探针) */
export async function testAiConnection(payload: AiTestConnectionPayload): Promise<AiTestConnectionResult> {
  const response = await http.post<ApiResponse<AiTestConnectionResult>>("/ai/manage/test-connection", payload);
  return response.data.data;
}

/** 获取前台启用的模型简要列表 (供抽屉和应用使用) */
export async function fetchPublicAiModels(): Promise<Array<{
  id: string;
  name: string;
  vendor: string;
  contextWindow: string;
  status: string;
  isDefault?: boolean;
  hasApiKey?: boolean;
}>> {
  const response = await http.get<ApiResponse<Array<{
    id: string;
    name: string;
    vendor: string;
    contextWindow: string;
    status: string;
    isDefault?: boolean;
    hasApiKey?: boolean;
  }>>>("/ai/models");
  return response.data.data;
}
