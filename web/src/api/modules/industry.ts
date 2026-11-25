import { http } from "@/api/http";
import type { ApiResponse } from "@/types/http";

export interface IndustryField { key: string; label: string; type: "text" | "number" | "date"; required: boolean }
export interface IndustryAction { key: string; label: string; rowKey?: string; fields: IndustryField[] }
export interface IndustryPage { key: string; label: string; columns: IndustryField[]; query: string; actions: string[] }
export interface IndustryEntry {
  code: string; name: string; revision: number; kind: "NATIVE" | "EMBED" | "MODULE"; url?: string;
  columns: IndustryField[]; actions: IndustryAction[]; pages?: IndustryPage[];
}
export interface IndustryManifest extends Omit<IndustryEntry, "url" | "kind"> {
  kind: "NATIVE" | "EMBED"; description: string; pc: boolean; mobile: boolean; pcUrl?: string; mobileUrl?: string;
}
export async function registeredIndustryApps() {
  return (await http.get<ApiResponse<IndustryManifest[]>>("/industry/registry")).data.data;
}
export async function industryEntries() {
  return (await http.get<ApiResponse<IndustryEntry[]>>("/industry/workspace", { params: { terminal: "pc" } })).data.data;
}
export async function industryAction(code: string, action: string, requestId: string, input: Record<string,string>) {
  return (await http.post<ApiResponse<Record<string,string>[]>>(
    `/industry/${encodeURIComponent(code)}/actions/${encodeURIComponent(action)}`,
    {requestId, input}, {params: {terminal: "pc"}})).data.data;
}
export async function publishIndustry(manifest: unknown, secret?: string) {
  await http.post("/industry/registry", {manifest, secret});
}

export { createRequestId as industryRequestId } from "@/utils/request-id";

export async function moduleRecords(code: string, pageNo: number) {
  return (await http.get<ApiResponse<{ records: Record<string, string>[]; total: number }>>(
    `/apps/${encodeURIComponent(code)}`, { params: { pageNo, pageSize: 20 } })).data.data;
}

export async function saveModuleRecord(code: string, action: string, input: Record<string, string>) {
  const { id, ...body } = input;
  const base = `/apps/${encodeURIComponent(code)}`;
  if (action === "create") return http.post(base, body);
  if (!id) throw new Error("缺少业务记录编号");
  const path = `${base}/${encodeURIComponent(id)}`;
  if (action === "update") return http.put(path, body);
  if (action === "delete") return http.delete(path);
  throw new Error("未登记的应用操作");
}
