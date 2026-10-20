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
