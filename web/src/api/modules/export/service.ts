import { http } from "@/api/http";
import type { ApiResponse } from "@/types/http";
import type {
  ExportCreatePayload,
  ExportDatasetInfo,
  ExportStatsSummary,
  ExportTaskRow,
  ExportTemplateRow,
  ExportTemplateSavePayload,
  ReportScheduleRow,
  ReportScheduleSavePayload
} from "./types";

function parseDownloadFilename(contentDisposition?: string, fallback = "export.xlsx"): string {
  if (!contentDisposition) return fallback;
  const utf8Match = /filename\*=utf-8''([^;]+)/i.exec(contentDisposition);
  if (utf8Match?.[1]) {
    try {
      return decodeURIComponent(utf8Match[1]);
    } catch {
      return utf8Match[1];
    }
  }
  const plainMatch = /filename="?([^";]+)"?/i.exec(contentDisposition);
  return plainMatch?.[1] || fallback;
}

export function triggerBlobDownload(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export async function fetchExportDatasets(): Promise<ExportDatasetInfo[]> {
  const res = await http.get<ApiResponse<ExportDatasetInfo[]>>("/exports/datasets");
  return res.data.data;
}

export async function executeSyncExport(payload: ExportCreatePayload): Promise<{
  fileName: string;
  traceCode: string;
  sha256: string;
}> {
  const ext =
    payload.exportFormat === "CSV"
      ? "csv"
      : payload.exportFormat === "PDF"
      ? "pdf"
      : payload.exportFormat === "HTML_REPORT"
      ? "html"
      : "xlsx";
  const res = await http.post<Blob>("/exports/sync", payload, {
    responseType: "blob",
    timeout: 60000
  });
  const headers = res.headers as Record<string, string | undefined>;
  const fileName = parseDownloadFilename(
    headers["content-disposition"],
    `${payload.taskName || payload.sourceCode || "export"}.${ext}`
  );
  triggerBlobDownload(res.data, fileName);
  return {
    fileName,
    traceCode: headers["x-export-trace-code"] || "",
    sha256: headers["x-export-sha256"] || ""
  };
}

export async function submitAsyncExport(payload: ExportCreatePayload): Promise<ExportTaskRow> {
  const res = await http.post<ApiResponse<ExportTaskRow>>("/exports/async", payload);
  return res.data.data;
}

export async function fetchExportTasks(): Promise<ExportTaskRow[]> {
  const res = await http.get<ApiResponse<ExportTaskRow[]>>("/exports/tasks");
  return res.data.data;
}

export async function fetchExportStats(): Promise<ExportStatsSummary> {
  const res = await http.get<ApiResponse<ExportStatsSummary>>("/exports/stats");
  return res.data.data;
}

export async function downloadExportTask(id: number | string, fallbackName = "export.xlsx"): Promise<void> {
  const res = await http.get<Blob>(`/exports/tasks/${id}/download`, {
    responseType: "blob",
    timeout: 60000
  });
  const headers = res.headers as Record<string, string | undefined>;
  const fileName = parseDownloadFilename(headers["content-disposition"], fallbackName);
  triggerBlobDownload(res.data, fileName);
}

export async function deleteExportTask(id: number | string): Promise<void> {
  await http.delete(`/exports/tasks/${id}`);
}

export async function fetchExportTemplates(sourceCode?: string): Promise<ExportTemplateRow[]> {
  const res = await http.get<ApiResponse<ExportTemplateRow[]>>("/exports/templates", {
    params: sourceCode ? { sourceCode } : undefined
  });
  return res.data.data;
}

export async function saveExportTemplate(payload: ExportTemplateSavePayload): Promise<ExportTemplateRow> {
  const res = await http.post<ApiResponse<ExportTemplateRow>>("/exports/templates", payload);
  return res.data.data;
}

export async function deleteExportTemplate(id: number): Promise<void> {
  await http.delete(`/exports/templates/${id}`);
}

export async function approveExportTask(id: number | string, reason?: string): Promise<ExportTaskRow> {
  const res = await http.post<ApiResponse<ExportTaskRow>>(`/exports/tasks/${id}/approve`, { reason });
  return res.data.data;
}

export async function rejectExportTask(id: number | string, reason?: string): Promise<ExportTaskRow> {
  const res = await http.post<ApiResponse<ExportTaskRow>>(`/exports/tasks/${id}/reject`, { reason });
  return res.data.data;
}

export async function fetchReportSchedules(): Promise<ReportScheduleRow[]> {
  const res = await http.get<ApiResponse<ReportScheduleRow[]>>("/exports/schedules");
  return res.data.data;
}

export async function saveReportSchedule(payload: ReportScheduleSavePayload): Promise<ReportScheduleRow> {
  const res = await http.post<ApiResponse<ReportScheduleRow>>("/exports/schedules", payload);
  return res.data.data;
}

export async function deleteReportSchedule(id: number): Promise<void> {
  await http.delete(`/exports/schedules/${id}`);
}

export async function triggerReportSchedule(id: number): Promise<ExportTaskRow> {
  const res = await http.post<ApiResponse<ExportTaskRow>>(`/exports/schedules/${id}/trigger`);
  return res.data.data;
}
