import { http } from "@/api/http";
import type { ApiResponse } from "@/types/http";
import type {
  SchedulerJobAlarmRow,
  SchedulerJobAlarmSavePayload,
  SchedulerJobLogRow,
  SchedulerJobRow,
  SchedulerJobSavePayload,
  SchedulerJobStatusPayload,
  SchedulerJobRetryPayload,
  SchedulerJobTriggerPayload
} from "./types";

export async function fetchSchedulerJobs(): Promise<SchedulerJobRow[]> {
  const response = await http.get<ApiResponse<SchedulerJobRow[]>>("/scheduler/jobs");
  return response.data.data;
}

export async function createSchedulerJob(payload: SchedulerJobSavePayload): Promise<SchedulerJobRow> {
  const response = await http.post<ApiResponse<SchedulerJobRow>>("/scheduler/jobs", payload);
  return response.data.data;
}

export async function updateSchedulerJob(id: number, payload: SchedulerJobSavePayload): Promise<SchedulerJobRow> {
  const response = await http.put<ApiResponse<SchedulerJobRow>>(`/scheduler/jobs/${id}`, payload);
  return response.data.data;
}

export async function fetchSchedulerLogs(): Promise<SchedulerJobLogRow[]> {
  const response = await http.get<ApiResponse<SchedulerJobLogRow[]>>("/scheduler/logs");
  return response.data.data;
}

export async function triggerSchedulerJob(id: number, payload: SchedulerJobTriggerPayload): Promise<SchedulerJobLogRow> {
  const response = await http.post<ApiResponse<SchedulerJobLogRow>>(`/scheduler/jobs/${id}/trigger`, payload);
  return response.data.data;
}

export async function retrySchedulerJob(id: number, payload: SchedulerJobRetryPayload): Promise<SchedulerJobLogRow> {
  const response = await http.post<ApiResponse<SchedulerJobLogRow>>(`/scheduler/jobs/${id}/retry`, payload);
  return response.data.data;
}

export async function updateSchedulerJobStatus(id: number, payload: SchedulerJobStatusPayload): Promise<SchedulerJobRow> {
  const response = await http.patch<ApiResponse<SchedulerJobRow>>(`/scheduler/jobs/${id}/status`, payload);
  return response.data.data;
}

export async function fetchSchedulerAlarms(): Promise<SchedulerJobAlarmRow[]> {
  const response = await http.get<ApiResponse<SchedulerJobAlarmRow[]>>("/scheduler/alarms");
  return response.data.data;
}

export async function createSchedulerAlarm(payload: SchedulerJobAlarmSavePayload): Promise<SchedulerJobAlarmRow> {
  const response = await http.post<ApiResponse<SchedulerJobAlarmRow>>("/scheduler/alarms", payload);
  return response.data.data;
}

export async function updateSchedulerAlarm(id: number, payload: SchedulerJobAlarmSavePayload): Promise<SchedulerJobAlarmRow> {
  const response = await http.put<ApiResponse<SchedulerJobAlarmRow>>(`/scheduler/alarms/${id}`, payload);
  return response.data.data;
}
