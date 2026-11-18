import { request } from "../utils/request";

export function apiGetCrmStatistics() {
  return request<any>({
    url: "/api/crm/statistics",
    method: "GET"
  });
}

export function apiGetCustomers(params?: {
  inPool?: boolean;
  isPool?: number;
  search?: string;
  keyword?: string;
  level?: string;
  status?: string;
}) {
  const queryParams: Record<string, any> = {};
  if (params) {
    if (params.isPool !== undefined) {
      queryParams.isPool = params.isPool;
    } else if (params.inPool !== undefined) {
      queryParams.isPool = params.inPool ? 1 : 0;
    }
    if (params.keyword !== undefined) {
      queryParams.keyword = params.keyword;
    } else if (params.search !== undefined) {
      queryParams.keyword = params.search;
    }
    if (params.level) {
      queryParams.level = params.level;
    }
    if (params.status) {
      queryParams.status = params.status;
    }
  }
  return request<any[]>({
    url: "/api/crm/customers",
    method: "GET",
    params: queryParams,
    silent: true
  });
}

export function apiDeleteCustomer(id: number) {
  return request<boolean>({
    url: `/api/crm/customers/${id}`,
    method: "DELETE"
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

export function apiDeleteClue(id: number) {
  return request<boolean>({
    url: `/api/crm/clues/${id}`,
    method: "DELETE"
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
  followType?: string;
  content: string;
  nextFollowTime?: string;
}) {
  return request<any>({
    url: "/api/crm/follow-up-records",
    method: "POST",
    data: {
      ...payload,
      followType: payload.contactType || payload.followType
    }
  });
}
