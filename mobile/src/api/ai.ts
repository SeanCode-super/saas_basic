import { request } from "../utils/request";

export interface MobileNl2SqlResponse {
  question: string;
  tableNames: string[];
  sql: string;
  columns: string[];
  columnLabels: Record<string, string>;
  rows: Array<Record<string, any>>;
  rowCount: number;
  insights: string[];
  latencyMs: number;
}

export interface MobileRagResponse {
  question: string;
  answer: string;
  engine: string;
  latencyMs: number;
  citations: Array<{
    citationNo: number;
    docName: string;
    pageNo: number;
    excerpt: string;
  }>;
}

export interface MobileExportTask {
  id: number;
  taskName: string;
  exportFormat: string;
  traceCode: string;
  totalRows: number;
  status: string;
  createdAt?: string;
}

export function apiMobileNl2Sql(question: string) {
  return request<MobileNl2SqlResponse>({
    url: "/api/ai/nl2sql/query",
    method: "POST",
    data: { question, maxRows: 20 }
  });
}

export function apiMobileAskKnowledge(question: string) {
  return request<MobileRagResponse>({
    url: "/api/ai/knowledge/ask",
    method: "POST",
    data: { question, topK: 3 }
  });
}

export function apiMobileSubmitExport(payload: {
  taskName: string;
  sourceType?: string;
  sourceCode?: string;
  exportFormat: string;
  adHocColumns?: string[];
  adHocColumnLabels?: Record<string, string>;
  adHocRows?: Array<Record<string, any>>;
  insights?: string[];
}) {
  return request<MobileExportTask>({
    url: "/api/exports/async",
    method: "POST",
    data: {
      ...payload,
      includeWatermark: true,
      desensitizeEnabled: true,
      async: true
    }
  });
}

export function apiMobileListExportTasks() {
  return request<MobileExportTask[]>({
    url: "/api/exports/tasks",
    method: "GET",
    silent: true
  });
}
