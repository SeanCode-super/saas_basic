export interface SystemConfigRow {
  id: number;
  tenantId: number;
  group: string;
  key: string;
  name: string;
  value: string;
  valueType: string;
  status: string;
  remark?: string;
}

export interface SystemConfigSavePayload {
  tenantId: number;
  group: string;
  key: string;
  name: string;
  value: string;
  valueType: string;
  status: string;
  remark?: string;
}

export interface SystemDictTypeRow {
  id: number;
  tenantId: number;
  dictCode: string;
  dictName: string;
  dictScope: string;
  status: string;
  cacheable: boolean;
  extJson?: string;
  remark?: string;
  itemCount: number;
}

export interface SystemDictTypeSavePayload {
  tenantId: number;
  dictCode: string;
  dictName: string;
  dictScope: string;
  status: string;
  cacheable: boolean;
  extJson?: string;
  remark?: string;
}

export interface SystemDictItemRow {
  id: number;
  tenantId: number;
  dictTypeId: number;
  itemValue: string;
  itemLabel: string;
  itemColor?: string;
  itemTag?: string;
  parentId: number;
  sortNo: number;
  status: string;
  defaultItem: boolean;
  extJson?: string;
  remark?: string;
}

export interface SystemDictItemSavePayload {
  tenantId: number;
  dictTypeId: number;
  itemValue: string;
  itemLabel: string;
  itemColor?: string;
  itemTag?: string;
  parentId?: number;
  sortNo?: number;
  status: string;
  defaultItem: boolean;
  extJson?: string;
  remark?: string;
}

export interface SystemDictStatusPayload {
  tenantId: number;
  status: string;
}

export interface SystemDictOption {
  dictCode: string;
  label: string;
  value: string;
  color?: string;
  tag?: string;
  defaultItem: boolean;
}

export interface RedisInfoData {
  available: boolean;
  mode: string;
  redisVersion: string;
  uptimeInSeconds: number;
  connectedClients: number;
  usedMemoryHuman: string;
  usedMemoryPeakHuman: string;
  totalCommandsProcessed: number;
  instantaneousOpsPerSec: number;
  totalKeys: number;
  hitRate: number;
  message: string;
}

export interface RedisKeyItem {
  key: string;
  type: string;
  ttlSeconds: number;
  size: string;
}

export interface SlowSqlRecord {
  id: string;
  sql: string;
  costMillis: number;
  executedAt: string;
  threadName: string;
  stackTrace: string;
}

export interface SlowSqlReportData {
  thresholdMillis: number;
  totalSlowQueries: number;
  recentSlowQueries: SlowSqlRecord[];
  topSlowestQueries: SlowSqlRecord[];
}

export interface JvmInfoData {
  javaVersion: string;
  javaVendor: string;
  jvmName: string;
  startTime: number;
  uptimeSeconds: number;
  heapUsedBytes: number;
  heapMaxBytes: number;
  heapCommittedBytes: number;
  heapUsedPercent: number;
  nonHeapUsedBytes: number;
  activeThreads: number;
  peakThreads: number;
  totalStartedThreads: number;
  gcMetrics: { name: string; count: number; timeMillis: number }[];
  availableProcessors: number;
  systemCpuLoad: number;
}

// ==================== 全链路可控传输加密 (Transport Crypto) ====================

export type TransportCryptoMode = "OFF" | "OPTIONAL" | "REQUIRED";
export type TransportScopeMode = "ALL_API" | "SENSITIVE_ONLY" | "CUSTOM";
export type TransportKeySource = "AUTO_GENERATED" | "CUSTOM_IMPORTED";

export interface RetainedKeySummary {
  keyId: string;
  keySource: TransportKeySource;
  publicKey: string;
  fingerprintSha256: string;
  issuedAtEpochMs: number;
  active: boolean;
  remark?: string;
}

export interface TransportKeySummary {
  activeKid: string;
  keySource: TransportKeySource;
  publicKey: string;
  fingerprintSha256: string;
  issuedAtEpochMs: number;
  retainedKeyCount: number;
  keyRotationHours: number;
  maxGraceKeys: number;
  vaultUsingDefaultKey: boolean;
  vaultActiveKeyVersion: string;
  retainedKeys: RetainedKeySummary[];
}

