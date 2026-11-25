<script setup lang="ts">
import { computed, ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import { listIndustryApps, moduleRecords, saveModuleRecord, runIndustryAction, operationId, type Action, type Entry } from "../../api/industry";

const packagedApplication = "";
const code=ref("");
const app=ref<Entry>();
const pageKey = ref("");
const section = computed(() => app.value?.pages?.find(item => item.key === pageKey.value));
const columns = computed(() => section.value?.columns ?? app.value?.columns ?? []);
const actions = computed(() => (app.value?.actions ?? []).filter(action =>
  section.value ? section.value.actions.includes(action.key) : action.key !== "query"));
function selectPage(key: string) {
  pageKey.value = key;
  void refresh();
}
const rows=ref<Record<string,string>[]>([]);
const error=ref("");
const busy=ref(false);
const page=ref(1);
const total=ref(0);
const selected=ref<Action>();
const input=ref<Record<string,string>>({});
let operation="";
let lastPayload="";
let sequence = 0;
async function refresh() {
  const revision = ++sequence;
  busy.value=true;
  error.value="";
  app.value=undefined;
  rows.value=[];
  selected.value=undefined;
  try {
    const active=(await listIndustryApps()).find(item=>item.code===code.value);
    if (revision !== sequence) return;
    if (!active) throw Error("当前账号没有此应用的访问权限");
    app.value=active;
    if (!active.pages?.some(p => p.key === pageKey.value)) pageKey.value = active.pages?.[0]?.key ?? "";
    uni.setNavigationBarTitle({title:active.name});
    if (active.kind==="MODULE") {
      const result = await moduleRecords(active.code, page.value);
      if (revision !== sequence) return;
      rows.value = result.records;
      total.value = result.total;
    } else if (active.kind==="NATIVE") {
      const result = await runIndustryAction(active.code,section.value?.query ?? "query",operationId(),{});
      if (revision === sequence) rows.value = result;
    }
  } catch(e:any) { if (revision === sequence) error.value=e?.message || "应用暂时不可用"; }
  finally { if (revision === sequence) busy.value=false; }
}
function edit(action: Action, row?: Record<string, string>) {
  selected.value = action;
  input.value = action.rowKey && row ? { [action.rowKey]: String(row[action.rowKey]) } : {};
  if (app.value?.kind === "MODULE" && row && action.key === "update") {
    for (const field of action.fields) input.value[field.key] = String(row[field.key] ?? "");
  }
  operation = "";
  error.value = "";
}
async function submit() {
  const a=selected.value;
  if (!a || busy.value) return;
  if (a.fields.some(f=>f.required && !String(input.value[f.key] ?? "").trim())) { error.value="请填写必填项"; return; }
  const payload=JSON.stringify(input.value);
  if (!operation || lastPayload!==payload) operation=operationId();
  lastPayload=payload;
  busy.value=true;
  try {
    if (app.value?.kind === "MODULE") await saveModuleRecord(code.value,a.key,input.value);
    else await runIndustryAction(code.value,a.key,operation,input.value);
    selected.value=undefined;
    await refresh();
  } catch(e:any) { error.value=e?.message || "提交失败"; }
  finally { busy.value=false; }
}
onLoad(params=>{code.value=packagedApplication || String(params?.code ?? ""); void refresh();});
</script>
<template>
  <view class="application">
    <web-view v-if="app?.kind === 'EMBED' && app.url" :src="app.url" />
    <template v-else>
      <view v-if="app?.pages?.length" class="actions">
        <button v-for="item in app.pages" :key="item.key" role="button" :aria-label="item.label" size="mini" :type="item.key === pageKey ? 'primary' : 'default'" :disabled="busy" @click="selectPage(item.key)">{{ item.label }}</button>
      </view>
      <view v-if="error" class="notice">{{ error }}</view>
      <view v-if="selected" class="record">
        <text class="title">{{ selected.label }}</text>
        <text v-if="app?.kind === 'MODULE' && selected.key === 'delete'">确认删除这条记录？</text>
        <view v-for="f in selected.fields" :key="f.key" class="field">
          <text>{{ f.label }}{{ f.required ? ' *' : '' }}</text>
          <picker v-if="f.type === 'date'" mode="date" :disabled="f.key === selected.rowKey" @change="input[f.key] = String($event.detail.value)">{{ input[f.key] || '选择日期' }}</picker>
          <input v-else v-model="input[f.key]" :type="f.type === 'number' ? 'digit' : 'text'" maxlength="2048" :disabled="f.key === selected.rowKey" />
        </view>
        <button class="submit-button" :disabled="busy" @click="submit">提交</button>
        <button :disabled="busy" @click="selected = undefined; error = ''">返回列表</button>
      </view>
      <template v-else>
        <view class="actions">
          <button v-for="a in actions.filter(x => !x.rowKey)" :key="a.key" size="mini" :disabled="busy" @click="edit(a)">{{ a.label }}</button>
          <button size="mini" :disabled="busy" @click="refresh">刷新</button>
        </view>
        <view v-for="(row,i) in rows" :key="String(row.id ?? i)" class="record">
          <view v-for="f in columns" :key="f.key" class="cell"><text>{{ f.label }}</text><text>{{ row[f.key] ?? '—' }}</text></view>
          <view class="row-actions">
            <button v-for="a in actions.filter(x => x.rowKey)" :key="a.key" size="mini" :disabled="busy" @click="edit(a, row)">{{ a.label }}</button>
          </view>
        </view>
        <view v-if="app?.kind === 'MODULE' && total > 20" class="row-actions">
          <button size="mini" :disabled="busy || page === 1" @click="page--; refresh()">上一页</button>
          <button size="mini" :disabled="busy || page * 20 >= total" @click="page++; refresh()">下一页</button>
        </view>
        <text v-if="!busy && !error && !rows.length">暂无记录</text>
      </template>
    </template>
  </view>
</template>
<style scoped>
.application { padding: 16px; color: var(--ios-text-primary, #0f172a); }
.actions, .row-actions, .cell { display: flex; gap: 10px; margin-bottom: 12px; flex-wrap: wrap; }
button { margin: 4px 0; }
.record { padding: 16px; margin-bottom: 12px; background: white; border-radius: 10px; }
.cell { justify-content: space-between; }
.field { margin: 14px 0; }
.field input, .field picker { margin-top: 5px; padding: 8px; border: 1px solid #cbd5e1; border-radius: 5px; }
.submit-button { margin-top: 18px; background: #e8effc; color: #21395e; font-weight: 600; }
.submit-button:active { background: #dce7fa; }
.notice { padding: 12px 0; color: #9c391d; }
.title { font-weight: 600; }
</style>
