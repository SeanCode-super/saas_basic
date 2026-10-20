import { http } from "@/api/http";
import type { ApiResponse } from "@/types/http";

export interface CrmStatistics {
  totalCustomerCount: number;
  poolCustomerCount: number;
  totalClueCount: number;
  totalBusinessAmount: number;
  contractWonAmount: number;
  receivableReceivedAmount: number;
  receivablePendingAmount: number;
}

export interface CrmClueRow {
  id: number;
  name: string;
  contactName: string;
  mobile: string;
  telephone?: string;
  email?: string;
  address?: string;
  source: string;
  status: "UNFOLLOWED" | "FOLLOWING" | "CONVERTED" | "CLOSED" | string;
  ownerUserId?: number;
  ownerUserName?: string;
  nextContactTime?: string;
  convertedCustomerId?: number;
  remark?: string;
  createdAt: string;
}

export interface CrmCustomerRow {
  id: number;
  customerCode: string;
  name: string;
  industry: string;
  level: "A" | "B" | "C" | "D" | string;
  source: string;
  mobile?: string;
  telephone?: string;
  email?: string;
  address?: string;
  dealStatus: number;
  isPool: number;
  lockStatus: number;
  ownerUserId?: number;
  ownerUserName?: string;
  lastContactTime?: string;
  nextContactTime?: string;
  remark?: string;
  createdAt: string;
}

export interface CrmBusinessRow {
  id: number;
  businessCode: string;
  name: string;
  customerId: number;
  customerName: string;
  totalPrice: number;
  dealDate?: string;
  stage: "INITIAL" | "PROPOSAL" | "NEGOTIATION" | "WON" | "LOST" | string;
  winRate: number;
  lossReason?: string;
  ownerUserId?: number;
  ownerUserName?: string;
  remark?: string;
  createdAt: string;
}

export interface CrmContractRow {
  id: number;
  contractNo: string;
  name: string;
  customerId: number;
  customerName: string;
  businessId?: number;
  businessName?: string;
  totalPrice: number;
  startTime?: string;
  endTime?: string;
  signerUserName?: string;
  status: "DRAFT" | "APPROVING" | "APPROVED" | "REJECTED" | string;
  remark?: string;
  createdAt: string;
}

export interface CrmReceivableRow {
  id: number;
  receivableNo: string;
  contractId: number;
  contractName: string;
  customerId: number;
  customerName: string;
  price: number;
  returnTime?: string;
  payType: "BANK_TRANSFER" | "ALIPAY" | "WECHAT" | "CASH" | string;
  status: "PENDING" | "APPROVED" | string;
  remark?: string;
  createdAt: string;
}

export interface CrmFollowUpRecordRow {
  id: number;
  bizType: string;
  bizId: number;
  followType: "CALL" | "VISIT" | "WECHAT" | "EMAIL" | "OTHER" | string;
  content: string;
  nextContactTime?: string;
  contactUserName: string;
  createdAt: string;
}

// ================= API 请求函数 =================
export async function getCrmStatistics(): Promise<ApiResponse<CrmStatistics>> {
  const res = await http.get<ApiResponse<CrmStatistics>>("/crm/statistics");
  return res.data;
}

export async function listCrmClues(params?: { status?: string; keyword?: string }): Promise<ApiResponse<CrmClueRow[]>> {
  const res = await http.get<ApiResponse<CrmClueRow[]>>("/crm/clues", { params });
  return res.data;
}

export async function createCrmClue(data: Partial<CrmClueRow>): Promise<ApiResponse<CrmClueRow>> {
  const res = await http.post<ApiResponse<CrmClueRow>>("/crm/clues", data);
  return res.data;
}

export async function convertClueToCustomer(id: number): Promise<ApiResponse<CrmCustomerRow>> {
  const res = await http.post<ApiResponse<CrmCustomerRow>>(`/crm/clues/${id}/convert`);
  return res.data;
}

