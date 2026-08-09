export interface SchedulerJobRow {
  id: number;
  tenantId: number;
  code: string;
  name: string;
  jobType: string;
  executorId: number;
  handlerName: string;
  status: string;
  remark?: string;
}

export interface SchedulerJobSavePayload {
  tenantId: number;
  code: string;
  name: string;
  jobType: string;
  executorId: number;
  handlerName: string;
  status: string;
  remark?: string;
}

export interface SchedulerJobLogRow {
  id: number;
  tenantId: number;
  jobId: number;
  executionNo: string;
  executorId: number;
  startedAt?: string;
  finishedAt?: string;
  runStatus: string;
  success: boolean;
  retryCount: number;
  errorMessage?: string;
  traceId?: string;
}

export interface SchedulerJobTriggerPayload {
  tenantId: number;
  remark?: string;
}

export interface SchedulerJobRetryPayload {
  tenantId: number;
  remark?: string;
}

export interface SchedulerJobStatusPayload {
  tenantId: number;
  status: string;
}

export interface SchedulerJobAlarmRow {
  id: number;
  tenantId: number;
  jobId: number;
  alarmCode: string;
  alarmName: string;
  channelType: string;
  triggerRule: string;
  receiverJson?: string;
  templateCode?: string;
  silenceMinutes: number;
  status: string;
  remark?: string;
}

export interface SchedulerJobAlarmSavePayload {
  tenantId: number;
  jobId: number;
  alarmCode: string;
  alarmName: string;
  channelType: string;
  triggerRule: string;
  receiverJson?: string;
  templateCode?: string;
  silenceMinutes: number;
  status: string;
  remark?: string;
}
