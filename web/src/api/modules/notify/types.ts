export type NotifyCategory = "WORKFLOW" | "AUDIT" | "SYSTEM" | "ALL";

export interface NotifyMessage {
  id: number;
  tenantId: number;
  userId: number;
  category: NotifyCategory;
  title: string;
  content: string;
  readStatus: 0 | 1;
  readAt?: string | null;
  bizType?: string | null;
  bizId?: string | null;
  extJson?: string | null;
  createdAt: string;
}

export interface NotifyCountResult {
  unreadCount: number;
}