export interface TransportTelemetrySummary {
  totalRequests: number;
  encryptedRequests: number;
  plaintextAllowed: number;
  rejectedPlaintext: number;
  rejectedReplay: number;
  decryptErrors: number;
}

export interface TransportSecurityOverview {
  mode: TransportCryptoMode;
  scopeMode: TransportScopeMode;
  algorithm: string;
  encryptRequest: boolean;
  encryptResponse: boolean;
  encryptQuery: boolean;
  replayProtection: boolean;
  timestampSkewSeconds: number;
  keyRotationHours: number;
  maxGraceKeys: number;
  includePaths: string[];
  excludePaths: string[];
  sensitivePaths: string[];
  policyVersion: number;
  updatedAtEpochMs: number;
  remark?: string;
  keySummary: TransportKeySummary;
  telemetry: TransportTelemetrySummary;
}

export interface TransportPolicyUpdatePayload {
  mode: TransportCryptoMode;
  scopeMode: TransportScopeMode;
  encryptRequest: boolean;
  encryptResponse: boolean;
  encryptQuery: boolean;
  replayProtection: boolean;
  timestampSkewSeconds: number;
  keyRotationHours?: number;
  maxGraceKeys?: number;
  includePaths: string[];
  excludePaths: string[];
  sensitivePaths: string[];
  remark?: string;
}

export interface TransportKeyConfigurePayload {
  mode: "CUSTOM" | "GENERATE";
  keyId?: string;
  privateKeyInput?: string;
  keyRotationHours?: number;
  maxGraceKeys?: number;
  remark?: string;
}

export interface ApiPackageTreeNode {
  id: string;
  nodeType: "PROJECT" | "PACKAGE" | "CONTROLLER";
  label: string;
  fullName: string;
  level: number;
  endpointCount: number;
  totalCalls: number;
  encryptedCalls: number;
  plaintextCalls: number;
  rejectedCalls: number;
  suggestedAntPatterns: string[];
  children: ApiPackageTreeNode[];
}

export interface ApiPathGroupItem {
  pattern: string;
  label: string;
  depth: number;
  endpointCount: number;
  totalCalls: number;
  encryptedCalls: number;
  plaintextCalls: number;
  rejectedCalls: number;
}

export interface ApiModuleStatItem {
  moduleCode: string;
  moduleName: string;
  endpointCount: number;
  totalCalls: number;
  encryptedCalls: number;
  plaintextCalls: number;
  rejectedCalls: number;
  errorCalls: number;
  suggestedAntPatterns: string[];
}

export interface ApiEndpointStatView {
  endpointKey: string;
  projectCode: string;
  projectName: string;
  packageName: string;
  packageLevels: string[];
  moduleCode: string;
  moduleName: string;
  controllerName: string;
  handlerMethod: string;
  httpMethod: string;
  pathPattern: string;
  antPattern: string;
  urlPrefix2: string;
  urlPrefix3: string;
  effectiveAction: "REQUIRED" | "OPTIONAL" | "BYPASS";
  matchedReason: string;
  alwaysBypass: boolean;
  totalCalls: number;
  encryptedCalls: number;
  plaintextCalls: number;
  rejectedCalls: number;
  errorCalls: number;
  avgLatencyMs: number;
  p95LatencyMs: number;
  p99LatencyMs: number;
  lastCalledAtEpochMs: number;
}

export interface ApiCatalogSummary {
  projectCode: string;
  projectName: string;
  totalEndpoints: number;
  activeEndpoints: number;
  totalModules: number;
  totalPackages: number;
  totalCalls: number;
  encryptedCalls: number;
  plaintextCalls: number;
  rejectedCalls: number;
  errorCalls: number;
}

export interface ApiCatalogSnapshot {
  summary: ApiCatalogSummary;
  packageTree: ApiPackageTreeNode[];
  pathGroups: ApiPathGroupItem[];
  modules: ApiModuleStatItem[];
  endpoints: ApiEndpointStatView[];
}

export interface TransportRoundtripResult {
  echoedText: string;
  transportEncrypted: boolean;
  mode: string;
  scopeMode: string;
  activeKid: string;
  policyVersion: number;
  serverHandledAtMs: number;
}
