export interface DatasourceRow {
  id: number;
  tenantId: number;
  code: string;
  name: string;
  type: string;
  usageType: string;
  host?: string;
  port?: number | null;
  databaseName?: string;
  username?: string;
  testStatus: string;
  status: string;
  remark?: string;
}

export interface DatasourceSavePayload {
  tenantId: number;
  code: string;
  name: string;
  type: string;
  usageType: string;
  host?: string;
  port?: number | null;
  databaseName?: string;
  username?: string;
  testStatus: string;
  status: string;
  remark?: string;
}

export interface MessageChannelItem {
  channelCode: string;
  channelName: string;
  status: string;
}

export interface MessageChannelsData {
  smsChannels: MessageChannelItem[];
  mailChannels: MessageChannelItem[];
  botChannels?: MessageChannelItem[];
}

export interface BotSendPayload {
  botType: "WECHAT_WORK" | "DINGTALK" | "FEISHU";
  webhookUrl: string;
  secret?: string;
  title: string;
  content: string;
  msgType?: string;
}

export interface BotSendResult {
  success: boolean;
  botType: string;
  messageId: string;
  message: string;
  durationMs: number;
  sendTime: string;
}

export interface SmsSendPayload {
  phone: string;
  channelCode: string;
  signName?: string;
  templateCode?: string;
  templateParams?: Record<string, any>;
}

export interface SmsSendResult {
  success: boolean;
  messageId: string;
  channelCode: string;
  phone: string;
  code: string;
  message: string;
  sentAt: string;
}

export interface MailSendPayload {
  to: string;
  subject: string;
  content: string;
  isHtml?: boolean;
  cc?: string;
}

export interface MailSendResult {
  success: boolean;
  messageId: string;
  to: string;
  subject: string;
  message: string;
  sentAt: string;
}

export interface MessageLogItem {
  id: string;
  type: "SMS" | "MAIL" | "BOT";
  target: string;
  channel: string;
  title: string;
  content: string;
  status: "SUCCESS" | "FAILED";
  messageId: string;
  responseMsg: string;
  costMillis: number;
  sentAt: string;
}

export interface PayOrderCreatePayload {
  orderNo?: string;
  amount: number;
  channel: "WECHAT" | "ALIPAY" | "MOCK_SANDBOX";
  subject?: string;
  description?: string;
}

export interface PayOrderRow {
  payTradeNo: string;
  orderNo: string;
  amount: number;
  channel: string;
  status: "PENDING" | "SUCCESS" | "FAILED" | "EXPIRED" | "NOT_FOUND";
  payUrl: string;
  qrCodeUrl: string;
  expireAt: string;
  paidAt?: string;
}

export interface OpenAppRow {
  id: number;
  tenantId: number;
  appKey: string;
  appSecret: string;
  appName: string;
  status: string;
  rateLimit: number;
  allowedPermissions?: string;
  remark?: string;
  createdAt: string;
}

export interface OpenAppSavePayload {
  id?: number;
  appName: string;
  status?: string;
  rateLimit?: number;
  allowedPermissions?: string;
  remark?: string;
}
