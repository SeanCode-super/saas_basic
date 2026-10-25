import { http } from "@/api/http";
import type { ApiResponse } from "@/types/http";

export interface FormFieldConfig {
  fieldCode: string;
  fieldName: string;
  fieldType: "TEXT" | "TEXTAREA" | "NUMBER" | "SELECT" | "DATE";
  required?: boolean;
  placeholder?: string;
  options?: string[];
  defaultValue?: unknown;
}

export interface ConditionRule {
  field: string;
  operator: ">" | ">=" | "<" | "<=" | "==" | "!=" | "contains";
  value: unknown;
}

export interface ConditionBranch {
  name: string;
  condition?: ConditionRule;
  next?: FlowNodeConfig;
}

export interface FlowNodeConfig {
  id: string;
  type: "START" | "APPROVER" | "CONDITION_BRANCH" | "CC" | "END";
  name: string;
  assigneeType?: "DEPT_LEADER" | "ROLE" | "USER";
  assigneeValue?: string;
  assigneeTitle?: string;
  approvalMode?: "ANY" | "ALL";
  branches?: ConditionBranch[];
  next?: FlowNodeConfig;
}

export interface ProcessDefinitionRow {
  id: number;
  tenantId: number;
  processCode: string;
  processName: string;
  category: string;
  icon: string;
  color: string;
  description?: string;
  formSchemaJson?: string;
  flowNodesJson?: string;
  version: number;
  status: "DRAFT" | "PUBLISHED" | "DISABLED";
  sortNo: number;
  createdAt: string;
  updatedAt: string;
}

export interface ProcessInstanceRow {
  id: number;
  tenantId: number;
  instanceNo: string;
  definitionId: number;
  processCode: string;
  processName: string;
  title: string;
  category: string;
  initiatorId: number;
  initiatorName: string;
  initiatorDeptId?: number;
  initiatorDeptName?: string;
  currentNodeId?: string;
  currentNodeName?: string;
  status: "RUNNING" | "APPROVED" | "REJECTED" | "CANCELLED";
  startTime: string;
  endTime?: string;
  createdAt: string;
}

export interface WorkflowTaskRow {
  id: number;
  tenantId: number;
  instanceId: number;
  instanceNo: string;
  processCode: string;
  processName: string;
  instanceTitle: string;
  category: string;
  initiatorId: number;
  initiatorName: string;
  nodeId: string;
  nodeName: string;
  nodeType: "APPROVER" | "CC";
  assigneeId: number;
  assigneeName: string;
  status: "PENDING" | "COMPLETED" | "REJECTED" | "TRANSFERRED" | "CANCELLED" | "ROLLBACK";
  action?: "AGREE" | "REJECT" | "TRANSFER" | "READ";
  comment?: string;
  claimTime?: string;
  finishTime?: string;
  createdAt: string;
}

export interface TaskLogItem {
  id: number;
  tenantId: number;
  instanceId: number;
  taskId?: number;
  nodeName: string;
  operatorId: number;
  operatorName: string;
  action: "SUBMIT" | "AGREE" | "REJECT" | "TRANSFER" | "CANCEL" | "READ" | "END" | "ADD_SIGN" | "ROLLBACK_PREVIOUS" | "ROLLBACK_INITIATOR";
  actionLabel: string;
  comment?: string;
  createdAt: string;
}

export interface ProcessInstanceDetail {
  instance: ProcessInstanceRow;
  definition?: ProcessDefinitionRow;
  formDataJson?: string;
  tasks: WorkflowTaskRow[];
  logs: TaskLogItem[];
}

export interface ProcessDefinitionSavePayload {
  processCode: string;
  processName: string;
  category?: string;
  icon?: string;
  color?: string;
  description?: string;
  formSchemaJson?: string;
  flowNodesJson?: string;
  status?: string;
  sortNo?: number;
}

export interface ProcessStartPayload {
  definitionId: number;
  title: string;
  formDataJson: string;
}

export interface TaskHandlePayload {
  action: "AGREE" | "REJECT" | "TRANSFER" | "READ" | "ADD_SIGN" | "ROLLBACK_PREVIOUS" | "ROLLBACK_INITIATOR" | "ROLLBACK_TARGET" | "DELEGATE" | "DELEGATE_RESOLVE" | "URGE";
  comment?: string;
  transferToUserId?: number;
  transferToUserName?: string;
  addSignUserId?: number;
  addSignUserName?: string;
  addSignType?: "BEFORE" | "AFTER";
  delegateUserId?: number;
  delegateUserName?: string;
  targetNodeId?: string;
  targetNodeName?: string;
}

