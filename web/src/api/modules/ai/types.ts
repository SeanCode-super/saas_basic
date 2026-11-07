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
