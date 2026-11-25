<script setup lang="ts">
import { computed, onMounted, ref, shallowRef, watch } from "vue";
import { ElMessage } from "element-plus";
import { useAuthStore } from "@/stores/modules/auth";
import { indexDirectory, type Inventory } from "@/utils/project-workbench/inventory";
import { analyzeProject, previewProject, downloadProject, installProject, type ProjectInstallation, type Blueprint, type ProjectPreview } from "@/api/modules/codegen/project-workbench";
import { fetchPublicAiModels } from "@/api/modules/ai";
import { fetchTenantPage, type TenantRow } from "@/api/modules/tenant";
import { publishIndustry } from "@/api/modules/industry";

const auth = useAuthStore();
const picker = ref<HTMLInputElement>();
const importer = ref<HTMLInputElement>();
const inventory = shallowRef<Inventory>();
const directory = ref("");
const models = ref<Awaited<ReturnType<typeof fetchPublicAiModels>>>([]);
const model = ref("");
const busy = ref("");
const error = ref("");
const draft = ref("");
const reviewing = ref(false);
const origin = ref("");
const proposal = shallowRef<{design:Blueprint;model:string;runNo:string}>();
const changes = computed(() => {
  if (!proposal.value || !design.value) return [];
  const before = new Map(design.value.pages.map(p=>[p.key,p]));
  const after = new Map(proposal.value.design.pages.map(p=>[p.key,p]));
  return [...new Set([...before.keys(),...after.keys()])].flatMap(key=>{
    const old=before.get(key), next=after.get(key);
    if (!old) return ["新增 "+next!.label];
    if (!next) return ["移除 "+old.label];
    return JSON.stringify(old)===JSON.stringify(next) ? [] : ["修改 "+next.label];
  });
});
const generated = shallowRef<ProjectPreview>();
const file = ref("");
const installDialog = ref(false);
const tenants = ref<TenantRow[]>([]);
const target = ref<number>();
const installed = ref<ProjectInstallation>();
const canInstall = computed(() => auth.currentUser?.tenantId === 1 && auth.hasPermission("tenant:write") && auth.hasPermission("codegen:project:write"));
const publishDialog = ref(false);
const secret = ref("");
const published = ref(false);
const evidence = ref(false);
const sources = computed(() => ({units:inventory.value?.units ?? [], model:model.value || undefined}));
const routes = computed(() => sources.value.units.reduce((n,u) => n+u.routes.length,0));
const design = computed<Blueprint | undefined>(() => {
  try {
    const d=JSON.parse(draft.value);
    if (Array.isArray(d?.pages) && Array.isArray(d.roles) && Array.isArray(d.workflow) && Array.isArray(d.decisions)
      && d.pages.every((p:Blueprint["pages"][number]) => p && Array.isArray(p.actions) && Array.isArray(p.columns))) return d;
  } catch { /* Draft may be incomplete while editing. */ }
  return undefined;
});
const sourceFile = computed(() => generated.value?.files.find(f=>f.path===file.value));
watch(draft, () => { generated.value=undefined; proposal.value=undefined; published.value=false; installed.value=undefined; error.value=""; },{flush:"sync"});
async function run(label:string, job:()=>Promise<void>) {
  if (busy.value) return;
  error.value=""; busy.value=label;
  try { await job(); }
  catch(e:unknown) {
    const ex=e as {response?:{data?:{message?:string}};message?:string};
    error.value=ex.response?.data?.message || ex.message || "操作失败";
  } finally { busy.value=""; }
}
async function selectDirectory(event:Event) {
  const input=event.target as HTMLInputElement;
  if (!input.files?.length) return;
  const files=Array.from(input.files);
  input.value="";
  await run("读取目录", async()=>{
    const next=await indexDirectory(files);
    if (!next.units.length) throw Error("没有找到可分析的接口、模型或项目文档");
    inventory.value=next; directory.value=files[0].webkitRelativePath.split("/")[0];
    draft.value=""; origin.value=""; reviewing.value=false; evidence.value=false;
  });
}
async function analyze() {
  await run("AI 分析",async()=>{
    const result=await analyzeProject({...sources.value,previous:design.value});
    if (design.value) { proposal.value=result; return; }
    draft.value=JSON.stringify(result.design,null,2);
    origin.value=[result.model,result.runNo].filter(Boolean).join(" · ");
    reviewing.value=false;
  });
}
function adoptProposal() {
  const result=proposal.value;
  if (!result) return;
  draft.value=JSON.stringify(result.design,null,2);
  origin.value=[result.model,result.runNo].filter(Boolean).join(" · ");
}
function renamePage(key:string, value:string) {
  if (design.value && value.trim()) draft.value=JSON.stringify({...design.value,pages:design.value.pages.map(
    p=>p.key===key ? {...p,label:value.trim()} : p)},null,2);
}
function changeList(key:"roles"|"workflow"|"decisions", value:string) {
  if (design.value) draft.value=JSON.stringify({...design.value,[key]:value.split("\n").map(s=>s.trim()).filter(Boolean)},null,2);
}
async function loadDesign(event:Event) {
  const input=event.target as HTMLInputElement;
  const f=input.files?.[0]; input.value="";
  if (!f) return;
  await run("读取蓝图",async()=>{
    if (f.size>512000) throw Error("蓝图文件过大");
    const parsed=JSON.parse(await f.text());
    if (!Array.isArray(parsed?.pages)) throw Error("请选择工程中的 project-design.json");
    draft.value=JSON.stringify(parsed,null,2); origin.value="导入的蓝图"; reviewing.value=false;
  });
}
async function generate() {
  if (!design.value) return;
  await run("生成工程",async()=>{
    generated.value=await previewProject(sources.value,design.value!);
    file.value=generated.value.files[0]?.path ?? ""; reviewing.value=false;
  });
}
function actionLabels(page: Blueprint["pages"][number]) {
  return page.actions.map(a=>a.label).join("、") || "查看";
}
async function download() {
  if (design.value) await run("下载工程",()=>downloadProject(sources.value,design.value!));
}
async function chooseTenant() {
  await run("加载租户", async () => {
    tenants.value = await fetchTenantPage();
    target.value = undefined;
    installDialog.value = true;
  });
}
async function install() {
  if (!target.value || !design.value) return;
  await run("生成应用", async () => {
    installed.value = await installProject(target.value!, sources.value, design.value!);
    installDialog.value = false;
    ElMessage.success("应用已加入租户工作台");
  });
}
async function register() {
  if (!generated.value) return;
  await run("登记应用",async()=>{
    await publishIndustry(generated.value!.manifest,secret.value || undefined);
    publishDialog.value=false; secret.value=""; published.value=true;
    ElMessage.success("已登记，可在租户应用目录开通");
  });
}
onMounted(async()=>{
  try {
    models.value=await fetchPublicAiModels();
    model.value=(models.value.find(m=>m.isDefault) || models.value.find(m=>m.hasApiKey) || models.value[0])?.id || "";
  } catch { /* The configured model ID can also be entered directly. */ }
});
</script>
<template>
  <section class="project-workbench">
    <header><h2>从已有项目生成管理应用</h2><p>选择源码目录，在本机提取接口、模型及页面声明。点击 AI 分析后，结构索引会发送给所选模型。</p></header>
    <input ref="picker" hidden type="file" webkitdirectory multiple @change="selectDirectory" />
    <input ref="importer" hidden type="file" accept=".json,application/json" @change="loadDesign" />
    <div class="workbench-controls">
      <el-button :disabled="!!busy" @click="picker?.click()">{{ directory ? '更换项目目录' : '选择项目目录' }}</el-button>
      <el-select v-model="model" filterable allow-create default-first-option placeholder="模型 ID" aria-label="分析模型">
        <el-option v-for="m in models" :key="m.id" :label="m.name || m.id" :value="m.id" />
      </el-select>
      <el-button type="primary" :disabled="!inventory || !!busy" :loading="busy==='AI 分析'" @click="analyze">{{ design ? 'AI 分析项目差异' : 'AI 分析项目' }}</el-button>
      <el-button :disabled="!inventory || !!busy" @click="importer?.click()">导入已有蓝图</el-button>
    </div>
    <p v-if="busy" role="status">{{ busy }}中…</p>
    <div v-if="inventory" class="workbench-controls"><strong>{{ directory }}</strong><span>{{ inventory.units.length }} 个索引文件 · {{ routes }} 个接口声明</span>
      <el-button link @click="evidence=!evidence">{{ evidence ? '收起索引' : '核对发送内容' }}</el-button>
      <span v-if="inventory.truncated">有 {{ inventory.truncated }} 个文件超出索引上限，可选择子目录补充分析。</span>
    </div>
    <pre v-if="evidence" tabindex="0">{{ JSON.stringify(sources.units,null,2) }}</pre>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <section v-if="proposal" class="project-proposal">
      <h3>待采纳的修订 {{ proposal.design.revision }}</h3>
      <p>{{ changes.join('、') || '页面结构相同' }}</p>
      <details><summary>核对完整修订</summary><pre>{{ JSON.stringify(proposal.design,null,2) }}</pre></details>
      <el-button @click="proposal=undefined">保留当前蓝图</el-button><el-button type="primary" @click="adoptProposal">采用新修订</el-button>
    </section>
    <template v-if="draft">
      <div class="workbench-controls"><h3>业务蓝图</h3><span>{{ origin }}</span><el-button link @click="reviewing=!reviewing">{{ reviewing ? '查看摘要' : '编辑蓝图' }}</el-button></div>
      <el-input v-if="reviewing || !design" v-model="draft" type="textarea" :rows="16" aria-label="业务蓝图 JSON" />
      <template v-if="design">
        <h4>{{ design.name }}</h4><p>{{ design.description }}</p>
        <dl><dt>角色与数据范围</dt><dd>{{ design.roles.join('；') }}</dd><dt>业务流程</dt><dd>{{ design.workflow.join(' → ') }}</dd></dl>
        <el-table :data="design.pages" empty-text="暂无页面"><el-table-column label="页面"><template #default="{row}"><el-input v-if="reviewing" :model-value="row.label" aria-label="页面名称" @change="renamePage(row.key,$event)" /><span v-else>{{ row.label }}</span></template></el-table-column>
          <el-table-column label="业务操作"><template #default="{row}">{{ actionLabels(row) }}</template></el-table-column>
          <el-table-column prop="path" label="查询接口" /><el-table-column prop="evidence" label="源码依据" show-overflow-tooltip /></el-table>
        <el-form v-if="reviewing" label-position="top">
          <el-form-item label="角色与数据范围（每行一项）"><el-input type="textarea" :model-value="design.roles.join('\n')" @change="changeList('roles',$event)" /></el-form-item>
          <el-form-item label="业务流程（每行一步）"><el-input type="textarea" :model-value="design.workflow.join('\n')" @change="changeList('workflow',$event)" /></el-form-item>
          <el-form-item label="待核对事项（每行一项）"><el-input type="textarea" :model-value="design.decisions.join('\n')" @change="changeList('decisions',$event)" /></el-form-item>
        </el-form>
        <section v-if="design.decisions.length"><h4>待核对事项</h4><ul><li v-for="d in design.decisions" :key="d">{{ d }}</li></ul></section>
        <div class="workbench-controls">
          <el-button v-if="canInstall" type="primary" :disabled="!!busy" @click="chooseTenant">生成到天枢</el-button>
          <el-button :disabled="!!busy" @click="generate">确认蓝图并预览工程</el-button>
          <span v-if="installed" role="status">已加入 {{ tenants.find(t=>t.tenantCode===installed?.tenantCode)?.tenantName }} 工作台 · {{ installed.name }}</span>
        </div>
      </template>
    </template>
    <template v-if="generated && design">
      <div class="workbench-controls"><h3>工程源码</h3>
        <el-button :disabled="!!busy" @click="download">下载 ZIP</el-button>
        <el-button v-if="auth.currentUser?.tenantId===1 && auth.hasPermission('tenant:write')" :disabled="published || !!busy" @click="publishDialog=true">{{ published ? '已登记' : '登记到应用目录' }}</el-button>
      </div>
      <el-select v-model="file" aria-label="预览文件"><el-option v-for="f in generated.files" :key="f.path" :value="f.path" :label="f.layer+' · '+f.path" /></el-select>
      <pre tabindex="0">{{ sourceFile?.code }}</pre>
    </template>
    <el-dialog v-model="installDialog" title="生成到天枢" width="min(480px, 94vw)">
      <el-form label-position="top"><el-form-item label="开通租户"><el-select v-model="target" filterable aria-label="开通租户" placeholder="选择租户">
        <el-option v-for="t in tenants" :key="t.id" :label="t.tenantName+' ('+t.tenantCode+')'" :value="t.id" />
      </el-select></el-form-item></el-form>
      <p>应用将加入所选租户的 PC 和手机工作台，由租户管理员分配成员权限。</p>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <template #footer><el-button @click="installDialog=false">取消</el-button><el-button type="primary" :disabled="!target" :loading="!!busy" @click="install">生成并开通</el-button></template>
    </el-dialog>
    <el-dialog v-model="publishDialog" title="登记行业应用" width="min(480px, 94vw)" @closed="secret=''">
      <p>先部署包内业务适配服务并配置行业网关。登记后，由目标租户开通并授权。</p>
      <el-input v-model="secret" type="password" show-password autocomplete="off" placeholder="与业务适配服务一致的签名密钥" aria-label="签名密钥" />
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <template #footer><el-button @click="publishDialog=false">取消</el-button><el-button type="primary" :loading="!!busy" @click="register">登记应用</el-button></template>
    </el-dialog>
  </section>
</template>
<style scoped>
.project-workbench { display: grid; gap: 16px; padding: 24px; background: var(--el-bg-color); border: 1px solid var(--el-border-color-lighter); border-radius: 12px; min-width: 0; }
.workbench-controls { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; }
.workbench-controls .el-select { width: 210px; }
h2,h3,h4,p,dl,dd { margin: 0; }
h2 { font-size: 20px; }
p,dd { color: var(--el-text-color-regular); line-height: 1.6; }
header p { margin-top: 6px; }
dt { font-weight: 600; margin: 8px 0 2px; }
pre { overflow: auto; max-height: 440px; margin: 0; padding: 14px; background: var(--el-fill-color-lighter); font-size: 13px; }
</style>
