import { http } from "@/api/http";

export interface ErpProductRow {
  id: number;
  productCode: string;
  productName: string;
  category: string;
  specification: string;
  unit: string;
  purchasePrice: number;
  salePrice: number;
  minStock: number;
  status: string;
  remark?: string;
  createdAt?: string;
}

export interface ErpInventoryRow {
  id: number;
  productId: number;
  productCode: string;
  productName: string;
  specification: string;
  unit: string;
  warehouseId: number;
  warehouseName: string;
  stockQuantity: number;
  lockedQuantity: number;
  availableQuantity: number;
  minStock: number;
  isLowStock: boolean;
  totalValue: number;
  updatedAt?: string;
}

export interface ErpInventoryRecordRow {
  id: number;
  recordNo: string;
  recordType: "PURCHASE_IN" | "SALE_OUT" | "ADJUST" | string;
  productId: number;
  productName: string;
  warehouseId: number;
  warehouseName: string;
  changeQuantity: number;
  beforeQuantity: number;
  afterQuantity: number;
  operatorName: string;
  remark?: string;
  createdAt?: string;
}

export interface ErpPurchaseOrderRow {
  id: number;
  orderNo: string;
  supplierId: number;
  warehouseId: number | null;
  warehouseName: string | null;
  items: ErpPurchaseLine[];
  supplierName: string;
  totalAmount: number;
  status: "PENDING" | "IN_STORAGE" | "CANCELLED" | string;
  remark?: string;
  createdAt?: string;
}

export interface ErpPurchaseLine {
  productId: number;
  productName: string;
  quantity: number;
  unitPrice: number;
  subtotal: number;
}

export interface ErpPurchaseRequest {
  supplierId: number;
  warehouseId: number;
  remark: string;
  items: Pick<ErpPurchaseLine, "productId" | "quantity" | "unitPrice">[];
}

export interface ErpMasterOption {
  id: number;
  code: string;
  name: string;
}

export type ErpMasterType = "suppliers" | "warehouses";

export async function fetchErpOptions(type: ErpMasterType): Promise<ErpMasterOption[]> {
  const res = await http.get<{ data: ErpMasterOption[] }>(`/erp/${type}`);
  return res.data.data;
}

export async function createErpOption(type: ErpMasterType, code: string, name: string): Promise<ErpMasterOption> {
  const res = await http.post<{ data: ErpMasterOption }>(`/erp/${type}`, { code, name });
  return res.data.data;
}

export async function fetchErpProducts(): Promise<ErpProductRow[]> {
  const res = await http.get<{ data: ErpProductRow[] }>("/erp/products");
  return res.data.data;
}

export async function saveErpProduct(payload: Partial<ErpProductRow>): Promise<ErpProductRow> {
  const res = await http.post<{ data: ErpProductRow }>("/erp/products", payload);
  return res.data.data;
}

export async function deleteErpProduct(id: number): Promise<void> {
  await http.delete(`/erp/products/${id}`);
}

export async function fetchErpInventory(): Promise<ErpInventoryRow[]> {
  const res = await http.get<{ data: ErpInventoryRow[] }>("/erp/inventory");
  return res.data.data;
}

export async function fetchErpInventoryRecords(): Promise<ErpInventoryRecordRow[]> {
  const res = await http.get<{ data: ErpInventoryRecordRow[] }>("/erp/inventory/records");
  return res.data.data;
}

export async function fetchErpPurchaseOrders(): Promise<ErpPurchaseOrderRow[]> {
  const res = await http.get<{ data: ErpPurchaseOrderRow[] }>("/erp/purchase-orders");
  return res.data.data;
}

export async function createErpPurchaseOrder(payload: ErpPurchaseRequest): Promise<ErpPurchaseOrderRow> {
  const res = await http.post<{ data: ErpPurchaseOrderRow }>("/erp/purchase-orders", payload);
  return res.data.data;
}

export async function confirmLegacyPurchaseOrder(id: number, payload: ErpPurchaseRequest): Promise<void> {
  await http.put(`/erp/purchase-orders/${id}/confirmation`, payload);
}

export async function stockInPurchaseOrder(id: number): Promise<void> {
  await http.post(`/erp/purchase-orders/${id}/stock-in`);
}
