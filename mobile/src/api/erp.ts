import { request } from "../utils/request";

export function apiGetInventory() {
  return request<any[]>({
    url: "/api/erp/inventory",
    method: "GET"
  });
}

export function apiGetProducts() {
  return request<any[]>({
    url: "/api/erp/products",
    method: "GET"
  });
}

export function apiAdjustStock(data: {
  productId: number | string;
  warehouseId?: number | string;
  type: string;
  delta: number;
  remark?: string;
}) {
  return request<void>({
    url: "/api/erp/inventory/adjust",
    method: "POST",
    data
  });
}
