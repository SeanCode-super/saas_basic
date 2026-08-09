export interface TenantRow {
  id: number;
  tenantCode: string;
  tenantName: string;
  packageId?: number;
  packageName: string;
  status: string;
  isolationMode: string;
  remark?: string;
}

export interface TenantSavePayload {
  tenantCode: string;
  tenantName: string;
  packageId: number;
  status: string;
  isolationMode: string;
  remark?: string;
}
