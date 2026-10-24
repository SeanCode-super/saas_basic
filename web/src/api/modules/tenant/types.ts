export interface TenantRow {
  id: number;
  tenantCode: string;
  tenantName: string;
  packageId?: number;
  packageName: string;
  status: string;
  isolationMode: string;
  expireAt?: string;
  daysRemaining?: number | null;
  contactName?: string;
  contactMobile?: string;
  contactEmail?: string;
  remark?: string;
}

export interface TenantSavePayload {
  tenantCode: string;
  tenantName: string;
  packageId: number;
  status: string;
  isolationMode: string;
  expireAt?: string;
  contactName?: string;
  contactMobile?: string;
  contactEmail?: string;
  remark?: string;
}

export interface TenantPackageRow {
  id: number;
  packageCode: string;
  packageName: string;
  packageType: string;
  status: string;
  maxUserCount: number;
  maxDeptCount: number;
  maxStorageMb: number;
  maxApiPerMinute: number;
  featureFlagsJson?: string;
  sortNo: number;
  remark?: string;
  activeTenantCount?: number;
}

export interface TenantPackageSavePayload {
  packageCode: string;
  packageName: string;
  packageType?: string;
  status?: string;
  maxUserCount: number;
  maxDeptCount: number;
  maxStorageMb: number;
  maxApiPerMinute: number;
  featureFlagsJson?: string;
  sortNo?: number;
  remark?: string;
}

export interface TenantQuotaItem {
  quotaCode: string;
  quotaName: string;
  quotaValue: number;
  usedValue: number;
  usagePercent: number;
  status: "NORMAL" | "WARNING" | "DANGER";
}

export interface TenantQuotaOverview {
  tenantId: number;
  tenantCode: string;
  tenantName: string;
  packageId: number;
  packageName: string;
  expireAt: string;
  daysRemaining: number | null;
  lifecycleStatus: string;
  quotas: TenantQuotaItem[];
}

export interface TenantPlanUpdatePayload {
  packageId: number;
  expireAt?: string;
  remark?: string;
}

export interface TenantMeteringSummary {
  tenantId: number;
  tenantCode: string;
  tenantName: string;
  totalApiCalls: number;
  storageMb: number;
  activeUsers: number;
  estimatedCost: number;
  periodStart: string;
  periodEnd: string;
}

export interface TenantBillRow {
  id: number;
  billNo: string;
  tenantId: number;
  tenantCode: string;
  billingCycle: string;
  apiCallsCount: number;
  storageMb: number;
  amount: number;
  currency: string;
  status: string;
  paidAt?: string;
  createdAt: string;
}

export interface TenantSnapshotExportResult {
  snapshotId: string;
  tenantId: number;
  tenantCode: string;
  fileSizeBytes: number;
  downloadUrl: string;
  checksum: string;
  createdAt: string;
}

export interface TenantSnapshotRestoreResult {
  tenantId: number;
  tenantCode: string;
  snapshotId: string;
  restoreBatchNo: string;
  status: string;
  restoredCounts: Record<string, number>;
  restoredAt: string;
  message: string;
}

export interface TenantAppCatalogItem {
  appCode: string;
  appName: string;
  description: string;
  category: string;
  icon: string;
  requiredTier: string;
  status: "ENABLED" | "DISABLED" | "TRIAL" | "EXPIRED";
  openedAt?: string;
  expiredAt?: string;
  daysRemaining?: number | null;
  isTrial: boolean;
  isIncludedInPlan: boolean;
  canSubscribeTrial: boolean;
}

export interface TenantAppResponse {
  id: number;
  tenantId: number;
  appCode: string;
  appName: string;
  status: string;
  openedAt?: string;
  expiredAt?: string;
  configJson?: string;
  remark?: string;
}

export interface TenantAppSubscribePayload {
  appCode: string;
  trialDays?: number;
  remark?: string;
}
