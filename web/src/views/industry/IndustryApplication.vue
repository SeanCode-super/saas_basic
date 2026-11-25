<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { ElMessage } from "element-plus";
import { moduleRecords, saveModuleRecord, industryAction, industryEntries, industryRequestId, type IndustryAction, type IndustryEntry } from "@/api/modules/industry";

const route = useRoute();
const packagedApplication = "";
const code = computed(() => packagedApplication || String(route.params.appCode ?? route.path.split("/").pop()));
const app = ref<IndustryEntry>();
const pageKey = ref("");
const section = computed(() => app.value?.pages?.find(item => item.key === pageKey.value));
const columns = computed(() => section.value?.columns ?? app.value?.columns ?? []);
const actions = computed(() => (app.value?.actions ?? []).filter(action =>
  section.value ? section.value.actions.includes(action.key) : action.key !== "query"));
function selectPage(key: string) {
  pageKey.value = key;
  void refresh();
}
const rows = ref<Record<string,string>[]>([]);
const error = ref("");
const loading = ref(false);
const saving = ref(false);
const selected = ref<IndustryAction>();
const form = ref<Record<string,string>>({});
let operation = "";
let lastPayload = "";
let sequence = 0;
const page = ref(1);
const total = ref(0);
const dialog = computed({ get: () => !!selected.value, set: (v: boolean) => { if (!v) selected.value = undefined; } });
const frame = computed(() => {
  if (!app.value?.url) return "";
  const url = new URL(app.value.url);
  return url.protocol === "https:" && url.origin !== location.origin ? url.href : "";
});

async function refresh() {
  const revision = ++sequence;
  const target = code.value;
  loading.value = true;
  app.value = undefined;
  selected.value = undefined;
  rows.value = [];
  error.value = "";
  try {
    const entry = (await industryEntries()).find(item => item.code === target);
    if (!entry) throw new Error("当前账号没有此应用的访问权限");
    if (revision !== sequence) return;
    app.value = entry;
    if (!entry.pages?.some(p => p.key === pageKey.value)) pageKey.value = entry.pages?.[0]?.key ?? "";
    if (entry.kind === "MODULE") {
      const result = await moduleRecords(target, page.value);
      if (revision === sequence) {
        rows.value = result.records;
        total.value = result.total;
      }
    } else if (entry.kind === "NATIVE") {
      const result = await industryAction(target, section.value?.query ?? "query", industryRequestId(), {});
      if (revision === sequence) rows.value = result;
    }
  } catch (e: any) {
    if (revision === sequence) error.value = e?.response?.data?.message || e?.message || "应用暂时不可用";
  } finally { if (revision === sequence) loading.value = false; }
}
function open(action: IndustryAction, row?: Record<string, string>) {
  form.value = action.rowKey && row ? { [action.rowKey]: String(row[action.rowKey]) } : {};
  if (app.value?.kind === "MODULE" && row && action.key === "update") {
    for (const field of action.fields) form.value[field.key] = String(row[field.key] ?? "");
  }
  operation = "";
  selected.value = action;
}
async function submit() {
  if (!selected.value || saving.value) return;
  if (selected.value.fields.some(f => f.required && !form.value[f.key]?.trim())) {
    ElMessage.warning("请填写必填项"); return;
  }
  const payload = JSON.stringify(form.value);
  if (!operation || lastPayload !== payload) operation = industryRequestId();
  lastPayload = payload;
  saving.value = true;
  try {
    if (app.value?.kind === "MODULE") await saveModuleRecord(code.value, selected.value.key, form.value);
    else await industryAction(code.value, selected.value.key, operation, form.value);
    ElMessage.success("已保存");
    await refresh();
  } catch (e: any) { ElMessage.error(e?.response?.data?.message || e?.message || "提交失败"); }
  finally { saving.value = false; }
}
watch(code, () => { page.value = 1; void refresh(); }, {immediate: true});
</script>

<template>
  <section v-loading="loading" class="industry-page">
    <header><h2>{{ app?.name || '行业应用' }}</h2><el-button @click="refresh">刷新</el-button></header>
    <nav v-if="app?.pages?.length" class="industry-actions" aria-label="应用页面">
      <el-button v-for="item in app.pages" :key="item.key" :type="item.key === pageKey ? 'primary' : 'default'" @click="selectPage(item.key)">{{ item.label }}</el-button>
    </nav>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <template v-else-if="app?.kind === 'NATIVE' || app?.kind === 'MODULE'">
      <div class="industry-actions">
        <el-button v-for="action in actions.filter(a => !a.rowKey)" :key="action.key" @click="open(action)">{{ action.label }}</el-button>
      </div>
      <el-table :data="rows" empty-text="暂无记录">
        <el-table-column v-for="field in columns" :key="field.key" :prop="field.key" :label="field.label" />
        <el-table-column v-if="actions.some(a => a.rowKey)" label="操作" min-width="140">
          <template #default="{ row }">
            <el-button v-for="action in actions.filter(a => a.rowKey)" :key="action.key" link type="primary" @click="open(action, row)">{{ action.label }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-if="app.kind === 'MODULE' && total > 20" v-model:current-page="page" :page-size="20"
        :total="total" layout="prev, pager, next" @current-change="refresh" />
    </template>
    <iframe v-else-if="app && frame" :src="frame" :title="app.name" sandbox="allow-forms allow-scripts allow-same-origin" referrerpolicy="no-referrer" />
    <el-alert v-else-if="app" title="请配置独立 HTTPS 域名的应用入口" type="warning" />
    <el-dialog v-model="dialog" :title="selected?.label" width="min(460px, 94vw)" append-to-body>
      <p v-if="app?.kind === 'MODULE' && selected?.key === 'delete'">确认删除这条记录？</p>
      <el-form label-position="top">
        <el-form-item v-for="field in selected?.fields" :key="field.key" :label="field.label" :required="field.required">
          <el-input v-model="form[field.key]" :type="field.type" maxlength="2048" :readonly="field.key === selected?.rowKey" />
        </el-form-item>
      </el-form>
      <template #footer><el-button type="primary" :loading="saving" @click="submit">提交</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.industry-page { padding: 24px; }
header, .industry-actions { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
header { justify-content: space-between; }
h2 { margin: 0; }
iframe { width: 100%; min-height: 72vh; border: 0; }
</style>
