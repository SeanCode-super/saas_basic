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
  exportFormat: "XLSX" | "CSV" | "PDF" | "HTML_REPORT";
  selectedFields?: string[];
  filters?: Record<string, unknown>;
  adHocColumns?: string[];
  adHocColumnLabels?: Record<string, string>;
  adHocRows?: Array<Record<string, unknown>>;
  insights?: string[];
  includeWatermark?: boolean;
  desensitizeEnabled?: boolean;
  async?: boolean;
  requireApproval?: boolean;
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
  status: "PENDING" | "PENDING_APPROVAL" | "PROCESSING" | "COMPLETED" | "REJECTED" | "FAILED" | string;
  approvalStatus?: "NOT_REQUIRED" | "PENDING" | "APPROVED" | "REJECTED" | string;
  approvedByName?: string;
  approvedAt?: string;
  approveReason?: string;
  errorMessage?: string;
  expireAt?: string;
  operatorName: string;
  createdAt?: string;
}

export interface ExportStatsSummary {
  totalExports: number;
  completedExports: number;
  pendingApprovals: number;
  totalRowsExported: number;
  totalFileBytes: number;
  desensitizedExports: number;
  activeTemplates: number;
  activeSchedules: number;
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

export interface ReportScheduleRow {
  id: number;
  scheduleCode: string;
  scheduleName: string;
  sourceCode: string;
  exportFormat: string;
  fields: string[];
  frequency: "DAILY" | "WEEKLY" | "MONTHLY" | string;
  cronExpr: string;
  runHour: number;
  runMinute: number;
  includeWatermark: boolean;
  desensitizeEnabled: boolean;
  recipientUserIds: string;
  lastRunAt?: string;
  nextRunAt?: string;
  lastTaskId?: number;
  status: string;
  remark?: string;
}

export interface ReportScheduleSavePayload {
  id?: number;
  scheduleName: string;
  sourceCode: string;
  exportFormat: string;
  fields?: string[];
  frequency: "DAILY" | "WEEKLY" | "MONTHLY" | string;
  runHour?: number;
  runMinute?: number;
  includeWatermark?: boolean;
  desensitizeEnabled?: boolean;
  recipientUserIds?: string;
  status?: string;
  remark?: string;
}
