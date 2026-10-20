import { request } from "../utils/request";

export function apiGetPendingTasks() {
  return request<any[]>({
    url: "/api/workflow/tasks/pending",
    method: "GET"
  });
}

export function apiHandleTask(taskId: number, payload: { action: "AGREE" | "REJECT"; comment: string }) {
  return request<void>({
    url: `/api/workflow/tasks/${taskId}/handle`,
    method: "POST",
    data: payload
  });
}

export function apiGetMyInstances() {
  return request<any[]>({
    url: "/api/workflow/instances",
    method: "GET",
    params: { onlyMine: true }
  });
}
