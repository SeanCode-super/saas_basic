export interface AiProviderRow {
  id: number;
  tenantId: number;
  providerCode: string;
  providerName: string;
  providerType: string;
  baseUrl: string;
  apiKey?: string;
  apiKeyMasked?: string;
  hasApiKey: boolean;
  encryptedAtRest?: boolean;
  keyVersion?: string;
  cipherDigest?: string;
  rotationCount?: number;
  status: "ENABLED" | "DISABLED";
  isDefault: number;
  sortNo: number;
  remark?: string;
  modelCount?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface AiProviderSavePayload {
  id?: number;
  tenantId?: number;
  providerCode: string;
  providerName: string;
  providerType?: string;
  baseUrl: string;
  apiKey?: string;
  status?: string;
  isDefault?: number;
  sortNo?: number;
  remark?: string;
}

export interface AiModelConfigRow {
  id: number;
  tenantId: number;
  providerId: number;
  providerName?: string;
  providerCode?: string;
  modelId: string;
  modelName: string;
  category: "CHAT" | "REASONING" | "CODER" | string;
  contextWindow: string;
  maxTokens: number;
  temperature: number;
  isDefault: number;
  status: "ENABLED" | "DISABLED";
  sortNo: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface AiModelConfigSavePayload {
  id?: number;
  tenantId?: number;
  providerId: number;
  modelId: string;
  modelName: string;
  category?: string;
  contextWindow?: string;
  maxTokens?: number;
  temperature?: number;
  isDefault?: number;
  status?: string;
  sortNo?: number;
}

export interface AiTestConnectionPayload {
  providerId?: number;
  baseUrl?: string;
  apiKey?: string;
  testModel?: string;
}

export interface AiTestConnectionResult {
  success: boolean;
  latencyMillis: number;
  message: string;
  replySnippet?: string;
}

export interface AiCredentialRefRow {
  id: number;
  tenantId: number;
  credentialCode: string;
  providerId: number;
  keyAlias: string;
  maskedHint: string;
  encryptionAlgo: string;
  keyVersion: string;
  cipherDigest: string;
  rotationCount: number;
  lastRotatedAt?: string;
  status: string;
  remark?: string;
}

export interface VaultRotateResult {
  rotatedProviders: number;
  rotatedMcpServers: number;
  activeKeyVersion: string;
  message: string;
}

export interface AiPromptRow {
  id: number;
  tenantId: number;
  promptCode: string;
  promptName: string;
  category: string;
  activeVersion: number;
  status: string;
  systemPrompt: string;
  userTemplate: string;
  modelHint: string;
  remark?: string;
  updatedAt?: string;
}

export interface AiPromptVersionRow {
  id: number;
  promptId: number;
  versionNo: number;
  systemPrompt: string;
  userTemplate: string;
  variablesJson: string;
  modelHint?: string;
  changelog: string;
  isPublished: number;
  createdAt?: string;
}

export interface AiPromptSavePayload {
  promptCode: string;
  promptName?: string;
  category?: string;
  systemPrompt: string;
  userTemplate?: string;
  variablesJson?: string;
  modelHint?: string;
  changelog?: string;
  publishImmediately?: boolean;
}

export interface AiMcpServerRow {
  id: number;
  tenantId: number;
  serverCode: string;
  serverName: string;
  transportType: "BUILTIN_LOOPBACK" | "STREAMABLE_HTTP" | "SSE" | string;
  endpointUrl: string;
  authHeaderName: string;
  hasAuthToken: boolean;
  authTokenMasked: string;
  allowedTools: string[];
  healthStatus: string;
  lastCheckedAt?: string;
  status: string;
  remark?: string;
}

export interface AiMcpServerSavePayload {
  id?: number;
  serverCode: string;
  serverName: string;
  transportType: string;
  endpointUrl: string;
  authHeaderName?: string;
  authToken?: string;
  allowedTools?: string[];
  status?: string;
  remark?: string;
}

export interface McpDiscoveredTool {
  name: string;
  description: string;
  inputSchemaJson: string;
  whitelisted: boolean;
}

export interface AiMcpProbeResult {
  success: boolean;
  protocolVersion: string;
  serverTitle: string;
  latencyMs: number;
  message: string;
  discoveredTools: McpDiscoveredTool[];
}

export interface AiToolRow {
  id: number;
  tenantId: number;
  toolCode: string;
  toolName: string;
  semanticVersion: string;
  description: string;
  inputSchemaJson: string;
  permissionCode: string;
  riskLevel: "READ_ONLY" | "LOW" | "HIGH" | string;
  adapterType: string;
  mcpServerId?: number;
  whitelisted: boolean;
  idempotent: boolean;
  timeoutMs: number;
  status: string;
}

export interface AiTenantQuotaRow {
  tenantId: number;
  monthlyTokenLimit: number;
  usedTokensMonth: number;
  promptTokensMonth: number;
  completionTokensMonth: number;
  remainingTokens: number;
  usagePercent: number;
  estimatedCostMonth: number;
  billingMonth: string;
  rpmLimit: number;
  status: string;
}

export interface AiRunStepRow {
  id: number;
  stepNo: number;
  stepType: string;
  modelId?: string;
  toolCode?: string;
  status: string;
  promptTokens: number;
  completionTokens: number;
  latencyMs: number;
  errorType?: string;
  detailJson?: string;
  createdAt?: string;
}

export interface AiToolCallRow {
  id: number;
  runId: number;
  callNo: string;
  toolCode: string;
  adapterType: string;
  riskLevel: string;
  permissionCode: string;
  idempotencyKey: string;
  argumentsSummary: string;
  resultSummary?: string;
  permissionGranted: boolean;
  approvalStatus: "AUTO_APPROVED" | "PENDING_APPROVAL" | "APPROVED" | "REJECTED" | string;
  approvedBy?: number;
  approvedAt?: string;
  rejectReason?: string;
  executionStatus: string;
  latencyMs: number;
  createdAt?: string;
}

export interface AiRunRow {
  id: number;
  tenantId: number;
  runNo: string;
  userId: number;
  username: string;
  modelId: string;
  providerCode: string;
  promptCode?: string;
  promptVersion?: number;
  status: string;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  estimatedCost: number;
  latencyMs: number;
  errorType?: string;
  errorMessage?: string;
  traceId?: string;
  inputSummary?: string;
  outputSummary?: string;
  startedAt?: string;
  finishedAt?: string;
  steps: AiRunStepRow[];
  toolCalls: AiToolCallRow[];
}

export interface AiRunCreatePayload {
  modelId?: string;
  promptCode?: string;
  inputPrompt?: string;
  toolCode?: string;
  toolArguments?: Record<string, unknown>;
}

export interface Nl2SqlSchemaColumn {
  columnCode: string;
  columnLabel: string;
  dataType: string;
  sensitive: boolean;
}

export interface Nl2SqlSchemaTable {
  tableName: string;
  tableLabel: string;
  domain: string;
  hasDeletedColumn: boolean;
  columns: Nl2SqlSchemaColumn[];
}

export interface Nl2SqlQueryRequest {
  question: string;
  modelId?: string;
  maxRows?: number;
}

export interface Nl2SqlQueryResponse {
  question: string;
  tableNames: string[];
  sql: string;
  columns: string[];
  columnLabels: Record<string, string>;
  rows: Array<Record<string, unknown>>;
  rowCount: number;
  recommendedChartType: "BAR" | "LINE" | "PIE" | "STAT" | "TABLE" | string;
  chartTitle: string;
  chartXAxis: string;
  chartSeriesField: string;
  insights: string[];
  followUpSuggestions: string[];
  maskedFields: string[];
  executionEngine: string;
  latencyMs: number;
}

export interface AiCommandRequest {
  input: string;
  currentPath?: string;
  modelId?: string;
  executeNow?: boolean;
}

export interface AiCommandResponse {
  intentCode: string;
  intentLabel: string;
  confidence: number;
  explanation: string;
  requiresConfirmation: boolean;
  suggestedRoute: string;
  actionPayload: Record<string, unknown>;
  nl2sqlResult?: Nl2SqlQueryResponse | null;
}

export interface AiCommandHistoryRow {
  id: number | string;
  rawCommand: string;
  intentCode: string;
  confidence: number;
  generatedSql?: string;
  resultSummary?: string;
  rowCount: number;
  latencyMs: number;
  status: string;
  createdAt?: string;
}
