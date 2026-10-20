export interface CodegenProjectRow {
  id: number;
  tenantId: number;
  code: string;
  name: string;
  basePackage: string;
  modulePrefix: string;
  outputMode: string;
  status: string;
  remark?: string;
  createdAt?: string;
}

export interface CodegenProjectSavePayload {
  tenantId: number;
  code: string;
  name: string;
  basePackage: string;
  modulePrefix: string;
  outputMode: string;
  status: string;
  remark?: string;
}

export interface DbTableItem {
  tableName: string;
  tableComment: string;
  createTime: string;
}

export interface CodegenColumnModel {
  columnName: string;
  fieldName: string;
  columnType: string;
  javaType: string;
  comment: string;
  isPk: boolean;
  isRequired: boolean;
  isQuery: boolean;
  queryType: "EQ" | "LIKE" | "BETWEEN" | "IN" | string;
  componentType: "input" | "select" | "date" | "textarea" | "switch" | "number" | string;
  dictCode?: string;
}

export interface CodegenTableModel {
  tableName: string;
  tableComment: string;
  className: string;
  businessName: string;
  functionName: string;
  moduleName: string;
  basePackage?: string;
  columns: CodegenColumnModel[];
  templateType?: "SINGLE" | "MASTER_SLAVE" | "TREE";
  subTableName?: string;
  subTableComment?: string;
  subClassName?: string;
  subTableFk?: string;
  subColumns?: CodegenColumnModel[];
  treeId?: string;
  treeParentId?: string;
  treeName?: string;
}

export interface CodegenPreviewFile {
  path: string;
  name: string;
  layer: string;
  language: string;
  code: string;
}
