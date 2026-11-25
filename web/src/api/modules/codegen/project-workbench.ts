import { http } from "@/api/http";
import type { ApiResponse } from "@/types/http";
import type { SourceUnit } from "@/utils/project-workbench/inventory";
import type { IndustryManifest } from "@/api/modules/industry";

export interface Blueprint {
  code:string; name:string; description:string; revision:number; roles:string[]; workflow:string[]; decisions:string[];
  pages:Array<{key:string;label:string;evidence:string;path:string;rowsAt:string;
    columns:Array<{key:string;label:string;type:string;source:string}>;
    actions:Array<{key:string;label:string;method:string;path:string;evidence:string;fields:unknown[]}>}>;
}
export interface Sources {units:SourceUnit[];model?:string;previous?:Blueprint}
export interface ProjectPreview {manifest:IndustryManifest;files:Array<{path:string;code:string;layer:string}>}
function unpack<T>(res:ApiResponse<T>):T {
  if (!res.success) throw Error(res.message);
  return res.data;
}
export async function analyzeProject(sources:Sources) {
  return unpack((await http.post<ApiResponse<{design:Blueprint;model:string;runNo:string}>>(
    "/codegen/project-workbench/analyze",sources,{timeout:120000})).data);
}
export async function previewProject(sources:Sources, design:Blueprint) {
  return unpack((await http.post<ApiResponse<ProjectPreview>>("/codegen/project-workbench/preview",{sources,design})).data);
}
export async function downloadProject(sources:Sources, design:Blueprint) {
  const {data} = await http.post<Blob>("/codegen/project-workbench/download",{sources,design},{responseType:"blob"});
  if (data.type.includes("json")) throw Error(JSON.parse(await data.text()).message);
  const url=URL.createObjectURL(data);
  const a=document.createElement("a");
  a.href=url; a.download=design.code+".zip"; a.click();
  setTimeout(()=>URL.revokeObjectURL(url),1000);
}

export interface ProjectInstallation {code:string;name:string;revision:number;tenantCode:string;path:string}
export async function installProject(tenantId:number,sources:Sources,design:Blueprint) {
  return unpack((await http.post<ApiResponse<ProjectInstallation>>("/codegen/project-workbench/install",{tenantId,sources,design})).data);
}
