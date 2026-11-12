<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { ArrowDown, ArrowUp, Download, Lock, Collection, Promotion } from "@element-plus/icons-vue";
import {
  executeSyncExport,
  fetchExportDatasets,
  fetchExportTemplates,
  saveExportTemplate,
  submitAsyncExport,
  type ExportDatasetInfo,
  type ExportFieldSpec,
  type ExportTemplateRow
} from "@/api/modules/export";

const props = withDefaults(
  defineProps<{
    visible: boolean;
    sourceCode?: string;
    defaultTitle?: string;
    filters?: Record<string, unknown>;
    adHocColumns?: string[];
    adHocColumnLabels?: Record<string, string>;
    adHocRows?: Array<Record<string, unknown>>;
    insights?: string[];
  }>(),
  {
    sourceCode: "",
    defaultTitle: "",
    filters: () => ({}),
    adHocColumns: () => [],
    adHocColumnLabels: () => ({}),
    adHocRows: () => [],
    insights: () => []
  }
);

const emit = defineEmits<{
  (e: "update:visible", val: boolean): void;
  (e: "exported", detail: { mode: "sync" | "async"; traceCode: string }): void;
}>();

const router = useRouter();
const loadingMeta = ref(false);
const submitting = ref(false);
const datasets = ref<ExportDatasetInfo[]>([]);
const templates = ref<ExportTemplateRow[]>([]);
const selectedSourceCode = ref("");
const taskName = ref("");
const exportFormat = ref<"XLSX" | "CSV" | "PDF" | "HTML_REPORT">("XLSX");
const deliveryMode = ref<"sync" | "async">("sync");
const includeWatermark = ref(true);
const desensitizeEnabled = ref(true);
const requireApproval = ref(false);
const orderedFields = ref<ExportFieldSpec[]>([]);
const checkedFieldCodes = ref<string[]>([]);
const activeTemplateId = ref<number | null>(null);
const newTemplateName = ref("");
const savingTemplate = ref(false);

const isAdHocMode = computed(() => (props.adHocRows?.length ?? 0) > 0);

async function bootstrapDialog() {
  loadingMeta.value = true;
  try {
    if (isAdHocMode.value) {
      const cols = props.adHocColumns.length
        ? props.adHocColumns
        : Object.keys(props.adHocRows[0] || {});
      orderedFields.value = cols.map((code) => ({
        fieldCode: code,
        fieldLabel: props.adHocColumnLabels[code] || code,
        sensitive: /mobile|phone|email|id_card|bank_card/i.test(code),
        dataType: "VARCHAR"
      }));
      checkedFieldCodes.value = [...cols];
      taskName.value = props.defaultTitle || "AI智能分析数据导出";
    } else {
      datasets.value = await fetchExportDatasets();
      selectedSourceCode.value =
        props.sourceCode && datasets.value.some((d) => d.sourceCode === props.sourceCode)
          ? props.sourceCode
          : datasets.value[0]?.sourceCode || "";
      applyDatasetSelection();
    }
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : "加载导出配置失败");
  } finally {
    loadingMeta.value = false;
  }
}

async function applyDatasetSelection() {
  const ds = datasets.value.find((d) => d.sourceCode === selectedSourceCode.value);
  if (!ds) return;
  orderedFields.value = [...ds.fields];
  checkedFieldCodes.value = ds.fields.map((f) => f.fieldCode);
  taskName.value = props.defaultTitle || ds.sourceName;
  activeTemplateId.value = null;
  try {
    templates.value = await fetchExportTemplates(ds.sourceCode);
  } catch {
    templates.value = [];
  }
}

function applyTemplate(tplId: number | null) {
  activeTemplateId.value = tplId;
  if (!tplId) return;
  const tpl = templates.value.find((t) => t.id === tplId);
  if (!tpl) return;
  exportFormat.value = (tpl.defaultFormat as "XLSX" | "CSV" | "PDF" | "HTML_REPORT") || "XLSX";
  includeWatermark.value = tpl.includeWatermark;
  desensitizeEnabled.value = tpl.desensitizeEnabled;
  const validSet = new Set(tpl.fields);
  const prioritized = tpl.fields
    .map((code) => orderedFields.value.find((f) => f.fieldCode === code))
    .filter((f): f is ExportFieldSpec => Boolean(f));
  const remaining = orderedFields.value.filter((f) => !validSet.has(f.fieldCode));
  orderedFields.value = [...prioritized, ...remaining];
  checkedFieldCodes.value = prioritized.map((f) => f.fieldCode);
  ElMessage.success(`已套用模板「${tpl.templateName}」`);
}

function moveField(index: number, delta: -1 | 1) {
  const target = index + delta;
  if (target < 0 || target >= orderedFields.value.length) return;
  const next = [...orderedFields.value];
  const [item] = next.splice(index, 1);
  next.splice(target, 0, item);
  orderedFields.value = next;
}

function toggleSelectAll(checked: boolean) {
  checkedFieldCodes.value = checked ? orderedFields.value.map((f) => f.fieldCode) : [];
}

