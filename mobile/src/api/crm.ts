import { request } from "../utils/request";

export function apiGetCrmStatistics() {
  return request<any>({
    url: "/api/crm/statistics",
    method: "GET"
  });
}

export function apiGetCustomers(params?: { inPool?: boolean; search?: string; status?: string }) {
  return request<any[]>({
    url: "/api/crm/customers",
    method: "GET",
    params,
    silent: true
  });
}

export function apiCreateCustomer(payload: any) {
  return request<any>({
    url: "/api/crm/customers",
    method: "POST",
    data: payload
  });
}

export function apiClaimCustomer(id: number) {
  return request<void>({
    url: `/api/crm/customers/${id}/claim`,
    method: "POST"
  });
}

export function apiReturnCustomerToPool(id: number, reason: string = "移动端退回公海") {
  return request<void>({
    url: `/api/crm/customers/${id}/return-to-pool`,
    method: "POST",
    data: { reason }
  });
}

export function apiGetClues(params?: any) {
  return request<any[]>({
    url: "/api/crm/clues",
    method: "GET",
    params
  });
}

export function apiCreateClue(payload: any) {
  return request<any>({
    url: "/api/crm/clues",
    method: "POST",
    data: payload
  });
}

export function apiGetFollowUpRecords(params?: { customerId?: number; clueId?: number }) {
  return request<any[]>({
    url: "/api/crm/follow-up-records",
    method: "GET",
    params
  });
}

export function apiCreateFollowUpRecord(payload: {
  bizType: "CUSTOMER" | "CLUE";
  bizId: number;
  contactType: "PHONE" | "VISIT" | "WECHAT" | "MAIL";
  content: string;
  nextFollowTime?: string;
}) {
  return request<any>({
    url: "/api/crm/follow-up-records",
    method: "POST",
    data: payload
  });
}
