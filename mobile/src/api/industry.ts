import { request } from "../utils/request";

export interface Field {key:string; label:string; type:"text"|"number"|"date"; required:boolean}
export interface Action {key:string; label:string; rowKey?:string; fields:Field[]}
export interface Page {key:string; label:string; columns:Field[]; query:string; actions:string[]}
export interface Entry {
  code:string; name:string; kind:"NATIVE"|"EMBED"|"MODULE"; revision:number; url?:string; columns:Field[]; actions:Action[]; pages?:Page[];
}

export function listIndustryApps() {
  return request<Entry[]>({url:"/api/industry/workspace",params:{terminal:"mobile"},silent:true});
}
export function runIndustryAction(code:string, action:string, requestId:string, input:Record<string,string>) {
  return request<Record<string,string>[]>({
    url:`/api/industry/${encodeURIComponent(code)}/actions/${encodeURIComponent(action)}`,
    method:"POST",params:{terminal:"mobile"},data:{requestId,input},silent:true
  });
}

// Correlates retries of one edit. Authorization comes from the signed server request.
export function operationId(): string {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID();
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, ch => {
    const n=Math.floor(Math.random()*16);
    return (ch==="x" ? n : (n&3)|8).toString(16);
  });
}

export function moduleRecords(code: string, pageNo: number) {
  return request<{ records: Record<string, string>[]; total: number }>({
    url: `/api/apps/${encodeURIComponent(code)}`, params: {pageNo, pageSize: 20}, silent: true
  });
}

export function saveModuleRecord(code: string, action: string, input: Record<string,string>) {
  const {id, ...body} = input;
  const base = `/api/apps/${encodeURIComponent(code)}`;
  if (action === "create") return request({url: base, method: "POST", data: body, silent: true});
  if (!id) throw new Error("缺少业务记录编号");
  const path = `${base}/${encodeURIComponent(id)}`;
  if (action === "update") return request({url: path, method: "PUT", data: body, silent: true});
  if (action === "delete") return request({url: path, method: "DELETE", silent: true});
  throw new Error("未登记的应用操作");
}
