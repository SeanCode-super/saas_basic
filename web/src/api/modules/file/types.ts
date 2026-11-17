export interface FileCapability {
  providers: string[];
  directUploadEnabled: boolean;
  chunkUploadEnabled: boolean;
  auditEnabled: boolean;
}

export interface StorageAvailability {
  configured: boolean;
  reasonCode: string;
  message: string;
  activeStorageId?: number | null;
  storageCode?: string | null;
  storageName?: string | null;
  storageType?: string | null;
  uploadMode?: string | null;
  basePath?: string | null;
  defaultFolder?: string | null;
  resolvedTargetDir?: string | null;
  resolvedPathPattern?: string | null;
}

export interface FileStorageRow {
  id: number;
  tenantId: number;
  storageCode: string;
  storageName: string;
  storageType: string;
  endpoint?: string;
  bucketDefault?: string;
  regionName?: string;
  basePath?: string;
  defaultFolder?: string;
  pathPattern?: string;
  maxFileMb?: number;
  allowedExts?: string;
  publicBaseUrl?: string;
  status: string;
  isDefault?: boolean;
  uploadMode?: string;
  remark?: string;
}

export interface FileStorageSavePayload {
  tenantId: number;
  storageCode: string;
  storageName: string;
  storageType: string;
  endpoint?: string;
  bucketDefault?: string;
  regionName?: string;
  accessKey?: string;
  secretKey?: string;
  basePath?: string;
  defaultFolder?: string;
  pathPattern?: string;
  maxFileMb?: number;
  allowedExts?: string;
  publicBaseUrl?: string;
  status: string;
  isDefault?: boolean;
  uploadMode?: string;
  remark?: string;
}

export interface FileStorageTestStep {
  step: string;
  status: string;
  latency: string;
  detail: string;
}

export interface FileStorageTestResult {
  storageId: number;
  storageCode: string;
  storageName: string;
  storageType: string;
  reachable: boolean;
  summary: string;
  steps: FileStorageTestStep[];
}

export interface FileUploadRouteRow {
  id: number;
  tenantId: number;
  storageId?: number | null;
  bizType: string;
  routeName: string;
  uploadMode?: string | null;
  targetDir: string;
  pathPattern?: string | null;
  maxFileMb?: number | null;
  allowedExts?: string | null;
  priority: number;
  status: string;
  remark?: string;
}

export interface FileUploadRouteSavePayload {
  tenantId?: number;
  storageId?: number | null;
  bizType: string;
  routeName: string;
  uploadMode?: string | null;
  targetDir: string;
  pathPattern?: string | null;
  maxFileMb?: number | null;
  allowedExts?: string | null;
  priority?: number;
  status?: string;
  remark?: string;
}

export interface RawUploadResult {
  fileId: number;
  storageId: number;
  storageCode: string;
  storageType: string;
  uploadMode: string;
  targetDir: string;
  objectKey: string;
  url: string;
  fileName: string;
  fileSize: number;
  contentType: string;
}

export interface FileObjectRow {
  id: number;
  tenantId: number;
  bucketId: number;
  storageId?: number | null;
  objectKey: string;
  fileName: string;
  fileExt?: string;
  contentType?: string;
  fileSize: number;
  visibility: string;
  bizType?: string;
  versionNo: number;
  status: string;
  storagePath: string;
  uploadMode?: string;
  targetDir?: string;
  publicUrl?: string;
  remark?: string;
}

export interface FileUploadSessionRow {
  id: number;
  tenantId: number;
  bucketId: number;
  storageId: number;
  sessionCode: string;
  objectKey: string;
  fileName: string;
  fileSize: number;
  uploadMode: string;
  partCount: number;
  ownerUserId: number;
  status: string;
  expireAt?: string;
  completedAt?: string;
}

export interface FileUploadSessionCompletePayload {
  tenantId: number;
  contentType: string;
  visibility: string;
  bizType?: string;
  remark?: string;
}

export interface FileUploadSessionCreatePayload {
  tenantId: number;
  bucketId?: number;
  storageId?: number;
  objectKey?: string;
  fileName: string;
  fileSize: number;
  uploadMode?: string;
  partCount: number;
  ownerUserId: number;
  bizType?: string;
}

export interface FileLifecyclePolicyRow {
  id: number;
  tenantId: number;
  policyCode: string;
  policyName: string;
  fileScope: string;
  retentionDays: number;
  archiveAfterDays: number;
  deleteAfterDays: number;
  deduplicateEnabled: boolean;
  versionRetentionCount: number;
  status: string;
  remark?: string;
}

export interface FileLifecyclePolicySavePayload {
  tenantId: number;
  policyCode: string;
  policyName: string;
  fileScope: string;
  retentionDays: number;
  archiveAfterDays: number;
  deleteAfterDays: number;
  deduplicateEnabled: boolean;
  versionRetentionCount: number;
  status: string;
  remark?: string;
}

export interface FileAccessLogRow {
  id: number;
  tenantId: number;
  fileId: number;
  accessType: string;
  operatorUserId: number;
  operatorIp?: string;
  success: boolean;
  occurredAt?: string;
  remark?: string;
}

export interface FileAccessLogCreatePayload {
  tenantId: number;
  fileId: number;
  accessType: string;
  operatorUserId: number;
  operatorIp?: string;
  success: boolean;
  remark?: string;
}
