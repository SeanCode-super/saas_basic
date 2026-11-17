import { http } from "@/api/http";
import type { ApiCatalogSnapshot } from "@/api/modules/system/types";

export interface CpuInfo {
  cpuNum: number;
  totalUsage: number;
  sysUsage: number;
  userUsage: number;
  idleRate: number;
  loadAverage: number;
  cpuModel: string;
}

export interface MemoryInfo {
  totalBytes: number;
  usedBytes: number;
  freeBytes: number;
  usageRate: number;
}

export interface GcMetric {
  name: string;
  count: number;
  timeMillis: number;
}

export interface JvmInfo {
  name: string;
  version: string;
  vendor: string;
  startTime: string;
  uptimeSeconds: number;
  heapMaxBytes: number;
  heapCommittedBytes: number;
  heapUsedBytes: number;
  heapUsageRate: number;
  nonHeapUsedBytes: number;
  nonHeapMaxBytes: number;
  threadCount: number;
  peakThreadCount: number;
  daemonThreadCount: number;
  hasDeadlock: boolean;
  deadlockedThreads: string[];
  gcMetrics: GcMetric[];
  jvmArguments: string[];
}

export interface DbPoolInfo {
  poolName: string;
  activeConnections: number;
  idleConnections: number;
  totalConnections: number;
  threadsAwaitingConnection: number;
  maxPoolSize: number;
  poolUsageRate: number;
}

export interface DiskInfo {
  dirName: string;
  sysTypeName: string;
  typeName: string;
  totalBytes: number;
  freeBytes: number;
  usedBytes: number;
  usageRate: number;
}

export interface ServerSysInfo {
  computerName: string;
  serverIp: string;
  osName: string;
  osArch: string;
  userDir: string;
}

export interface RedisSummary {
  available: boolean;
  version: string;
  connectedClients: number;
  usedMemory: string;
  keysCount: number;
}

export interface SlowSqlSummary {
  thresholdMillis: number;
  totalSlowQueries: number;
}

export interface WorkflowEngineStatus {
  status: string;
  mode: string;
  engineVersion: string;
  transactionType: string;
  definitionCount: number;
  instanceCount: number;
  pendingTaskCount: number;
  totalTaskCount: number;
}

export interface ModuleHealthItem {
  moduleName: string;
  moduleCode: string;
  status: string;
  mode: string;
  description: string;
}

export interface AppStatusInfo {
  deploymentMode: string;
  monolithStatus: string;
  workflow: WorkflowEngineStatus;
  modules: ModuleHealthItem[];
}

export interface ServerMonitorData {
  cpu: CpuInfo;
  memory: MemoryInfo;
  jvm: JvmInfo;
  dbPool: DbPoolInfo;
  disks: DiskInfo[];
  sys: ServerSysInfo;
  redis: RedisSummary;
  slowSql: SlowSqlSummary;
  appStatus: AppStatusInfo;
}

export interface ThreadDumpItem {
  id: number;
  name: string;
  state: string;
  daemon: boolean;
  cpuTimeMillis: number;
  lockName: string;
  lockOwner: string;
  stackTrace: string;
}

export async function fetchServerMonitor(): Promise<ServerMonitorData> {
  const res = await http.get<{ data: ServerMonitorData }>("/infra/server-monitor");
  return res.data.data;
}

export async function fetchThreadDump(): Promise<ThreadDumpItem[]> {
  const res = await http.get<{ data: ThreadDumpItem[] }>("/infra/server-monitor/thread-dump");
  return res.data.data;
}

export interface AlarmTestResult {
  channel: string;
  status: string;
  sentAt: string;
  serverIp: string;
  cpuUsage: string;
  jvmHeapUsedMb: number;
  hikariActiveConnections: number;
  hasDeadlock: boolean;
  alarmMessage: string;
}

export async function testAlarmWebhook(): Promise<AlarmTestResult> {
  const res = await http.post<{ data: AlarmTestResult }>("/infra/server-monitor/alarm-test");
  return res.data.data;
}

export interface TraceRecordItem {
  traceId: string;
  spanId: string;
  parentSpanId: string;
  method: string;
  uri: string;
  tenantId?: number;
  userId?: number;
  statusCode: number;
  durationMs: number;
  outcome: string;
  timestamp: string;
}