export async function listCrmCustomers(params?: { isPool?: number; keyword?: string; level?: string }): Promise<ApiResponse<CrmCustomerRow[]>> {
  const res = await http.get<ApiResponse<CrmCustomerRow[]>>("/crm/customers", { params });
  return res.data;
}

export async function createCrmCustomer(data: Partial<CrmCustomerRow>): Promise<ApiResponse<CrmCustomerRow>> {
  const res = await http.post<ApiResponse<CrmCustomerRow>>("/crm/customers", data);
  return res.data;
}

export async function claimCrmCustomer(id: number): Promise<ApiResponse<boolean>> {
  const res = await http.post<ApiResponse<boolean>>(`/crm/customers/${id}/claim`);
  return res.data;
}

export async function returnCustomerToPool(id: number): Promise<ApiResponse<boolean>> {
  const res = await http.post<ApiResponse<boolean>>(`/crm/customers/${id}/return-to-pool`);
  return res.data;
}

export async function toggleCustomerLock(id: number): Promise<ApiResponse<boolean>> {
  const res = await http.post<ApiResponse<boolean>>(`/crm/customers/${id}/toggle-lock`);
  return res.data;
}

export async function listCrmBusinesses(params?: { stage?: string; keyword?: string }): Promise<ApiResponse<CrmBusinessRow[]>> {
  const res = await http.get<ApiResponse<CrmBusinessRow[]>>("/crm/businesses", { params });
  return res.data;
}

export async function createCrmBusiness(data: Partial<CrmBusinessRow>): Promise<ApiResponse<CrmBusinessRow>> {
  const res = await http.post<ApiResponse<CrmBusinessRow>>("/crm/businesses", data);
  return res.data;
}

export async function updateCrmBusinessStage(id: number, data: { stage: string; winRate?: number; lossReason?: string }): Promise<ApiResponse<boolean>> {
  const res = await http.put<ApiResponse<boolean>>(`/crm/businesses/${id}/stage`, data);
  return res.data;
}

export async function listCrmContracts(params?: { status?: string; keyword?: string }): Promise<ApiResponse<CrmContractRow[]>> {
  const res = await http.get<ApiResponse<CrmContractRow[]>>("/crm/contracts", { params });
  return res.data;
}

export async function createCrmContract(data: Partial<CrmContractRow>): Promise<ApiResponse<CrmContractRow>> {
  const res = await http.post<ApiResponse<CrmContractRow>>("/crm/contracts", data);
  return res.data;
}

export async function approveCrmContract(id: number, approve: boolean): Promise<ApiResponse<boolean>> {
  const res = await http.post<ApiResponse<boolean>>(`/crm/contracts/${id}/approve`, { approve });
  return res.data;
}

export async function listCrmReceivables(params?: { status?: string }): Promise<ApiResponse<CrmReceivableRow[]>> {
  const res = await http.get<ApiResponse<CrmReceivableRow[]>>("/crm/receivables", { params });
  return res.data;
}

export async function createCrmReceivable(data: Partial<CrmReceivableRow>): Promise<ApiResponse<CrmReceivableRow>> {
  const res = await http.post<ApiResponse<CrmReceivableRow>>("/crm/receivables", data);
  return res.data;
}

export async function approveCrmReceivable(id: number): Promise<ApiResponse<boolean>> {
  const res = await http.post<ApiResponse<boolean>>(`/crm/receivables/${id}/approve`);
  return res.data;
}

export async function listCrmFollowUpRecords(params: { bizType: string; bizId: number }): Promise<ApiResponse<CrmFollowUpRecordRow[]>> {
  const res = await http.get<ApiResponse<CrmFollowUpRecordRow[]>>("/crm/follow-up-records", { params });
  return res.data;
}

export async function addCrmFollowUpRecord(data: Partial<CrmFollowUpRecordRow>): Promise<ApiResponse<CrmFollowUpRecordRow>> {
  const res = await http.post<ApiResponse<CrmFollowUpRecordRow>>("/crm/follow-up-records", data);
  return res.data;
}

