import { http } from "@/api/http";

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