async function handleSaveTemplate() {
  if (!selectedSourceCode.value || checkedFieldCodes.value.length === 0) {
    ElMessage.warning("请先选择数据源和导出字段");
    return;
  }
  const tplName = newTemplateName.value.trim();
  if (!tplName) {
    ElMessage.warning("请输入要保存的模板名称");
    return;
  }
  savingTemplate.value = true;
  try {
    const selectedOrdered = orderedFields.value
      .filter((f) => checkedFieldCodes.value.includes(f.fieldCode))
      .map((f) => f.fieldCode);
    const saved = await saveExportTemplate({
      templateName: tplName,
      sourceCode: selectedSourceCode.value,
      fields: selectedOrdered,
      defaultFormat: exportFormat.value,
      includeWatermark: includeWatermark.value,
      desensitizeEnabled: desensitizeEnabled.value
    });
    templates.value = [saved, ...templates.value];
    activeTemplateId.value = saved.id;
    newTemplateName.value = "";
    ElMessage.success(`导出模板「${saved.templateName}」已保存`);
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : "模板保存失败");
  } finally {
    savingTemplate.value = false;
  }
}

async function handleExecuteExport() {
  const selectedOrdered = orderedFields.value
    .filter((f) => checkedFieldCodes.value.includes(f.fieldCode))
    .map((f) => f.fieldCode);
  if (selectedOrdered.length === 0) {
    ElMessage.warning("请至少保留 1 个导出字段");
    return;
  }
  submitting.value = true;
  try {
    const payload = {
      taskName: taskName.value.trim() || "企业数据导出",
      sourceType: isAdHocMode.value ? ("AD_HOC" as const) : ("DATASET" as const),
      sourceCode: isAdHocMode.value ? "nl2sql_result" : selectedSourceCode.value,
      exportFormat: exportFormat.value,
      selectedFields: selectedOrdered,
      filters: props.filters,
      adHocColumns: isAdHocMode.value ? selectedOrdered : undefined,
      adHocColumnLabels: isAdHocMode.value ? props.adHocColumnLabels : undefined,
      adHocRows: isAdHocMode.value ? props.adHocRows : undefined,
      insights: props.insights,
      includeWatermark: includeWatermark.value,
      desensitizeEnabled: desensitizeEnabled.value,
      async: deliveryMode.value === "async" || requireApproval.value,
      requireApproval: requireApproval.value
    };

    if (deliveryMode.value === "sync" && !requireApproval.value) {
      const res = await executeSyncExport(payload);
      ElMessage.success(`导出成功（追溯码 ${res.traceCode}）`);
      emit("exported", { mode: "sync", traceCode: res.traceCode });
      emit("update:visible", false);
    } else {
      const task = await submitAsyncExport(payload);
      const isApproval = task.status === "PENDING_APPROVAL";
      ElMessage.success({
        message: isApproval
          ? `高危/敏感导出任务已提交风控审批（${task.traceCode}），管理员在下载中心审批放行后自动生成`
          : `异步导出任务已入队（${task.traceCode}），完成后将实时通知并存入下载中心`,
        duration: 4500
      });
      emit("exported", { mode: "async", traceCode: task.traceCode });
      emit("update:visible", false);
    }
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : "导出任务执行失败");
  } finally {
    submitting.value = false;
  }
}

function goDownloadCenter() {
  emit("update:visible", false);
  router.push("/file/download-center");
}

watch(
  () => props.visible,
  (val) => {
    if (val) {
      void bootstrapDialog();
    }
  }
);
</script>

