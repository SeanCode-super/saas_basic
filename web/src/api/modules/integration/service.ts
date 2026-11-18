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
  BotSendPayload,
  BotSendResult,
  PayOrderCreatePayload,
  PayOrderRow,
  MessageLogItem,
  OpenAppRow,
  OpenAppSavePayload,
  WebhookDeliveryRow,
  WebhookSubscriptionRow,
  WebhookSubscriptionSavePayload
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

export async function sendTestBot(payload: BotSendPayload): Promise<BotSendResult> {
  const response = await http.post<ApiResponse<BotSendResult>>("/message/bot/send", payload);
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

// ==================== 开放平台应用 (Open API Platform) ====================

export async function fetchOpenApps(): Promise<OpenAppRow[]> {
  const response = await http.get<ApiResponse<OpenAppRow[]>>("/integration/open-apps");
  return response.data.data;
}

export async function saveOpenApp(payload: OpenAppSavePayload): Promise<OpenAppRow> {
  const response = await http.post<ApiResponse<OpenAppRow>>("/integration/open-apps", payload);
  return response.data.data;
}

export async function regenerateOpenAppSecret(id: number): Promise<OpenAppRow> {
  const response = await http.post<ApiResponse<OpenAppRow>>(`/integration/open-apps/${id}/regenerate-secret`);
  return response.data.data;
}

export async function deleteOpenApp(id: number): Promise<void> {
  await http.delete(`/integration/open-apps/${id}`);
}

// ==================== 统一支付中心 (Unified Pay Center) ====================

export async function fetchPayOrders(): Promise<PayOrderRow[]> {
  const response = await http.get<ApiResponse<PayOrderRow[]>>("/pay/orders");
  return response.data.data;
}

export async function createPayOrder(payload: PayOrderCreatePayload): Promise<PayOrderRow> {
  const response = await http.post<ApiResponse<PayOrderRow>>("/pay/create-order", payload);
  return response.data.data;
}

export async function getPayOrderStatus(orderNo: string): Promise<PayOrderRow> {
  const response = await http.get<ApiResponse<PayOrderRow>>(`/pay/order-status/${orderNo}`);
  return response.data.data;
}

export async function mockPaySuccess(orderNo: string): Promise<PayOrderRow> {
  const response = await http.post<ApiResponse<PayOrderRow>>(`/pay/mock-success/${orderNo}`);
  return response.data.data;
}

export async function refundPayOrder(orderNo: string): Promise<PayOrderRow> {
  const response = await http.post<ApiResponse<PayOrderRow>>(`/pay/refund/${orderNo}`);
  return response.data.data;
}

// ==================== CloudEvents 1.0 出站 Webhook 中心 ====================

export async function fetchWebhookSubscriptions(): Promise<WebhookSubscriptionRow[]> {
  const response = await http.get<ApiResponse<WebhookSubscriptionRow[]>>("/integration/webhooks/subscriptions");
  return response.data.data;
}

export async function createWebhookSubscription(payload: WebhookSubscriptionSavePayload): Promise<WebhookSubscriptionRow> {
  const response = await http.post<ApiResponse<WebhookSubscriptionRow>>("/integration/webhooks/subscriptions", payload);
  return response.data.data;
}

export async function deleteWebhookSubscription(id: number): Promise<void> {
  await http.delete(`/integration/webhooks/subscriptions/${id}`);
}

export async function fetchWebhookDeliveries(): Promise<WebhookDeliveryRow[]> {
  const response = await http.get<ApiResponse<WebhookDeliveryRow[]>>("/integration/webhooks/deliveries");
  return response.data.data;
}

export async function sendTestWebhook(id: number, eventType?: string): Promise<WebhookDeliveryRow> {
  const response = await http.post<ApiResponse<WebhookDeliveryRow>>(`/integration/webhooks/test/${id}`, null, {
    params: eventType ? { eventType } : {}
  });
  return response.data.data;
}

export async function replayWebhookDelivery(logId: number): Promise<WebhookDeliveryRow> {
  const response = await http.post<ApiResponse<WebhookDeliveryRow>>(`/integration/webhooks/deliveries/${logId}/replay`);
  return response.data.data;
}
