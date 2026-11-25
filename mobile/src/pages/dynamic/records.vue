<script setup lang="ts">
import { computed, ref } from "vue";
import { onLoad, onShow, onPullDownRefresh } from "@dcloudio/uni-app";
import { request } from "../../utils/request";
import { apiGetUserInfo } from "../../api/auth";
import { sessionGeneration } from "../../utils/warm-cache";

interface Field {
  fieldCode: string; fieldName: string; componentType: string; isRequired: boolean; optionsJson?: string;
}
interface Model { modelName: string; fields: Field[] }
const meta = ref<Model>();
const rows = ref<Record<string, unknown>[]>([]);
const draft = ref<Record<string, string | number>>({});
const editing = ref(false);
const permissions = ref<string[]>([]);
const recordId = ref<string>();
function allowed(action: string) {
  return permissions.value.some(p => ["*", "*:*:*", "codegen:project:write", `dynamic:${code.value}:${action}`].includes(p));
}
function edit(row?: Record<string, unknown>) {
  recordId.value = row ? String(row.id) : undefined;
  draft.value = {};
  for (const field of meta.value?.fields ?? []) {
    const value = row?.[field.fieldCode];
    if (value != null) draft.value[field.fieldCode] = String(value);
  }
  editing.value = true;
}
async function remove(row: Record<string, unknown>) {
  const result = await uni.showModal({title: "删除记录", content: "确认删除这条记录？"});
  if (!result.confirm || !allowed("delete") || loading.value) return;
  loading.value = true;
  try {
    await request({url: endpoint.value + "/" + encodeURIComponent(String(row.id)), method: "DELETE", silent: true});
    if (rows.value.length === 1 && page.value > 1) page.value--;
    await refresh();
  } catch (e) {
    error.value = e instanceof Error ? e.message : "删除失败";
  } finally { loading.value = false; }
}
const error = ref("");
const loading = ref(false);
const page = ref(1);
const total = ref(0);
const code = ref("");
const endpoint = computed(() => "/api/dynamic/" + encodeURIComponent(code.value));
let requestNumber = 0;

function choices(field: Field): Array<{ label: string; value: string | number }> {
  if (!field.optionsJson) return [];
  let values;
  try { values = JSON.parse(field.optionsJson); } catch { values = field.optionsJson.split(","); }
  return Array.isArray(values) ? values.map(v => typeof v === "object" && v !== null
    ? { label: String(v.label ?? v.value), value: v.value } : { label: String(v), value: v }) : [];
}
async function refresh() {
  if (!code.value) return;
  const n = ++requestNumber;
  const session = sessionGeneration();
  loading.value = true;
  error.value = "";
  try {
    const [model, result, user] = await Promise.all([
      request<Model>({url: endpoint.value + "/meta", silent: true}),
      request<{records: Record<string, unknown>[]; total: number}>({url: endpoint.value + "/page", method: "POST", data: { page: page.value, size: 20 }, silent: true}),
      apiGetUserInfo()
    ]);
    if (n !== requestNumber || session !== sessionGeneration()) return;
    meta.value = model;
    rows.value = result.records;
    total.value = result.total;
    permissions.value = user.permissions ?? [];
    uni.setNavigationBarTitle({title: model.modelName});
  } catch (e) {
    if (n !== requestNumber) return;
    meta.value = undefined;
    rows.value = [];
    editing.value = false;
    error.value = e instanceof Error ? e.message : "应用读取失败";
  } finally {
    if (n === requestNumber) loading.value = false;
    uni.stopPullDownRefresh();
  }
}
function changePage(step: number) { page.value += step; void refresh(); }
async function save() {
  if (!meta.value || loading.value || !allowed(recordId.value ? "update" : "create")) return;
  if (meta.value.fields.some(f => f.isRequired && !String(draft.value[f.fieldCode] ?? "").trim())) {
    error.value = "请填写必填字段"; return;
  }
  loading.value = true;
  try {
    await request({url: recordId.value ? endpoint.value + "/" + encodeURIComponent(recordId.value) : endpoint.value,
      method: recordId.value ? "PUT" : "POST", data: draft.value, silent: true});
    editing.value = false;
    draft.value = {};
    page.value = 1;
    await refresh();
  } catch (e) {
    error.value = e instanceof Error ? e.message : "保存失败";
  } finally { loading.value = false; }
}
onLoad(options => { code.value = String(options?.code ?? ""); });
onShow(refresh);
onPullDownRefresh(refresh);
</script>
<template>
  <view class="records">
    <view v-if="error" role="alert">{{ error }} <button v-if="!editing" size="mini" @click="refresh">重试</button></view>
    <template v-if="meta">
      <button v-if="allowed('create') && !editing" @click="edit()">新增记录</button>
      <view v-if="editing" class="entry">
        <view v-for="f in meta.fields" :key="f.fieldCode" class="field">
          <text>{{ f.fieldName }}{{ f.isRequired ? ' *' : '' }}</text>
          <picker v-if="f.componentType === 'select'" :range="choices(f)" range-key="label" @change="draft[f.fieldCode] = choices(f)[Number($event.detail.value)].value">
            <view>{{ choices(f).find(o => o.value === draft[f.fieldCode])?.label ?? '请选择' }}</view>
          </picker>
          <picker v-else-if="f.componentType === 'date'" mode="date" @change="draft[f.fieldCode] = String($event.detail.value)">
            <view>{{ draft[f.fieldCode] || '选择日期' }}</view>
          </picker>
          <input v-else v-model="draft[f.fieldCode]" :type="f.componentType === 'number' ? 'digit' : 'text'" :placeholder="f.componentType === 'datetime' ? 'YYYY-MM-DD HH:mm:ss' : f.fieldName" />
        </view>
        <button :disabled="loading" @click="save">保存</button>
        <button :disabled="loading" @click="editing = false">取消</button>
      </view>
      <view v-else>
        <view v-for="r in rows" :key="String(r.id)" class="entry">
          <view v-for="f in meta.fields" :key="f.fieldCode" class="field">
            <text>{{ f.fieldName }}</text><text>{{ r[f.fieldCode] ?? '—' }}</text>
          </view>
          <button v-if="allowed('update')" size="mini" :disabled="loading" @click="edit(r)">编辑</button>
          <button v-if="allowed('delete')" size="mini" :disabled="loading" @click="remove(r)">删除</button>
        </view>
        <text v-if="!rows.length && !loading">暂无记录</text>
        <view v-if="total > 20" class="paging">
          <button :disabled="page <= 1 || loading" @click="changePage(-1)">上一页</button>
          <button :disabled="page * 20 >= total || loading" @click="changePage(1)">下一页</button>
        </view>
      </view>
    </template>
  </view>
</template>
<style scoped>
.records { padding: 24rpx; color: #17233b; }
.entry { margin: 20rpx 0; padding: 24rpx; border-radius: 16rpx; background: white; }
.field { display: flex; justify-content: space-between; gap: 20rpx; padding: 10rpx 0; }
.field input { text-align: right; }
.paging { display: flex; }
</style>
