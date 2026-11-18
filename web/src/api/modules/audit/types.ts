export interface AuditOverview {
  loginEventsToday: number;
  operationEventsToday: number;
  riskMatchesToday: number;
  activeBlackRules: number;
}

export interface LoginLog {
  id: number;
  tenantId: number;
  userId: number;
  username: string;
  loginType: string;
  loginIp: string;
  success: boolean;
  failReason?: string;
  occurredAt: string;
}

export interface OperationLog {
  id: number | string;
  tenantId: number;
  operatorUserId: number;
  operatorName: string;
  bizModule: string;
  bizType: string;
  bizId: string;
  operationType: string;
  success: boolean;
  occurredAt: string;
}

export interface FieldDiffItem {
  fieldPath: string;
  beforeValue?: string | null;
  afterValue?: string | null;
  changeType: "ADDED" | "REMOVED" | "MODIFIED";
}

export interface OperationLogDiffResponse {
  id: number | string;
  bizModule: string;
  bizType: string;
  bizId: string;
  operationType: string;
  operatorName: string;
  occurredAt: string;
  beforeJson?: string | null;
  afterJson?: string | null;
  changedFields: FieldDiffItem[];
}

export interface AuditQueryParam {
  username?: string;
  success?: boolean;
  bizModule?: string;
  operationType?: string;
}

export interface BlacklistItem {
  id: number;
  tenantId: number;
  type: "IP" | "ACCOUNT" | "DEVICE" | string;
  targetValue: string;
  reason?: string;
  blockedAt: string;
  expireAt: string;
  status: "ACTIVE" | "EXPIRED" | string;
  operator?: string;
}

export interface BlacklistCreatePayload {
  type: string;
  targetValue: string;
  reason?: string;
  expireDays?: number | null;
}

export interface RiskRuleItem {
  id: number;
  code: string;
  name: string;
  severity: "CRITICAL" | "HIGH" | "MEDIUM" | "LOW" | string;
  ruleExpr: string;
  action: string;
  description?: string;
  enabled: boolean;
}

export interface RiskEvaluatePayload {
  ruleCode: string;
  sampleAccount?: string;
  sampleIp?: string;
  frequency?: number;
}

export interface RiskEvaluateResult {
  evaluatedAt: string;
  matchedRule: string;
  decision: string;
  matchedThreshold: string;
  enforcedAction: string;
  latencyMs: number;
  recentLoginFailures: number;
  recentOperations: number;
}

export interface HighRiskCatalogItem {
  code: string;
  name: string;
  httpMethod: string;
  urlPattern: string;
  category: string;
  riskLevel: string;
  requireApproval: boolean;
  maskingPolicy: string;
  retentionDays: number;
}
