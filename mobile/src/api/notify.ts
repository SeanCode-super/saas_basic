import { request } from "../utils/request";

export function apiGetMyNotifications(category?: string) {
  return request<any[]>({
    url: "/api/system/notify/my-messages",
    method: "GET",
    params: category ? { category } : {}
  });
}

export function apiGetUnreadNotificationCount() {
  return request<{ count: number }>({
    url: "/api/system/notify/unread-count",
    method: "GET"
  });
}

export function apiMarkNotificationRead(id: number) {
  return request<void>({
    url: `/api/system/notify/read/${id}`,
    method: "PUT"
  });
}