export interface TelemetrySnapshotData {
  serviceName: string;
  otelEnabled: boolean;
  otlpEnabled: boolean;
  otlpEndpoint: string;
  prometheusEndpoint: string;
  httpTotalRequests: number;
  httpErrorRequests: number;
  avgLatencyMs: number;
  rateLimitAllowed: number;
  rateLimitRejected: number;
  aiRequestsTotal: number;
  aiPromptTokensTotal: number;
  aiCompletionTokensTotal: number;
  vaultUsingDefaultKey: boolean;
  vaultActiveKeyVersion: string;
  tenantRpmLimit: number;
  aiTenantRpmLimit: number;
  recentTraces: TraceRecordItem[];
}

export async function fetchServerTelemetry(): Promise<TelemetrySnapshotData | null> {
  const res = await http.get<{ data: TelemetrySnapshotData | null }>("/infra/server-monitor/telemetry");
  return res.data.data;
}

export async function fetchServerApiStats(): Promise<ApiCatalogSnapshot | null> {
  const res = await http.get<{ data: ApiCatalogSnapshot | null }>("/infra/server-monitor/api-stats");
  return res.data.data;
}

export interface AlertRuleItem {
  id: number;
  ruleCode: string;
  ruleName: string;
  metricType: string;
  comparisonOp: string;
  thresholdValue: number;
  severity: string;
  cooldownSeconds: number;
  notifyChannels: string;
  webhookUrl?: string | null;
  enabled: boolean;
  lastTriggeredAt?: string | null;
}

export interface AlertRuleSavePayload {
  ruleCode: string;
  ruleName: string;
  metricType: string;
  comparisonOp: string;
  thresholdValue: number;
  severity: string;
  cooldownSeconds: number;
  notifyChannels: string;
  webhookUrl?: string;
  enabled: boolean;
}

export interface AlertEventItem {
  id: number;
  ruleId: number;
  ruleCode: string;
  ruleName: string;
  severity: string;
  metricType: string;
  metricValue: number;
  thresholdValue: number;
  targetEndpoint?: string | null;
  message: string;
  dispatchStatus: string;
  dispatchDetail?: string | null;
  acknowledged: boolean;
  acknowledgedBy?: string | null;
  acknowledgedAt?: string | null;
  triggeredAt: string;
}

export interface AlertEvaluationSummary {
  evaluatedRules: number;
  triggeredEvents: number;
  newEvents: AlertEventItem[];
}

export async function fetchAlertRules(): Promise<AlertRuleItem[]> {
  const res = await http.get<{ data: AlertRuleItem[] }>("/infra/server-monitor/alerts/rules");
  return res.data.data;
}

export async function createAlertRule(payload: AlertRuleSavePayload): Promise<AlertRuleItem> {
  const res = await http.post<{ data: AlertRuleItem }>("/infra/server-monitor/alerts/rules", payload);
  return res.data.data;
}

export async function updateAlertRule(id: number, payload: AlertRuleSavePayload): Promise<AlertRuleItem> {
  const res = await http.put<{ data: AlertRuleItem }>(`/infra/server-monitor/alerts/rules/${id}`, payload);
  return res.data.data;
}

export async function deleteAlertRule(id: number): Promise<void> {
  await http.delete(`/infra/server-monitor/alerts/rules/${id}`);
}

export async function evaluateAlertRules(ignoreCooldown: boolean = false): Promise<AlertEvaluationSummary> {
  const res = await http.post<{ data: AlertEvaluationSummary }>("/infra/server-monitor/alerts/evaluate", null, {
    params: { ignoreCooldown }
  });
  return res.data.data;
}

export async function fetchAlertEvents(limit: number = 50): Promise<AlertEventItem[]> {
  const res = await http.get<{ data: AlertEventItem[] }>("/infra/server-monitor/alerts/events", {
    params: { limit }
  });
  return res.data.data;
}

export async function acknowledgeAlertEvent(id: number): Promise<AlertEventItem> {
  const res = await http.post<{ data: AlertEventItem }>(`/infra/server-monitor/alerts/events/${id}/ack`);
  return res.data.data;
}
