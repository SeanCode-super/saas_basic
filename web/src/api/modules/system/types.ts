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
