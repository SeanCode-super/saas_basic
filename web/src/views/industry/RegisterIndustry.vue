<script setup lang="ts">
import { computed, ref } from "vue";
import { ElMessage } from "element-plus";
import { publishIndustry, registeredIndustryApps, type IndustryField, type IndustryManifest } from "@/api/modules/industry";

const emit = defineEmits<{ published: [] }>();
const visible = ref(false);
const mode = ref<"register" | "field">("register");
const raw = ref("");
const secret = ref("");
const saving = ref(false);
const loading = ref(false);
const apps = ref<IndustryManifest[]>([]);
const appCode = ref("");
const app = computed(() => apps.value.find(item => item.code === appCode.value));
const pageKey=ref("");
const page=computed(()=>app.value?.pages?.find(p=>p.key===pageKey.value));
const formActions=computed(()=>app.value?.actions.filter(a=>page.value ? page.value.actions.includes(a.key) : a.key!=="query") ?? []);
const actionKeys = ref<string[]>([]);
const field = ref<IndustryField>({ key: "", label: "", type: "text", required: false });

async function loadApps() {
  loading.value = true;
  try {
    apps.value = (await registeredIndustryApps()).filter(item => item.kind === "NATIVE");
    select(app.value?.code ?? apps.value[0]?.code ?? "");
  } catch {
    apps.value = [];
    ElMessage.error("读取应用清单失败");
  } finally {
    loading.value = false;
  }
}

function select(code: string) {
  appCode.value = code;
  pageKey.value = app.value?.pages?.[0]?.key ?? "";
  selectPage(pageKey.value);
}
function selectPage(key:string) {
  pageKey.value=key;
  const actions = formActions.value.filter(item=>!item.rowKey);
  actionKeys.value = actions.slice(0, 1).map(item => item.key);
}

function withField() {
  const base = app.value;
  const next = { ...field.value, key: field.value.key.trim(), label: field.value.label.trim() };
  if (!base || !next.label || !/^[a-z][a-z0-9_]{0,39}$/.test(next.key)) {
    throw new Error("请填写字段名称、英文编码");
  }
  if ((page.value?.columns ?? base.columns).some(column => column.key === next.key) || base.actions.some(action =>
    actionKeys.value.includes(action.key) && action.fields.some(column => column.key === next.key))) {
    throw new Error("该字段编码已存在");
  }
  return {
    ...base,
    revision: base.revision + 1,
    columns: page.value ? base.columns : [...base.columns, next],
    pages: base.pages?.map(p=>p.key===pageKey.value ? {...p,columns:[...p.columns,next]} : p),
    actions: base.actions.map(action =>
      actionKeys.value.includes(action.key) ? { ...action, fields: [...action.fields, next] } : action)
  };
}

async function publish() {
  if (saving.value) return;
  saving.value = true;
  try {
    const manifest = mode.value === "field" ? withField() : JSON.parse(raw.value);
    await publishIndustry(manifest, mode.value === "register" ? secret.value || undefined : undefined);
    visible.value = false;
    raw.value = "";
    field.value = { key: "", label: "", type: "text", required: false };
    ElMessage.success("版本已登记，可在目标租户的应用目录开通或升级");
    emit("published");
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || "登记失败");
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <el-button @click="visible = true">管理行业应用</el-button>
  <el-dialog v-model="visible" title="行业应用" width="min(540px, 94vw)" append-to-body @open="loadApps" @closed="secret = ''">
    <el-tabs v-model="mode">
      <el-tab-pane label="登记清单" name="register">
        <el-form label-position="top">
          <el-form-item label="应用清单 JSON"><el-input v-model="raw" type="textarea" :rows="8" /></el-form-item>
          <el-form-item label="原生服务密钥（升级可沿用）"><el-input v-model="secret" type="password" autocomplete="off" /></el-form-item>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="给表单加字段" name="field">
        <el-form v-loading="loading" label-position="top">
          <el-form-item label="应用">
            <el-select :model-value="appCode" placeholder="选择已登记的原生应用" @update:model-value="select">
              <el-option v-for="item in apps" :key="item.code" :label="item.name" :value="item.code" />
            </el-select>
          </el-form-item>
          <template v-if="app">
            <el-form-item v-if="app.pages?.length" label="所属页面">
              <el-select :model-value="pageKey" @change="selectPage"><el-option v-for="p in app.pages" :key="p.key" :label="p.label" :value="p.key" /></el-select>
            </el-form-item>
            <el-form-item label="字段名称"><el-input v-model="field.label" maxlength="100" placeholder="如：所属班组" /></el-form-item>
            <el-form-item label="英文编码"><el-input v-model="field.key" maxlength="40" placeholder="如：team" /></el-form-item>
            <el-form-item label="类型">
              <el-radio-group v-model="field.type"><el-radio-button value="text">文本</el-radio-button><el-radio-button value="number">数字</el-radio-button><el-radio-button value="date">日期</el-radio-button></el-radio-group>
            </el-form-item>
            <el-form-item label="加入表单">
              <el-checkbox-group v-model="actionKeys">
                <el-checkbox v-for="action in formActions" :key="action.key" :value="action.key">{{ action.label }}</el-checkbox>
              </el-checkbox-group>
            </el-form-item>
            <el-checkbox v-model="field.required">必填</el-checkbox>
          </template>
        </el-form>
      </el-tab-pane>
    </el-tabs>
    <template #footer>
      <el-button type="primary" :disabled="mode === 'register' ? !raw.trim() : !app" :loading="saving" @click="publish">
        {{ mode === 'field' && app ? '登记版本 ' + (app.revision + 1) : '登记版本' }}
      </el-button>
    </template>
  </el-dialog>
</template>
