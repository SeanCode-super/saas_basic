import { http } from "@/api/http";
import type { ApiResponse } from "@/types/http";
import type { NotifyCountResult, NotifyMessage } from "./types";

export async function fetchMyNotifications(params?: {
  category?: string;
  unreadOnly?: boolean;
}): Promise<NotifyMessage[]> {
  const response = await http.get<ApiResponse<NotifyMessage[]>>("/notify/my-messages", { params });
  return response.data.data;
}

export async function fetchUnreadCount(): Promise<number> {
  const response = await http.get<ApiResponse<NotifyCountResult>>("/notify/unread-count");
  return response.data.data.unreadCount;
}

export async function markNotificationRead(id: number): Promise<void> {
  await http.put<ApiResponse<void>>(`/notify/read/${id}`);
}

export async function markAllNotificationsRead(): Promise<void> {
  await http.put<ApiResponse<void>>("/notify/read-all");
}

export async function sendTestNotification(payload: {
  category: string;
  title: string;
  content: string;
  bizType?: string;
  bizId?: string;
}): Promise<NotifyMessage> {
  const response = await http.post<ApiResponse<NotifyMessage>>("/notify/send-test", payload);
  return response.data.data;
}
