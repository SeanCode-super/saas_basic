import { request } from "../utils/request";

export function apiGetPendingTasks() {
  return request<any[]>({
    url: "/api/workflow/tasks/pending",
    method: "GET",
    silent: true
  });
}

export function apiGetCompletedTasks() {
  return request<any[]>({
    url: "/api/workflow/tasks/completed",
    method: "GET",
    silent: true
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

export function apiStartWorkflow(payload: {
  defId: number | string;
  businessKey?: string;
  title: string;
  formData: Record<string, any>;
}) {
  return request<any>({
    url: "/api/workflow/instances/start",
    method: "POST",
    data: payload
  });
}

export function apiGetWorkflowDefinitions() {
  return request<any[]>({
    url: "/api/workflow/definitions",
    method: "GET"
  });
}
