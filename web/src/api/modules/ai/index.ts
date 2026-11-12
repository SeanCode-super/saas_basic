import { http } from "@/api/http";
import type { ApiResponse } from "@/types/http";
import type {
  AiProviderRow,
  AiProviderSavePayload,
  AiModelConfigRow,
  AiModelConfigSavePayload,
  AiTestConnectionPayload,
  AiTestConnectionResult,
  AiCredentialRefRow,
  VaultRotateResult,
  AiPromptRow,
  AiPromptVersionRow,
  AiPromptSavePayload,
  AiMcpServerRow,
  AiMcpServerSavePayload,
  AiMcpProbeResult,
  AiToolRow,
  AiTenantQuotaRow,
  AiRunRow,
  AiToolCallRow,
  AiRunCreatePayload,
  AiCommandRequest,
  AiCommandResponse,
  Nl2SqlQueryRequest,
  Nl2SqlQueryResponse,
  Nl2SqlSchemaTable,
  AiCommandHistoryRow
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

/** 获取凭据 Vault 引用审计列表 */
export async function fetchAiCredentialRefs(): Promise<AiCredentialRefRow[]> {
  const response = await http.get<ApiResponse<AiCredentialRefRow[]>>("/ai/manage/credentials");
  return response.data.data;
}

/** 一键执行 AES-256-GCM 密钥轮换与密文升级 */
export async function rotateAllVaultCredentials(): Promise<VaultRotateResult> {
  const response = await http.post<ApiResponse<VaultRotateResult>>("/ai/manage/vault/rotate-all");
  return response.data.data;
}

/** 获取版本化 Prompt 列表 */
export async function fetchAiPrompts(): Promise<AiPromptRow[]> {
  const response = await http.get<ApiResponse<AiPromptRow[]>>("/ai/prompts");
  return response.data.data;
}

/** 获取 Prompt 历史版本 */
export async function fetchPromptVersions(promptCode: string): Promise<AiPromptVersionRow[]> {
  const response = await http.get<ApiResponse<AiPromptVersionRow[]>>(`/ai/prompts/${promptCode}/versions`);
  return response.data.data;
}

/** 保存并发布新版本 Prompt */
export async function savePromptVersion(payload: AiPromptSavePayload): Promise<AiPromptVersionRow> {
  const response = await http.post<ApiResponse<AiPromptVersionRow>>("/ai/prompts", payload);
  return response.data.data;
}

/** 切换 Prompt 激活版本号 */
export async function publishPromptVersion(promptCode: string, versionNo: number): Promise<AiPromptRow> {
  const response = await http.post<ApiResponse<AiPromptRow>>(`/ai/prompts/${promptCode}/publish/${versionNo}`);
  return response.data.data;
}

/** 获取 MCP 服务器列表 */
export async function fetchMcpServers(): Promise<AiMcpServerRow[]> {
  const response = await http.get<ApiResponse<AiMcpServerRow[]>>("/ai/mcp-servers");
  return response.data.data;
}

/** 新增/更新 MCP 服务器 */
export async function saveMcpServer(payload: AiMcpServerSavePayload): Promise<number> {
  const response = await http.post<ApiResponse<number>>("/ai/mcp-servers", payload);
  return response.data.data;
}

/** 删除 MCP 服务器 */
export async function deleteMcpServer(id: number): Promise<boolean> {
  const response = await http.delete<ApiResponse<boolean>>(`/ai/mcp-servers/${id}`);
  return response.data.data;
}

/** MCP JSON-RPC 探测与工具发现 */
export async function probeMcpServer(id: number): Promise<AiMcpProbeResult> {
  const response = await http.post<ApiResponse<AiMcpProbeResult>>(`/ai/mcp-servers/${id}/probe`);
  return response.data.data;
}

/** 获取受控工具清单 */
export async function fetchAiTools(): Promise<AiToolRow[]> {
  const response = await http.get<ApiResponse<AiToolRow[]>>("/ai/tools");
  return response.data.data;
}

/** 更新工具白名单与风险等级 */
export async function updateAiToolGovernance(
  id: number,
  payload: { whitelisted?: boolean; riskLevel?: string }
): Promise<AiToolRow> {
  const response = await http.put<ApiResponse<AiToolRow>>(`/ai/tools/${id}/whitelist`, payload);
  return response.data.data;
}

/** 获取租户 AI 配额统计 */
export async function fetchAiTenantQuota(): Promise<AiTenantQuotaRow> {
  const response = await http.get<ApiResponse<AiTenantQuotaRow>>("/ai/quota");
  return response.data.data;
}

/** 更新租户 AI 配额上限 */
export async function updateAiTenantQuota(payload: { monthlyTokenLimit?: number; rpmLimit?: number }): Promise<AiTenantQuotaRow> {
  const response = await http.put<ApiResponse<AiTenantQuotaRow>>("/ai/quota", payload);
  return response.data.data;
}

/** 获取 AI Run 历史记录 */
export async function fetchAiRuns(): Promise<AiRunRow[]> {
  const response = await http.get<ApiResponse<AiRunRow[]>>("/ai/runs");
  return response.data.data;
}

/** 触发新的受控 Run */
export async function createAiRun(payload: AiRunCreatePayload): Promise<AiRunRow> {
  const response = await http.post<ApiResponse<AiRunRow>>("/ai/runs", payload);
  return response.data.data;
}

/** 获取待审批的高危 ToolCall 列表 */
export async function fetchPendingToolApprovals(): Promise<AiToolCallRow[]> {
  const response = await http.get<ApiResponse<AiToolCallRow[]>>("/ai/tool-calls/pending");
  return response.data.data;
}

/** 人工审批放行高危工具调用 */
export async function approveAiToolCall(callId: number, reason?: string): Promise<AiRunRow> {
  const response = await http.post<ApiResponse<AiRunRow>>(`/ai/tool-calls/${callId}/approve`, { reason });
  return response.data.data;
}

/** 驳回高危工具调用 */
export async function rejectAiToolCall(callId: number, reason?: string): Promise<AiRunRow> {
  const response = await http.post<ApiResponse<AiRunRow>>(`/ai/tool-calls/${callId}/reject`, { reason });
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

/** ⌘K 全局 AI 多意图路由解析与即席执行 */
export async function interpretAiCommand(payload: AiCommandRequest): Promise<AiCommandResponse> {
  const response = await http.post<ApiResponse<AiCommandResponse>>("/ai/command/interpret", payload);
  return response.data.data;
}

/** 获取 AI 命令与 NL2SQL 历史 */
export async function fetchAiCommandHistory(limit = 20): Promise<AiCommandHistoryRow[]> {
  const response = await http.get<ApiResponse<AiCommandHistoryRow[]>>("/ai/command/history", {
    params: { limit }
  });
  return response.data.data;
}

/** NL2SQL 自然语言安全查数 */
export async function executeNl2SqlQuery(payload: Nl2SqlQueryRequest): Promise<Nl2SqlQueryResponse> {
  const response = await http.post<ApiResponse<Nl2SqlQueryResponse>>("/ai/nl2sql/query", payload);
  return response.data.data;
}

/** 获取当前租户允许查询的 NL2SQL Schema 目录 */
export async function fetchNl2SqlSchemas(): Promise<Nl2SqlSchemaTable[]> {
  const response = await http.get<ApiResponse<Nl2SqlSchemaTable[]>>("/ai/nl2sql/schemas");
  return response.data.data;
}
