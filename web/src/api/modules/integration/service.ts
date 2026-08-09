import { http } from "@/api/http";
import type { ApiResponse } from "@/types/http";
import type { DatasourceRow, DatasourceSavePayload } from "./types";

export async function fetchDatasources(): Promise<DatasourceRow[]> {
  const response = await http.get<ApiResponse<DatasourceRow[]>>("/integrations/datasources");
  return response.data.data;
}

export async function createDatasource(payload: DatasourceSavePayload): Promise<DatasourceRow> {
  const response = await http.post<ApiResponse<DatasourceRow>>("/integrations/datasources", payload);
  return response.data.data;
}
