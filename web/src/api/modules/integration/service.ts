import { http } from "@/api/http";
import type { ApiResponse } from "@/types/http";
import type {
  DatasourceRow,
  DatasourceSavePayload,
  MessageChannelsData,
  SmsSendPayload,
  SmsSendResult,
  MailSendPayload,
  MailSendResult,
  MessageLogItem
} from "./types";

export async function fetchDatasources(): Promise<DatasourceRow[]> {
  const response = await http.get<ApiResponse<DatasourceRow[]>>("/integrations/datasources");
  return response.data.data;
}

export async function createDatasource(payload: DatasourceSavePayload): Promise<DatasourceRow> {
  const response = await http.post<ApiResponse<DatasourceRow>>("/integrations/datasources", payload);
  return response.data.data;
}

// ==================== 多渠道消息网关 (Message Gateway) ====================

export async function fetchMessageChannels(): Promise<MessageChannelsData> {
  const response = await http.get<ApiResponse<MessageChannelsData>>("/message/channels");
  return response.data.data;
}

export async function sendTestSms(payload: SmsSendPayload): Promise<SmsSendResult> {
  const response = await http.post<ApiResponse<SmsSendResult>>("/message/sms/send", payload);
  return response.data.data;
}

export async function sendTestMail(payload: MailSendPayload): Promise<MailSendResult> {
  const response = await http.post<ApiResponse<MailSendResult>>("/message/mail/send", payload);
  return response.data.data;
}

export async function fetchMessageLogs(): Promise<MessageLogItem[]> {
  const response = await http.get<ApiResponse<MessageLogItem[]>>("/message/logs");
  return response.data.data;
}

export async function clearMessageLogs(): Promise<boolean> {
  const response = await http.delete<ApiResponse<boolean>>("/message/logs");
  return response.data.data;
}