export interface RollbackTargetNode {
  nodeId: string;
  nodeName: string;
  assigneeId?: number;
  assigneeName?: string;
  time?: string;
}

// ----------------------------------------------------------------------------
// API 调用
// ----------------------------------------------------------------------------

export async function fetchWorkflowDefinitions(params?: { category?: string; keyword?: string; status?: string }): Promise<ProcessDefinitionRow[]> {
  const res = await http.get<ApiResponse<ProcessDefinitionRow[]>>("/workflow/definitions", { params });
  return res.data.data;
}

export async function getWorkflowDefinition(id: number): Promise<ProcessDefinitionRow> {
  const res = await http.get<ApiResponse<ProcessDefinitionRow>>(`/workflow/definitions/${id}`);
  return res.data.data;
}

export async function createWorkflowDefinition(data: ProcessDefinitionSavePayload): Promise<ProcessDefinitionRow> {
  const res = await http.post<ApiResponse<ProcessDefinitionRow>>("/workflow/definitions", data);
  return res.data.data;
}

export async function updateWorkflowDefinition(id: number, data: ProcessDefinitionSavePayload): Promise<ProcessDefinitionRow> {
  const res = await http.put<ApiResponse<ProcessDefinitionRow>>(`/workflow/definitions/${id}`, data);
  return res.data.data;
}

export async function toggleWorkflowDefinitionStatus(id: number): Promise<ProcessDefinitionRow> {
  const res = await http.patch<ApiResponse<ProcessDefinitionRow>>(`/workflow/definitions/${id}/status`);
  return res.data.data;
}

export async function deleteWorkflowDefinition(id: number): Promise<void> {
  await http.delete<ApiResponse<void>>(`/workflow/definitions/${id}`);
}

export async function startWorkflowInstance(data: ProcessStartPayload): Promise<ProcessInstanceRow> {
  const res = await http.post<ApiResponse<ProcessInstanceRow>>("/workflow/instances/start", data);
  return res.data.data;
}

export async function fetchWorkflowInstances(params?: { category?: string; status?: string; keyword?: string; onlyMine?: boolean }): Promise<ProcessInstanceRow[]> {
  const res = await http.get<ApiResponse<ProcessInstanceRow[]>>("/workflow/instances", { params });
  return res.data.data;
}

export async function getWorkflowInstanceDetail(id: number): Promise<ProcessInstanceDetail> {
  const res = await http.get<ApiResponse<ProcessInstanceDetail>>(`/workflow/instances/${id}`);
  return res.data.data;
}

export async function cancelWorkflowInstance(id: number, reason?: string): Promise<void> {
  await http.post<ApiResponse<void>>(`/workflow/instances/${id}/cancel`, null, { params: { reason } });
}

export async function fetchMyPendingTasks(): Promise<WorkflowTaskRow[]> {
  const res = await http.get<ApiResponse<WorkflowTaskRow[]>>("/workflow/tasks/pending");
  return res.data.data;
}

export async function fetchMyCompletedTasks(): Promise<WorkflowTaskRow[]> {
  const res = await http.get<ApiResponse<WorkflowTaskRow[]>>("/workflow/tasks/completed");
  return res.data.data;
}

export async function fetchCcTasks(): Promise<WorkflowTaskRow[]> {
  const res = await http.get<ApiResponse<WorkflowTaskRow[]>>("/workflow/tasks/cc");
  return res.data.data;
}

export async function handleWorkflowTask(taskId: number, data: TaskHandlePayload): Promise<void> {
  await http.post<ApiResponse<void>>(`/workflow/tasks/${taskId}/handle`, data);
}

export async function urgeWorkflowInstance(id: number, comment?: string): Promise<void> {
  await http.post<ApiResponse<void>>(`/workflow/instances/${id}/urge`, null, { params: { comment } });
}

export async function fetchRollbackNodes(id: number): Promise<RollbackTargetNode[]> {
  const res = await http.get<ApiResponse<RollbackTargetNode[]>>(`/workflow/instances/${id}/rollback-nodes`);
  return res.data.data;
}

export async function checkWorkflowHealth(): Promise<{ status: string; standaloneUrl?: string }> {
  const res = await http.get<ApiResponse<{ status: string; standaloneUrl?: string }>>("/workflow/health");
  return res.data.data;
}

