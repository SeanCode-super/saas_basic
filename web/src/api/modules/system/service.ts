import { http } from "@/api/http";
import type { ApiResponse } from "@/types/http";
import type {
  SystemConfigRow,
  SystemConfigSavePayload,
  SystemDictItemRow,
  SystemDictItemSavePayload,
  SystemDictOption,
  SystemDictStatusPayload,
  SystemDictTypeRow,
  SystemDictTypeSavePayload
} from "./types";

export async function fetchSystemConfigs(): Promise<SystemConfigRow[]> {
  const response = await http.get<ApiResponse<SystemConfigRow[]>>("/system/configs");
  return response.data.data;
}

export async function createSystemConfig(payload: SystemConfigSavePayload): Promise<SystemConfigRow> {
  const response = await http.post<ApiResponse<SystemConfigRow>>("/system/configs", payload);
  return response.data.data;
}

export async function fetchSystemDictTypes(): Promise<SystemDictTypeRow[]> {
  const response = await http.get<ApiResponse<SystemDictTypeRow[]>>("/system/dicts/types");
  return response.data.data;
}

export async function createSystemDictType(payload: SystemDictTypeSavePayload): Promise<SystemDictTypeRow> {
  const response = await http.post<ApiResponse<SystemDictTypeRow>>("/system/dicts/types", payload);
  return response.data.data;
}

export async function updateSystemDictType(id: number, payload: SystemDictTypeSavePayload): Promise<SystemDictTypeRow> {
  const response = await http.put<ApiResponse<SystemDictTypeRow>>(`/system/dicts/types/${id}`, payload);
  return response.data.data;
}

export async function updateSystemDictTypeStatus(id: number, payload: SystemDictStatusPayload): Promise<SystemDictTypeRow> {
  const response = await http.patch<ApiResponse<SystemDictTypeRow>>(`/system/dicts/types/${id}/status`, payload);
  return response.data.data;
}

export async function fetchSystemDictItems(dictTypeId: number): Promise<SystemDictItemRow[]> {
  const response = await http.get<ApiResponse<SystemDictItemRow[]>>(`/system/dicts/types/${dictTypeId}/items`);
  return response.data.data;
}

export async function createSystemDictItem(dictTypeId: number, payload: SystemDictItemSavePayload): Promise<SystemDictItemRow> {
  const response = await http.post<ApiResponse<SystemDictItemRow>>(`/system/dicts/types/${dictTypeId}/items`, payload);
  return response.data.data;
}

export async function updateSystemDictItem(id: number, payload: SystemDictItemSavePayload): Promise<SystemDictItemRow> {
  const response = await http.put<ApiResponse<SystemDictItemRow>>(`/system/dicts/items/${id}`, payload);
  return response.data.data;
}

export async function updateSystemDictItemStatus(id: number, payload: SystemDictStatusPayload): Promise<SystemDictItemRow> {
  const response = await http.patch<ApiResponse<SystemDictItemRow>>(`/system/dicts/items/${id}/status`, payload);
  return response.data.data;
}

export async function fetchSystemDictOptions(dictCode: string): Promise<SystemDictOption[]> {
  const response = await http.get<ApiResponse<SystemDictOption[]>>(`/system/dicts/options/${dictCode}`);
  return response.data.data;
}
