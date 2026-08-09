import { http } from "@/api/http";
import type { ApiResponse } from "@/types/http";
import type {
  PortalClientRow,
  PortalClientSavePayload,
  PortalEntry,
  PortalTerminalRow,
  PortalTerminalSavePayload
} from "./types";

export async function fetchPortalEntry(params: { clientId?: string; terminalCode?: string }) {
  const response = await http.get<ApiResponse<PortalEntry>>("/portal/entry", {
    params
  });
  return response.data.data;
}

export async function fetchPortalClients(): Promise<PortalClientRow[]> {
  const response = await http.get<ApiResponse<PortalClientRow[]>>("/portal/clients");
  return response.data.data;
}

export async function fetchPortalTerminals(params?: { clientId?: string }): Promise<PortalTerminalRow[]> {
  const response = await http.get<ApiResponse<PortalTerminalRow[]>>("/portal/terminals", {
    params
  });
  return response.data.data;
}

export async function createPortalClient(payload: PortalClientSavePayload): Promise<PortalClientRow> {
  const response = await http.post<ApiResponse<PortalClientRow>>("/portal/clients", payload);
  return response.data.data;
}

export async function updatePortalClient(id: number, payload: PortalClientSavePayload): Promise<PortalClientRow> {
  const response = await http.put<ApiResponse<PortalClientRow>>(`/portal/clients/${id}`, payload);
  return response.data.data;
}

export async function createPortalTerminal(payload: PortalTerminalSavePayload): Promise<PortalTerminalRow> {
  const response = await http.post<ApiResponse<PortalTerminalRow>>("/portal/terminals", payload);
  return response.data.data;
}

export async function updatePortalTerminal(id: number, payload: PortalTerminalSavePayload): Promise<PortalTerminalRow> {
  const response = await http.put<ApiResponse<PortalTerminalRow>>(`/portal/terminals/${id}`, payload);
  return response.data.data;
}
