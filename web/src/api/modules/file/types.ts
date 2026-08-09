export interface FileCapability {
  providers: string[];
  directUploadEnabled: boolean;
  chunkUploadEnabled: boolean;
  auditEnabled: boolean;
}

export interface FileStorageRow {
  id: number;
  tenantId: number;
  storageCode: string;
  storageName: string;
  storageType: string;
  endpoint?: string;
  bucketDefault?: string;
  publicBaseUrl?: string;
  status: string;
  remark?: string;
}

export interface FileStorageSavePayload {
  tenantId: number;
  storageCode: string;
  storageName: string;
  storageType: string;
  endpoint?: string;
  bucketDefault?: string;
  publicBaseUrl?: string;
  status: string;
  remark?: string;
}

export interface FileObjectRow {
  id: number;
  tenantId: number;
  bucketId: number;
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
  bucketId: number;
  storageId: number;
  objectKey: string;
  fileName: string;
  fileSize: number;
  uploadMode: string;
  partCount: number;
  ownerUserId: number;
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
