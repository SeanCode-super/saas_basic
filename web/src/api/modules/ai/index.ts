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
  AiCommandHistoryRow,
  KnowledgeBaseRow,
  KnowledgeDocRow,
  KnowledgeChunkRow,
  KnowledgeCitation,
  KnowledgeAskResponse,
  DocumentReviewResponse,
  AiAgentRow,
  AiToolOption,
  AgentChatResponse,
  FinetuneDatasetRow,
  FinetuneSampleRow,
  FinetuneJobRow
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

// --- Knowledge Base & Hybrid RAG ---
export async function fetchKnowledgeBases(): Promise<KnowledgeBaseRow[]> {
  const res = await http.get<ApiResponse<KnowledgeBaseRow[]>>("/ai/knowledge/bases");
  return res.data.data;
}

export async function saveKnowledgeBase(payload: Partial<KnowledgeBaseRow>): Promise<KnowledgeBaseRow> {
  const res = await http.post<ApiResponse<KnowledgeBaseRow>>("/ai/knowledge/bases", payload);
  return res.data.data;
}

export async function fetchKnowledgeDocs(kbId?: number): Promise<KnowledgeDocRow[]> {
  const res = await http.get<ApiResponse<KnowledgeDocRow[]>>("/ai/knowledge/docs", {
    params: kbId ? { kbId } : undefined
  });
  return res.data.data;
}

export async function fetchKnowledgeChunks(docId: number): Promise<KnowledgeChunkRow[]> {
  const res = await http.get<ApiResponse<KnowledgeChunkRow[]>>(`/ai/knowledge/docs/${docId}/chunks`);
  return res.data.data;
}

export async function uploadKnowledgeDoc(kbId: number, file: File): Promise<KnowledgeDocRow> {
  const formData = new FormData();
  formData.append("kbId", String(kbId));
  formData.append("file", file);
  const res = await http.post<ApiResponse<KnowledgeDocRow>>("/ai/knowledge/docs/upload", formData, {
    headers: { "Content-Type": "multipart/form-data" }
  });
  return res.data.data;
}

export async function ingestKnowledgeText(payload: {
  kbId: number;
  docName: string;
  content: string;
  fileType?: string;
}): Promise<KnowledgeDocRow> {
  const res = await http.post<ApiResponse<KnowledgeDocRow>>("/ai/knowledge/docs/text", payload);
  return res.data.data;
}

export async function deleteKnowledgeDoc(docId: number): Promise<void> {
  await http.delete(`/ai/knowledge/docs/${docId}`);
}

export async function searchKnowledge(payload: {
  kbId?: number;
  question: string;
  topK?: number;
}): Promise<KnowledgeCitation[]> {
  const res = await http.post<ApiResponse<KnowledgeCitation[]>>("/ai/knowledge/search", payload);
  return res.data.data;
}

export async function askKnowledge(payload: {
  kbId?: number;
  question: string;
  topK?: number;
  modelId?: string;
}): Promise<KnowledgeAskResponse> {
  const res = await http.post<ApiResponse<KnowledgeAskResponse>>("/ai/knowledge/ask", payload);
  return res.data.data;
}

export async function reviewDocumentCompliance(payload: {
  kbId?: number;
  documentTitle: string;
  documentText: string;
  modelId?: string;
}): Promise<DocumentReviewResponse> {
  const res = await http.post<ApiResponse<DocumentReviewResponse>>("/ai/knowledge/review-document", payload);
  return res.data.data;
}

// --- Visual Agent Studio & CRM Copilot ---
export async function fetchAgents(): Promise<AiAgentRow[]> {
  const res = await http.get<ApiResponse<AiAgentRow[]>>("/ai/agents");
  return res.data.data;
}

export async function fetchAgentTools(): Promise<AiToolOption[]> {
  const res = await http.get<ApiResponse<AiToolOption[]>>("/ai/agents/tools");
  return res.data.data;
}

