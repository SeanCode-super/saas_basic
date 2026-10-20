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
