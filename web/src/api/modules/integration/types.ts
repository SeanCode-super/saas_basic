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
  type: "SMS" | "MAIL";
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
