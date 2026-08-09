export interface DatasourceRow {
  id: number;
  tenantId: number;
  code: string;
  name: string;
  type: string;
  usageType: string;
  host?: string;
  port?: number | null;
  databaseName?: string;
  username?: string;
  testStatus: string;
  status: string;
  remark?: string;
}

export interface DatasourceSavePayload {
  tenantId: number;
  code: string;
  name: string;
  type: string;
  usageType: string;
  host?: string;
  port?: number | null;
  databaseName?: string;
  username?: string;
  testStatus: string;
  status: string;
  remark?: string;
}
