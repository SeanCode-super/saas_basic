import { http } from "@/api/http";
import type { ApiResponse } from "@/types/http";
import type { TransportCryptoTrace } from "@/utils/transport-crypto";
import type {
  SystemConfigRow,
  SystemConfigSavePayload,
  SystemDictItemRow,
  SystemDictItemSavePayload,
  SystemDictOption,
  SystemDictStatusPayload,
  SystemDictTypeRow,
  SystemDictTypeSavePayload,
  RedisInfoData,
  RedisKeyItem,
  SlowSqlReportData,
  JvmInfoData,
  TransportSecurityOverview,
  TransportPolicyUpdatePayload,
  TransportKeyConfigurePayload,
  TransportRoundtripResult,
  ApiCatalogSnapshot
} from "./types";

export async function fetchSystemConfigs(): Promise<SystemConfigRow[]> {
  const response = await http.get<ApiResponse<SystemConfigRow[]>>("/system/configs");
  return response.data.data;
}

export async function createSystemConfig(payload: SystemConfigSavePayload): Promise<SystemConfigRow> {
  const response = await http.post<ApiResponse<SystemConfigRow>>("/system/configs", payload);
  return response.data.data;
}

export async function upsertSystemConfigByKey(payload: SystemConfigSavePayload): Promise<SystemConfigRow> {
  const response = await http.put<ApiResponse<SystemConfigRow>>("/system/configs/by-key", payload);
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

export async function deleteSystemDictType(id: number): Promise<void> {
  await http.delete<ApiResponse<void>>(`/system/dicts/types/${id}`);
}

export async function deleteSystemDictItem(id: number): Promise<void> {
  await http.delete<ApiResponse<void>>(`/system/dicts/items/${id}`);
}

export async function refreshSystemDictCache(): Promise<boolean> {
  const response = await http.post<ApiResponse<boolean>>("/system/dicts/refresh-cache");
  return response.data.data;
}

export async function fetchSystemDictOptions(dictCode: string): Promise<SystemDictOption[]> {
  const response = await http.get<ApiResponse<SystemDictOption[]>>(`/system/dicts/options/${dictCode}`);
  return response.data.data;
}

// ==================== 运维监控 (DevOps & Infra Monitor) ====================

export async function fetchRedisInfo(): Promise<RedisInfoData> {
  const response = await http.get<ApiResponse<RedisInfoData>>("/system/infra/redis/info");
  return response.data.data;
}

export async function fetchRedisKeys(pattern: string = "*"): Promise<RedisKeyItem[]> {
  const response = await http.get<ApiResponse<RedisKeyItem[]>>("/system/infra/redis/keys", {
    params: { pattern }
  });
  return response.data.data;
}

export async function deleteRedisKey(key: string): Promise<boolean> {
  const response = await http.delete<ApiResponse<boolean>>("/system/infra/redis/keys", {
    params: { key }
  });
  return response.data.data;
}

export async function reloadRedisCache(): Promise<{ success: boolean; clearedKeys: number; message: string }> {
  const response = await http.post<ApiResponse<{ success: boolean; clearedKeys: number; message: string }>>(
    "/system/infra/redis/cache/reload"
  );
  return response.data.data;
}

export async function fetchSlowSqlReport(): Promise<SlowSqlReportData> {
  const response = await http.get<ApiResponse<SlowSqlReportData>>("/system/infra/slow-sql/report");
  return response.data.data;
}

export async function updateSlowSqlThreshold(thresholdMillis: number): Promise<number> {
  const response = await http.post<ApiResponse<number>>("/system/infra/slow-sql/threshold", {
    thresholdMillis
  });
  return response.data.data;
}

export async function clearSlowSql(): Promise<void> {
  await http.delete<ApiResponse<void>>("/system/infra/slow-sql");
}

export async function fetchJvmInfo(): Promise<JvmInfoData> {
  const response = await http.get<ApiResponse<JvmInfoData>>("/system/infra/jvm/info");
  return response.data.data;
}

// ==================== 全链路可控传输加密控制台 (Transport Security) ====================

export async function fetchTransportSecurityOverview(): Promise<TransportSecurityOverview> {
  const response = await http.get<ApiResponse<TransportSecurityOverview>>("/system/security/transport/overview");
  return response.data.data;
}

export async function updateTransportSecurityPolicy(payload: TransportPolicyUpdatePayload): Promise<TransportSecurityOverview> {
  const response = await http.put<ApiResponse<TransportSecurityOverview>>("/system/security/transport/policy", payload);
  return response.data.data;
}

export async function rotateTransportSecurityKey(): Promise<TransportSecurityOverview> {
  const response = await http.post<ApiResponse<TransportSecurityOverview>>("/system/security/transport/rotate-key");
  return response.data.data;
}

export async function configureTransportSecurityKey(
  payload: TransportKeyConfigurePayload
): Promise<TransportSecurityOverview> {
  const response = await http.post<ApiResponse<TransportSecurityOverview>>(
    "/system/security/transport/configure-key",
    payload
  );
  return response.data.data;
}

export async function activateTransportSecurityKey(keyId: string): Promise<TransportSecurityOverview> {
  const response = await http.post<ApiResponse<TransportSecurityOverview>>(
    "/system/security/transport/activate-key",
    { keyId }
  );
  return response.data.data;
}

export async function fetchTransportApiCatalog(): Promise<ApiCatalogSnapshot> {
  const response = await http.get<ApiResponse<ApiCatalogSnapshot>>("/system/security/transport/api-catalog");
  return response.data.data;
}

export async function verifyTransportRoundtrip(
  probeText: string,
  options?: { transportEncrypt?: boolean; onCryptoTrace?: (trace: TransportCryptoTrace) => void }
): Promise<TransportRoundtripResult> {
  const response = await http.post<ApiResponse<TransportRoundtripResult>>(
    "/system/security/transport/verify",
    { probeText, clientSentAtMs: Date.now() },
    {
      transportEncrypt: options?.transportEncrypt ?? true,
      onCryptoTrace: options?.onCryptoTrace
    }
  );
  return response.data.data;
}
