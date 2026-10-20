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