<template>
  <el-dialog
    :model-value="visible"
    width="760px"
    title="企业级安全数据导出与报告中心"
    class="export-dialog"
    destroy-on-close
    @update:model-value="(val) => emit('update:visible', val)"
  >
    <div v-loading="loadingMeta" class="export-body">
      <div class="config-grid">
        <div class="field-item" v-if="!isAdHocMode">
          <label>数据源实体</label>
          <el-select
            v-model="selectedSourceCode"
            style="width: 100%"
            :disabled="Boolean(sourceCode)"
            @change="applyDatasetSelection"
          >
            <el-option
              v-for="ds in datasets"
              :key="ds.sourceCode"
              :label="`${ds.sourceName} (${ds.domain})`"
              :value="ds.sourceCode"
            />
          </el-select>
        </div>

        <div class="field-item">
          <label>导出任务 / 文件标题</label>
          <el-input v-model="taskName" placeholder="请输入导出的文件名称" />
        </div>

        <div class="field-item" v-if="!isAdHocMode && templates.length">
          <label>套用已存模板</label>
          <el-select
            :model-value="activeTemplateId"
            clearable
            placeholder="选择快捷导出模板"
            style="width: 100%"
            @change="applyTemplate"
          >
            <el-option
              v-for="tpl in templates"
              :key="tpl.id"
              :label="`${tpl.templateName} (${tpl.fields.length}字段)`"
              :value="tpl.id"
            />
          </el-select>
        </div>

        <div class="field-item field-item--full">
          <label>导出文件格式</label>
          <el-radio-group v-model="exportFormat" size="default">
            <el-radio-button value="XLSX">Excel (.xlsx 流式防OOM)</el-radio-button>
            <el-radio-button value="CSV">CSV (UTF-8 BOM)</el-radio-button>
            <el-radio-button value="PDF">二进制 PDF 报告 (.pdf)</el-radio-button>
            <el-radio-button value="HTML_REPORT">A4 智能分析报告 (.html)</el-radio-button>
          </el-radio-group>
        </div>
      </div>

      <div class="security-strip">
        <div class="sec-toggles">
          <el-switch v-model="includeWatermark" active-text="注入溯源水印 (可见 + 隐写元数据)" />
          <el-switch v-model="desensitizeEnabled" active-text="敏感字段自动脱敏 (手机/邮箱/证件)" />
          <el-checkbox v-model="requireApproval">提交管理员审批放行后生成</el-checkbox>
        </div>
        <div class="sec-mode">
          <span class="mode-label">执行方式：</span>
          <el-radio-group v-model="deliveryMode" size="small">
            <el-radio value="sync">立即流式下载 (≤5000行)</el-radio>
            <el-radio value="async">后台异步排队 (大批量→下载中心)</el-radio>
          </el-radio-group>
        </div>
      </div>

      <div class="columns-section">
        <div class="cols-header">
          <span class="cols-title">自定义导出列与顺序（已选 {{ checkedFieldCodes.length }} / {{ orderedFields.length }}）</span>
          <div class="cols-actions">
            <el-button link type="primary" size="small" @click="toggleSelectAll(true)">全选</el-button>
            <el-button link size="small" @click="toggleSelectAll(false)">清空</el-button>
          </div>
        </div>

        <el-checkbox-group v-model="checkedFieldCodes" class="cols-list">
          <div v-for="(field, idx) in orderedFields" :key="field.fieldCode" class="col-row">
            <el-checkbox :value="field.fieldCode">
              <span class="col-label">{{ field.fieldLabel }}</span>
              <code class="col-code">{{ field.fieldCode }}</code>
              <el-tag v-if="field.sensitive && desensitizeEnabled" size="small" type="warning" effect="plain" class="mask-tag">
                <el-icon><Lock /></el-icon> 脱敏
              </el-tag>
            </el-checkbox>
            <div class="col-sort-btns">
              <el-button link size="small" :disabled="idx === 0" :icon="ArrowUp" @click="moveField(idx, -1)" />
              <el-button link size="small" :disabled="idx === orderedFields.length - 1" :icon="ArrowDown" @click="moveField(idx, 1)" />
            </div>
          </div>
        </el-checkbox-group>
      </div>

      <div v-if="!isAdHocMode" class="save-tpl-bar">
        <el-input
          v-model="newTemplateName"
          size="small"
          placeholder="将当前勾选列与安全策略存为复用模板，例如：财务月度对账单"
          style="max-width: 420px"
        />
        <el-button size="small" :icon="Collection" :loading="savingTemplate" @click="handleSaveTemplate">
          存为模板
        </el-button>
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button link type="primary" :icon="Promotion" @click="goDownloadCenter">前往下载中心</el-button>
        <div class="footer-right">
          <el-button @click="emit('update:visible', false)">取消</el-button>
          <el-button type="primary" :icon="Download" :loading="submitting" @click="handleExecuteExport">
            {{ deliveryMode === "sync" ? "立即安全导出" : "提交后台队列" }}
          </el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.export-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.config-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.field-item {
  display: flex;
  flex-direction: column;
  gap: 6px;

  &--full {
    grid-column: 1 / -1;
  }

  label {
    font-size: 12px;
    font-weight: 600;
    color: var(--app-text-secondary, #475569);
  }
}

.security-strip {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px 14px;
  border-radius: 10px;
  background: var(--app-surface-muted, #f8fafc);
  border: 1px solid var(--app-border-soft, #e2e8f0);
}

.sec-toggles {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
}

.sec-mode {
  display: flex;
  align-items: center;
  gap: 8px;

  .mode-label {
    font-size: 12px;
    color: var(--app-text-secondary, #64748b);
  }
}

.columns-section {
  border: 1px solid var(--app-border-soft, #e2e8f0);
  border-radius: 10px;
  overflow: hidden;
}

.cols-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  background: var(--app-surface-muted, #f8fafc);
  border-bottom: 1px solid var(--app-border-soft, #e2e8f0);

  .cols-title {
    font-size: 13px;
    font-weight: 600;
  }
}

.cols-list {
  display: flex;
  flex-direction: column;
  max-height: 240px;
  overflow-y: auto;
  padding: 6px 10px;
}

.col-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 8px;
  border-radius: 6px;

  &:hover {
    background: var(--app-surface-muted, #f1f5f9);
  }
}

.col-label {
  font-weight: 500;
  margin-right: 8px;
}

.col-code {
  font-size: 11px;
  color: #64748b;
  background: #f1f5f9;
  padding: 1px 6px;
  border-radius: 4px;
}

.mask-tag {
  margin-left: 8px;
}

.save-tpl-bar {
  display: flex;
  align-items: center;
  gap: 10px;
}

.dialog-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.footer-right {
  display: flex;
  gap: 10px;
}
</style>
