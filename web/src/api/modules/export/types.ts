export interface ExportFieldSpec {
  fieldCode: string;
  fieldLabel: string;
  sensitive: boolean;
  dataType: string;
}

export interface ExportDatasetInfo {
  sourceCode: string;
  sourceName: string;
  domain: string;
  fields: ExportFieldSpec[];
}

export interface ExportCreatePayload {
  taskName?: string;
  sourceType?: "DATASET" | "NL2SQL" | "AD_HOC" | "DYNAMIC_MODEL";
  sourceCode?: string;
  exportFormat: "XLSX" | "CSV" | "HTML_REPORT";
  selectedFields?: string[];
  filters?: Record<string, unknown>;
  adHocColumns?: string[];
  adHocColumnLabels?: Record<string, string>;
  adHocRows?: Array<Record<string, unknown>>;
  insights?: string[];
  includeWatermark?: boolean;
  desensitizeEnabled?: boolean;
  async?: boolean;
}

export interface ExportTaskRow {
  id: number | string;
  taskNo: string;
  taskName: string;
  sourceType: string;
  sourceCode: string;
  exportFormat: string;
  traceCode: string;
  watermarkText: string;
  desensitized: boolean;
  totalRows: number;
  processedRows: number;
  progressPercent: number;
  fileSize: number;
  fileSha256?: string;
  status: "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED" | string;
  errorMessage?: string;
  expireAt?: string;
  operatorName: string;
  createdAt?: string;
}

export interface ExportStatsSummary {
  totalExports: number;
  completedExports: number;
  totalRowsExported: number;
  totalFileBytes: number;
  desensitizedExports: number;
  activeTemplates: number;
}

export interface ExportTemplateRow {
  id: number;
  templateCode: string;
  templateName: string;
  sourceCode: string;
  fields: string[];
  defaultFormat: "XLSX" | "CSV" | "HTML_REPORT" | string;
  includeWatermark: boolean;
  desensitizeEnabled: boolean;
  remark?: string;
}

export interface ExportTemplateSavePayload {
  id?: number;
  templateCode?: string;
  templateName: string;
  sourceCode: string;
  fields: string[];
  defaultFormat: string;
  includeWatermark: boolean;
  desensitizeEnabled: boolean;
  remark?: string;
}
