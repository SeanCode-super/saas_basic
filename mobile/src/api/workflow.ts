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
  definitionId?: number | string;
  defId?: number | string;
  businessKey?: string;
  title: string;
  formDataJson?: string;
  formData?: Record<string, any>;
}) {
  const definitionId = Number(payload.definitionId || payload.defId || 1);
  const formDataJson = payload.formDataJson || JSON.stringify(payload.formData || {});
  return request<any>({
    url: "/api/workflow/instances/start",
    method: "POST",
    data: {
      definitionId,
      title: payload.title,
      formDataJson
    }
  });
}

export function apiGetWorkflowDefinitions() {
  return request<any[]>({
    url: "/api/workflow/definitions",
    method: "GET"
  });
}