export async function saveAgent(payload: Partial<AiAgentRow>): Promise<AiAgentRow> {
  const res = await http.post<ApiResponse<AiAgentRow>>("/ai/agents", payload);
  return res.data.data;
}

export async function deleteAgent(id: number): Promise<void> {
  await http.delete(`/ai/agents/${id}`);
}

export async function chatWithAgent(payload: {
  agentId: number;
  sessionId?: number;
  message: string;
}): Promise<AgentChatResponse> {
  const res = await http.post<ApiResponse<AgentChatResponse>>("/ai/agents/chat", payload);
  return res.data.data;
}

export async function fetchCrmCopilotSuggestion(bizType: string, bizId: number): Promise<{
  targetName: string;
  riskLevel: string;
  riskSummary: string;
  suggestedPitch: string;
  policyReferences: KnowledgeCitation[];
  recentContractCount: number;
}> {
  const res = await http.get<ApiResponse<any>>("/ai/copilot/crm-suggestion", {
    params: { bizType, bizId }
  });
  return res.data.data;
}

// --- Fine-tuning Workbench ---
export async function fetchFinetuneDatasets(): Promise<FinetuneDatasetRow[]> {
  const res = await http.get<ApiResponse<FinetuneDatasetRow[]>>("/ai/finetune/datasets");
  return res.data.data;
}

export async function saveFinetuneDataset(payload: Partial<FinetuneDatasetRow>): Promise<FinetuneDatasetRow> {
  const res = await http.post<ApiResponse<FinetuneDatasetRow>>("/ai/finetune/datasets", payload);
  return res.data.data;
}

export async function fetchFinetuneSamples(datasetId?: number): Promise<FinetuneSampleRow[]> {
  const res = await http.get<ApiResponse<FinetuneSampleRow[]>>("/ai/finetune/samples", {
    params: datasetId ? { datasetId } : undefined
  });
  return res.data.data;
}

export async function addFinetuneSample(payload: {
  datasetId: number;
  instruction: string;
  inputText?: string;
  outputText: string;
  desensitize?: boolean;
}): Promise<FinetuneSampleRow> {
  const res = await http.post<ApiResponse<FinetuneSampleRow>>("/ai/finetune/samples", payload);
  return res.data.data;
}

export async function harvestNl2SqlSamples(datasetId: number, limit = 15): Promise<{ imported: number }> {
  const res = await http.post<ApiResponse<{ imported: number }>>(`/ai/finetune/datasets/${datasetId}/harvest-nl2sql`, null, {
    params: { limit }
  });
  return res.data.data;
}

export async function reviewFinetuneSample(sampleId: number, reviewStatus: "APPROVED" | "REJECTED"): Promise<FinetuneSampleRow> {
  const res = await http.post<ApiResponse<FinetuneSampleRow>>(`/ai/finetune/samples/${sampleId}/review`, { reviewStatus });
  return res.data.data;
}

export async function exportFinetuneJsonl(datasetId: number, formatType = "ALPACA"): Promise<void> {
  const res = await http.get<Blob>(`/ai/finetune/datasets/${datasetId}/export-jsonl`, {
    params: { formatType },
    responseType: "blob"
  });
  const url = URL.createObjectURL(res.data);
  const link = document.createElement("a");
  link.href = url;
  link.download = `finetune_dataset_${datasetId}_${formatType.toLowerCase()}.jsonl`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export async function fetchFinetuneJobs(): Promise<FinetuneJobRow[]> {
  const res = await http.get<ApiResponse<FinetuneJobRow[]>>("/ai/finetune/jobs");
  return res.data.data;
}

export async function startFinetuneJob(payload: {
  jobName: string;
  datasetId: number;
  baseModel: string;
  tuningMethod: string;
  epochs: number;
  learningRate: string;
  loraRank: number;
}): Promise<FinetuneJobRow> {
  const res = await http.post<ApiResponse<FinetuneJobRow>>("/ai/finetune/jobs", payload);
  return res.data.data;
}
