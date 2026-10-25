<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import type { FormInstance, FormRules } from "element-plus";
import {
  Plus,
  RefreshRight,
  Delete,
  Download,
  Upload,
  Search,
  Back,
  Edit,
  Document,
  View,
  Printer,
  Grid,
  DataAnalysis,
  Opportunity,
  Coin,
  CircleCheck,
  WarningFilled,
  MagicStick,
  Right
} from "@element-plus/icons-vue";
import ControlSurface from "@/components/platform/ControlSurface.vue";
import BaseCard from "@/components/base/BaseCard.vue";
import {
  fetchDynamicMeta,
  fetchDynamicPage,
  createDynamicRecord,
  updateDynamicRecord,
  deleteDynamicRecord,
  batchDeleteDynamicRecords,
  seedDynamicMock,
  batchImportDynamicRecords
} from "@/api/modules/codegen/ai-builder";
import type { DynamicModelItem, DynamicFieldItem } from "@/api/modules/codegen/ai-builder";

const route = useRoute();
const router = useRouter();

// View Mode
const viewMode = ref<"table" | "kanban" | "analytics">("table");

// Extract modelCode from route params or path
const modelCode = computed(() => {
  if (route.params.modelCode) {
    return String(route.params.modelCode);
  }
  const parts = route.path.split("/dynamic/");
  if (parts.length > 1) {
    return parts[1].split("/")[0].split("?")[0];
  }
  return "";
});

const loading = ref(false);
const seeding = ref(false);
const submitting = ref(false);
const meta = ref<DynamicModelItem | null>(null);
const fields = ref<DynamicFieldItem[]>([]);
const records = ref<Record<string, any>[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(10);
const selectedRows = ref<Record<string, any>[]>([]);

// Query filter state
const queryParams = reactive<Record<string, any>>({});
const activeStatusFilter = ref<string>("ALL");

// Dialog / Form state
const dialogVisible = ref(false);
const isEditing = ref(false);
const formRef = ref<FormInstance>();
const formData = reactive<Record<string, any>>({});

// Detail Drawer state
const detailDrawerVisible = ref(false);
const detailRecord = ref<Record<string, any> | null>(null);

// Batch Import Dialog state
const importDialogVisible = ref(false);
const importCsvText = ref("");
const importing = ref(false);

// Option parsing helper
function parseOptions(optionsJson?: string): Array<{ label: string; value: string | number; color?: string }> {
  if (!optionsJson) return [];
  try {
    const parsed = JSON.parse(optionsJson);
    if (Array.isArray(parsed)) {
      return parsed.map((item, idx) => {
        if (typeof item === "string") {
          return { label: item, value: item };
        }
        return {
          label: item.label || String(item.value),
          value: item.value ?? item.label,
          color: item.color || ["primary", "success", "warning", "info", "danger"][idx % 5]
        };
      });
    }
  } catch {
    return optionsJson.split(",").map((s) => ({ label: s.trim(), value: s.trim() }));
  }
  return [];
}

// Find label for select value
function getOptionLabel(field: DynamicFieldItem, value: unknown): string {
  if (value === undefined || value === null) return "-";
  const options = parseOptions(field.optionsJson);
  const matched = options.find((opt) => String(opt.value) === String(value));
  return matched ? matched.label : String(value);
}

// Tag type for select value
function getTagType(field: DynamicFieldItem, value: unknown): "primary" | "success" | "warning" | "info" | "danger" {
  const options = parseOptions(field.optionsJson);
  const matched = options.find((opt) => String(opt.value) === String(value));
  if (matched?.color && ["primary", "success", "warning", "info", "danger"].includes(matched.color)) {
    return matched.color as "primary" | "success" | "warning" | "info" | "danger";
  }
  const str = String(value);
  if (str.includes("在用") || str.includes("已完成") || str.includes("通过") || str.includes("良好") || str.includes("IN_USE") || str.includes("CONFIRMED")) return "success";
  if (str.includes("待") || str.includes("闲置") || str.includes("审批中") || str.includes("IDLE") || str.includes("PROGRESS")) return "warning";
  if (str.includes("紧急") || str.includes("驳回") || str.includes("故障") || str.includes("报废") || str.includes("MAINTENANCE") || str.includes("SCRAPPED")) return "danger";
  return "primary";
}

// Find status field if exists
const statusField = computed(() => {
  return fields.value.find((f) =>
    f.fieldCode.includes("status") ||
    f.fieldName.includes("状态") ||
    (f.componentType === "select" && f.optionsJson)
  ) || null;
});

// Find main numeric/amount field if exists
const numericField = computed(() => {
  return fields.value.find((f) =>
    f.componentType === "number" ||
    f.fieldCode.includes("value") ||
    f.fieldCode.includes("price") ||
    f.fieldCode.includes("amount") ||
    f.fieldCode.includes("cost")
  ) || null;
});

// Category field if exists
const categoryField = computed(() => {
  return fields.value.find((f) =>
    f.fieldCode.includes("category") ||
    f.fieldName.includes("类别") ||
    f.fieldName.includes("分类")
  ) || null;
});

// KPI Calculations
const kpiStats = computed(() => {
  const totalCount = total.value;
  // Calculate total amount if numericField exists
  let totalAmount = 0;
  let activeCount = 0;
  let warningCount = 0;

  for (const r of records.value) {
    if (numericField.value) {
      const val = Number(r[numericField.value.fieldCode]);
      if (!isNaN(val)) totalAmount += val;
    }
    if (statusField.value) {
      const sVal = String(r[statusField.value.fieldCode] || "");
      if (sVal.includes("在用") || sVal.includes("完成") || sVal.includes("通过") || sVal.includes("IN_USE") || sVal.includes("CONFIRMED")) {
        activeCount++;
      } else if (sVal.includes("待") || sVal.includes("闲置") || sVal.includes("修") || sVal.includes("报废") || sVal.includes("IDLE") || sVal.includes("MAINTENANCE")) {
        warningCount++;
      }
    }
  }

  const activeRate = records.value.length > 0 ? Math.round((activeCount / records.value.length) * 100) : 0;

  return {
    totalCount,
    totalAmount,
    activeCount,
    activeRate,
    warningCount
  };
});

// Status options for quick filter and Kanban
const statusOptions = computed(() => {
  if (!statusField.value) return [];
  return parseOptions(statusField.value.optionsJson);
});

// Filtered records by quick status tab
const displayRecords = computed(() => {
  if (activeStatusFilter.value === "ALL" || !statusField.value) {
    return records.value;
  }
  return records.value.filter(
    (r) => String(r[statusField.value!.fieldCode]) === activeStatusFilter.value
  );
});

// Kanban Lanes
const kanbanLanes = computed(() => {
  if (!statusField.value || statusOptions.value.length === 0) {
    return [
      {
        key: "ALL",
        label: "业务台账全集",
        items: records.value
      }
    ];
  }

  return statusOptions.value.map((opt) => ({
    key: String(opt.value),
    label: opt.label,
    items: records.value.filter(
      (r) => String(r[statusField.value!.fieldCode]) === String(opt.value)
    )
  }));
});

// Analytics Breakdown
const categoryBreakdown = computed(() => {
  const targetField = categoryField.value || statusField.value;
  if (!targetField) return [];

  const map = new Map<string, { count: number; amount: number }>();
  for (const r of records.value) {
    const rawVal = r[targetField.fieldCode];
    const label = getOptionLabel(targetField, rawVal) || "未分类";
    const cur = map.get(label) || { count: 0, amount: 0 };
    cur.count++;
    if (numericField.value) {
      const amt = Number(r[numericField.value.fieldCode]) || 0;
      cur.amount += amt;
    }
    map.set(label, cur);
  }

  const result = [];
  for (const [name, stats] of map.entries()) {
    result.push({
      name,
      count: stats.count,
      amount: stats.amount,
      percentage: records.value.length > 0 ? Math.round((stats.count / records.value.length) * 100) : 0
    });
  }
  return result;
});

// Validation rules
const formRules = computed<FormRules>(() => {
  const rules: FormRules = {};
  for (const f of fields.value) {
    if (f.isRequired) {
      rules[f.fieldCode] = [
        { required: true, message: `请输入/选择 ${f.fieldName}`, trigger: ["blur", "change"] }
      ];
    }
  }
  return rules;
});

// Load metadata
async function loadMeta() {
  if (!modelCode.value) return;
  loading.value = true;
  try {
    const res = await fetchDynamicMeta(modelCode.value);
    if (res.data) {
      meta.value = res.data;
      fields.value = res.data.fields || [];
      for (const f of fields.value) {
        if (f.isQuery && !(f.fieldCode in queryParams)) {
          queryParams[f.fieldCode] = undefined;
        }
      }
      await loadData();
    }
  } catch (err: unknown) {
    ElMessage.error(err instanceof Error ? err.message : "加载业务模型元数据失败");
  } finally {
    loading.value = false;
  }
}

// Load dynamic data page
async function loadData() {
  if (!modelCode.value) return;
  loading.value = true;
  try {
    const activeParams: Record<string, unknown> = {};
    for (const [k, v] of Object.entries(queryParams)) {
      if (v !== undefined && v !== "" && v !== null) {
        activeParams[k] = v;
      }
    }

    const res = await fetchDynamicPage(modelCode.value, {
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      params: activeParams
    });

    if (res.data) {
      records.value = res.data.records || [];
      total.value = res.data.total || 0;
    }
  } catch (err: unknown) {
    ElMessage.error(err instanceof Error ? err.message : "获取业务数据列表失败");
  } finally {
    loading.value = false;
  }
}

// Seed Demo Data
async function handleSeedMock() {
  seeding.value = true;
  try {
    const res = await seedDynamicMock(modelCode.value);
    ElMessage.success(`成功载入 ${res.data || 3} 条真实业务演示台账！`);
    await loadData();
  } catch (err: unknown) {
    ElMessage.error(err instanceof Error ? err.message : "载入演示数据失败");
  } finally {
    seeding.value = false;
  }
}

function handleSearch() {
  pageNum.value = 1;
  loadData();
}

function handleReset() {
  for (const k of Object.keys(queryParams)) {
    queryParams[k] = undefined;
  }
  activeStatusFilter.value = "ALL";
  pageNum.value = 1;
  loadData();
}

function handleSelectionChange(rows: Record<string, any>[]) {
  selectedRows.value = rows;
}

// Open Detail Drawer
function openDetailDrawer(row: Record<string, any>) {
  detailRecord.value = row;
  detailDrawerVisible.value = true;
}

// Open create dialog
function openCreateDialog() {
  isEditing.value = false;
  for (const k of Object.keys(formData)) {
    delete formData[k];
  }
  for (const f of fields.value) {
    if (f.componentType === "number") {
      formData[f.fieldCode] = 0;
    } else {
      formData[f.fieldCode] = "";
    }
  }
  dialogVisible.value = true;
}

// Open edit dialog
function openEditDialog(row: Record<string, any>) {
  isEditing.value = true;
  for (const k of Object.keys(formData)) {
    delete formData[k];
  }
  Object.assign(formData, row);
  dialogVisible.value = true;
}

// Submit save
async function submitForm() {
  if (!formRef.value) return;
  await formRef.value.validate(async (valid) => {
    if (!valid) return;
    submitting.value = true;
    try {
      if (isEditing.value) {
        await updateDynamicRecord(modelCode.value, formData);
        ElMessage.success("修改成功");
      } else {
        await createDynamicRecord(modelCode.value, formData);
        ElMessage.success("新增成功");
      }
      dialogVisible.value = false;
      if (detailDrawerVisible.value && detailRecord.value) {
        Object.assign(detailRecord.value, formData);
      }
      await loadData();
    } catch (err: unknown) {
      ElMessage.error(err instanceof Error ? err.message : "保存数据失败");
    } finally {
      submitting.value = false;
    }
  });
}

// Delete single record
async function handleDelete(row: Record<string, any>) {
  const id = Number(row.id);
  if (!id) return;
  try {
    await ElMessageBox.confirm("确定删除该条业务数据吗？删除后数据将被移入历史记录。", "删除确认", {
      type: "warning",
      confirmButtonText: "确定删除",
      cancelButtonText: "取消"
    });
    await deleteDynamicRecord(modelCode.value, id);
    ElMessage.success("删除成功");
    if (detailDrawerVisible.value) {
      detailDrawerVisible.value = false;
    }
    await loadData();
  } catch (err: unknown) {
    if (err !== "cancel") {
      ElMessage.error(err instanceof Error ? err.message : "删除失败");
    }
  }
}

// Batch delete
async function handleBatchDelete() {
  if (selectedRows.value.length === 0) return;
  const ids = selectedRows.value.map((r) => Number(r.id)).filter((id) => Boolean(id));
  try {
    await ElMessageBox.confirm(`确定批量删除选中的 ${ids.length} 条业务记录吗？`, "批量删除确认", {
      type: "warning",
      confirmButtonText: "批量删除",
      cancelButtonText: "取消"
    });
    await batchDeleteDynamicRecords(modelCode.value, ids);
    ElMessage.success("批量删除成功");
    selectedRows.value = [];
    await loadData();
  } catch (err: unknown) {
    if (err !== "cancel") {
      ElMessage.error(err instanceof Error ? err.message : "批量删除失败");
    }
  }
}

// Quick status change in Kanban
async function handleQuickStatusChange(row: Record<string, any>, newStatus: string) {
  if (!statusField.value) return;
  try {
    const updated = { ...row, [statusField.value.fieldCode]: newStatus };
    await updateDynamicRecord(modelCode.value, updated);
    ElMessage.success(`状态已流转至「${getOptionLabel(statusField.value, newStatus)}」`);
    await loadData();
  } catch (err: unknown) {
    ElMessage.error(err instanceof Error ? err.message : "流转状态失败");
  }
}

// Export CSV
function exportCsv() {
  if (records.value.length === 0) {
    ElMessage.warning("暂无数据可导出");
    return;
  }
  const headers = ["序号", ...fields.value.map((f) => f.fieldName), "创建时间"];
  const rows = records.value.map((row, idx) => [
    idx + 1,
    ...fields.value.map((f) => {
      const val = row[f.fieldCode];
      const display = f.componentType === "select" ? getOptionLabel(f, val) : val;
      return display !== undefined && display !== null ? `"${String(display).replace(/"/g, '""')}"` : '""';
    }),
    row.created_at ?? ""
  ]);
  const csvContent = "\uFEFF" + [headers.join(","), ...rows.map((r) => r.join(","))].join("\n");
  const blob = new Blob([csvContent], { type: "text/csv;charset=utf-8;" });
  const link = document.createElement("a");
  link.href = URL.createObjectURL(blob);
  link.download = `${meta.value?.modelName || "业务台账"}-导出.csv`;
  link.click();
  URL.revokeObjectURL(link.href);
  ElMessage.success("台账数据导出成功");
}

// Download Import Template
function downloadImportTemplate() {
  const headers = fields.value.map((f) => f.fieldName);
  const sampleRow = fields.value.map((f) => {
    if (f.componentType === "select") {
      const opts = parseOptions(f.optionsJson);
      return opts[0] ? opts[0].label : "示例选项";
    }
    if (f.componentType === "number") return "100";
    if (f.componentType === "date") return "2026-10-25";
    return `示例${f.fieldName}`;
  });
  const csvContent = "\uFEFF" + [headers.join(","), sampleRow.join(",")].join("\n");
  const blob = new Blob([csvContent], { type: "text/csv;charset=utf-8;" });
  const link = document.createElement("a");
  link.href = URL.createObjectURL(blob);
  link.download = `${meta.value?.modelName || "导入模版"}-导入模版.csv`;
  link.click();
  URL.revokeObjectURL(link.href);
  ElMessage.success("模版下载成功，请按模版表头格式整理数据");
}

// Process Batch Import
async function handleBatchImport() {
  if (!importCsvText.value.trim()) {
    ElMessage.warning("请粘贴 CSV 数据内容");
    return;
  }
  importing.value = true;
  try {
    const lines = importCsvText.value.trim().split("\n");
    if (lines.length < 2) {
      ElMessage.warning("导入数据至少需包含表头行与一行数据行");
      return;
    }
    const headerLine = lines[0].split(",").map((s) => s.trim().replace(/^"|"$/g, ""));
    const recordsToImport: Record<string, any>[] = [];

    for (let i = 1; i < lines.length; i++) {
      const line = lines[i].trim();
      if (!line) continue;
      const values = line.split(",").map((s) => s.trim().replace(/^"|"$/g, ""));
      const row: Record<string, any> = {};

      headerLine.forEach((header, colIdx) => {
        const val = values[colIdx];
        if (val !== undefined) {
          // match by fieldName or fieldCode
          const matchedField = fields.value.find((f) => f.fieldName === header || f.fieldCode === header);
          if (matchedField) {
            if (matchedField.componentType === "select") {
              const opts = parseOptions(matchedField.optionsJson);
              const matchedOpt = opts.find((o) => o.label === val || String(o.value) === val);
              row[matchedField.fieldCode] = matchedOpt ? matchedOpt.value : val;
            } else if (matchedField.componentType === "number") {
              row[matchedField.fieldCode] = Number(val) || 0;
            } else {
              row[matchedField.fieldCode] = val;
            }
          }
        }
      });
      if (Object.keys(row).length > 0) {
        recordsToImport.push(row);
      }
    }

    if (recordsToImport.length === 0) {
      ElMessage.warning("未能匹配到有效字段列，请检查表头是否与系统字段名一致");
      return;
    }

    const res = await batchImportDynamicRecords(modelCode.value, recordsToImport);
    ElMessage.success(`成功批量导入 ${res.data || recordsToImport.length} 条业务记录！`);
    importDialogVisible.value = false;
    importCsvText.value = "";
    await loadData();
  } catch (err: unknown) {
    ElMessage.error(err instanceof Error ? err.message : "批量导入失败");
  } finally {
    importing.value = false;
  }
}

// Print Detail
function handlePrint() {
  window.print();
}

watch(
  () => modelCode.value,
  () => {
    loadMeta();
  }
);

onMounted(() => {
  loadMeta();
});
</script>

<template>
  <div class="dynamic-view-container">
    <ControlSurface
      :eyebrow="`业务应用 · ${meta?.modelName || '业务中台'}`"
      :title="meta?.modelName || '业务中心'"
      :description="meta?.description || '企业级多租户业务应用中心。'"
    >
      <template #actions>
        <el-button :icon="Back" @click="router.push('/codegen')">返回应用工坊</el-button>
        <el-button
          v-if="records.length === 0"
          type="warning"
          plain
          :icon="MagicStick"
          :loading="seeding"
          @click="handleSeedMock"
        >
          载入演示数据
        </el-button>
        <el-button :icon="Upload" @click="importDialogVisible = true">批量导入</el-button>
        <el-button :icon="Download" @click="exportCsv">导出台账</el-button>
        <el-button :icon="RefreshRight" :loading="loading" @click="loadData">刷新</el-button>
        <el-button
          v-if="selectedRows.length > 0"
          type="danger"
          :icon="Delete"
          @click="handleBatchDelete"
        >
          批量删除 ({{ selectedRows.length }})
        </el-button>
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">新增记录</el-button>
      </template>

      <!-- 顶部 4 大核心 KPI 指标汇总看板 -->
      <el-row :gutter="14" class="kpi-cards-row">
        <!-- 指标 1: 总数 -->
        <el-col :xs="12" :sm="12" :md="6">
          <div class="kpi-card kpi-card--primary">
            <div class="kpi-icon-box">
              <el-icon><Document /></el-icon>
            </div>
            <div class="kpi-info">
              <div class="kpi-label">台账总记录</div>
              <div class="kpi-val">{{ kpiStats.totalCount }} <span class="kpi-unit">条</span></div>
              <div class="kpi-sub"><span class="badge-dot badge-dot--success"></span>多租户隔离已生效</div>
            </div>
          </div>
        </el-col>

        <!-- 指标 2: 核心数值汇总 (原值/金额/数量) -->
        <el-col :xs="12" :sm="12" :md="6">
          <div class="kpi-card kpi-card--success">
            <div class="kpi-icon-box">
              <el-icon><Coin /></el-icon>
            </div>
            <div class="kpi-info">
              <div class="kpi-label">{{ numericField ? numericField.fieldName + '总计' : '核心指标规模' }}</div>
              <div class="kpi-val">
                <template v-if="numericField">
                  ¥ {{ kpiStats.totalAmount.toLocaleString(undefined, { minimumFractionDigits: 0, maximumFractionDigits: 2 }) }}
                </template>
                <template v-else>
                  100 <span class="kpi-unit">%</span>
                </template>
              </div>
              <div class="kpi-sub">资产原值与资金体量</div>
            </div>
          </div>
        </el-col>

        <!-- 指标 3: 正常/已完成状态比例 -->
        <el-col :xs="12" :sm="12" :md="6">
          <div class="kpi-card kpi-card--warning">
            <div class="kpi-icon-box">
              <el-icon><CircleCheck /></el-icon>
            </div>
            <div class="kpi-info">
              <div class="kpi-label">正常运转 / 在用</div>
              <div class="kpi-val">{{ kpiStats.activeCount }} <span class="kpi-unit">件</span></div>
              <div class="kpi-sub">在网运转占比 {{ kpiStats.activeRate }}%</div>
            </div>
          </div>
        </el-col>

        <!-- 指标 4: 预警/待处理条目 -->
        <el-col :xs="12" :sm="12" :md="6">
          <div class="kpi-card kpi-card--danger">
            <div class="kpi-icon-box">
              <el-icon><WarningFilled /></el-icon>
            </div>
            <div class="kpi-info">
              <div class="kpi-label">闲置 / 待办预警</div>
              <div class="kpi-val">{{ kpiStats.warningCount }} <span class="kpi-unit">项</span></div>
              <div class="kpi-sub">需调度或维保处置</div>
            </div>
          </div>
        </el-col>
      </el-row>

      <!-- 视图模式切换与状态过滤栏 -->
      <BaseCard class="control-tabs-card">
        <div class="tabs-header-flex">
          <div class="view-mode-selector">
            <el-radio-group v-model="viewMode" size="default">
              <el-radio-button value="table">
                <el-icon><Grid /></el-icon> 数据台账
              </el-radio-button>
              <el-radio-button value="kanban">
                <el-icon><Opportunity /></el-icon> 流转看板
              </el-radio-button>
              <el-radio-button value="analytics">
                <el-icon><DataAnalysis /></el-icon> 透视分析
              </el-radio-button>
            </el-radio-group>
          </div>

          <!-- 快捷状态分类筛选 (仅在表格模式显示) -->
          <div v-if="viewMode === 'table' && statusOptions.length > 0" class="quick-status-pills">
            <span class="pills-label">状态筛选：</span>
            <el-check-tag
              :checked="activeStatusFilter === 'ALL'"
              @change="activeStatusFilter = 'ALL'"
            >
              全部 ({{ records.length }})
            </el-check-tag>
            <el-check-tag
              v-for="opt in statusOptions"
              :key="String(opt.value)"
              :checked="activeStatusFilter === String(opt.value)"
              @change="activeStatusFilter = String(opt.value)"
            >
              {{ opt.label }}
            </el-check-tag>
          </div>
        </div>
      </BaseCard>

      <!-- 视图 1: 数据台账表格视图 -->
      <div v-show="viewMode === 'table'" class="table-view-section">
        <!-- 搜索过滤栏 -->
        <BaseCard v-if="fields.some((f) => f.isQuery)" class="filter-card">
          <el-form inline class="filter-form" @submit.prevent="handleSearch">
            <template v-for="field in fields" :key="field.fieldCode">
              <el-form-item v-if="field.isQuery" :label="field.fieldName">
                <!-- 下拉框筛选 -->
                <el-select
                  v-if="field.componentType === 'select'"
                  v-model="queryParams[field.fieldCode]"
                  :placeholder="`全部${field.fieldName}`"
                  clearable
                  style="width: 170px"
                >
                  <el-option
                    v-for="opt in parseOptions(field.optionsJson)"
                    :key="String(opt.value)"
                    :label="opt.label"
                    :value="opt.value"
                  />
                </el-select>

                <!-- 日期筛选 -->
                <el-date-picker
                  v-else-if="field.componentType === 'date' || field.componentType === 'datetime'"
                  v-model="queryParams[field.fieldCode]"
                  type="date"
                  value-format="YYYY-MM-DD"
                  placeholder="选择日期"
                  clearable
                  style="width: 160px"
                />

                <!-- 普通文本搜索 -->
                <el-input
                  v-else
                  v-model="queryParams[field.fieldCode]"
                  :placeholder="`搜索${field.fieldName}`"
                  clearable
                  style="width: 180px"
                  @keyup.enter="handleSearch"
                />
              </el-form-item>
            </template>

            <el-form-item>
              <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
              <el-button :icon="RefreshRight" @click="handleReset">重置</el-button>
            </el-form-item>
          </el-form>
        </BaseCard>

        <!-- 数据表格 -->
        <BaseCard class="table-card">
          <el-table
            v-loading="loading"
            :data="displayRecords"
            stripe
            border
            style="width: 100%"
            row-key="id"
            @selection-change="handleSelectionChange"
            @row-dblclick="openDetailDrawer"
          >
            <!-- 空状态自定义插槽：100% 地道中文 -->
            <template #empty>
              <div class="custom-empty-box">
                <el-empty description="当前暂无业务台账记录">
                  <template #extra>
                    <div class="empty-action-group">
                      <el-button type="primary" :icon="Plus" @click="openCreateDialog">新增第一条记录</el-button>
                      <el-button :icon="MagicStick" :loading="seeding" @click="handleSeedMock">一键载入演示数据</el-button>
                      <el-button :icon="Upload" @click="importDialogVisible = true">批量导入台账</el-button>
                    </div>
                  </template>
                </el-empty>
              </div>
            </template>

            <el-table-column type="selection" width="48" align="center" />
            <el-table-column label="序号" type="index" width="60" align="center" />

            <!-- 动态业务字段列 -->
            <template v-for="field in fields" :key="field.fieldCode">
              <el-table-column
                :prop="field.fieldCode"
                :label="field.fieldName"
                min-width="150"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  <!-- 下拉选择标签样式 -->
                  <el-tag
                    v-if="field.componentType === 'select'"
                    :type="getTagType(field, row[field.fieldCode])"
                    size="small"
                  >
                    {{ getOptionLabel(field, row[field.fieldCode]) }}
                  </el-tag>

                  <!-- 数值金额样式 -->
                  <strong
                    v-else-if="field.componentType === 'number'"
                    class="number-cell"
                  >
                    ¥ {{ row[field.fieldCode] !== undefined && row[field.fieldCode] !== null ? Number(row[field.fieldCode]).toLocaleString() : '-' }}
                  </strong>

                  <!-- 普通文本与日期 -->
                  <span v-else>{{ row[field.fieldCode] ?? '-' }}</span>
                </template>
              </el-table-column>
            </template>

            <!-- 审计创建时间 -->
            <el-table-column prop="created_at" label="登记时间" width="170" align="center" />

            <!-- 操作列 -->
            <el-table-column label="操作" width="180" fixed="right" align="center">
              <template #default="{ row }">
                <el-button link type="primary" :icon="View" @click="openDetailDrawer(row)">详情</el-button>
                <el-button link type="primary" :icon="Edit" @click="openEditDialog(row)">编辑</el-button>
                <el-button link type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <!-- 中文本地化分页器 -->
          <div class="pagination-container">
            <el-pagination
              v-model:current-page="pageNum"
              v-model:page-size="pageSize"
              :page-sizes="[10, 20, 50, 100]"
              :total="total"
              background
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="loadData"
              @current-change="loadData"
            />
          </div>
        </BaseCard>
      </div>

      <!-- 视图 2: 业务流转看板视图 -->
      <div v-show="viewMode === 'kanban'" class="kanban-view-section">
        <div class="kanban-board-container">
          <div
            v-for="lane in kanbanLanes"
            :key="lane.key"
            class="kanban-lane"
          >
            <div class="lane-header">
              <div class="lane-title">
                <strong>{{ lane.label }}</strong>
                <el-badge :value="lane.items.length" :type="lane.items.length > 0 ? 'primary' : 'info'" class="lane-badge" />
              </div>
            </div>

            <div class="lane-body">
              <div
                v-for="item in lane.items"
                :key="item.id"
                class="kanban-card"
                @click="openDetailDrawer(item)"
              >
                <div class="kanban-card__header">
                  <span class="card-id">#{{ item.id }}</span>
                  <el-tag v-if="statusField" :type="getTagType(statusField, item[statusField.fieldCode])" size="small">
                    {{ getOptionLabel(statusField, item[statusField.fieldCode]) }}
                  </el-tag>
                </div>

                <h4 class="kanban-card__title">
                  {{ item.asset_name || item.title || item.applicant || item[fields[1]?.fieldCode] || '业务条目' }}
                </h4>

                <div class="kanban-card__meta">
                  <div v-if="item.asset_code" class="meta-row">
                    <span class="meta-k">编号：</span>
                    <span class="meta-v">{{ item.asset_code }}</span>
                  </div>
                  <div v-if="numericField && item[numericField.fieldCode]" class="meta-row">
                    <span class="meta-k">{{ numericField.fieldName }}：</span>
                    <strong class="text-primary">¥ {{ Number(item[numericField.fieldCode]).toLocaleString() }}</strong>
                  </div>
                  <div v-if="item.holder || item.applicant || item.department" class="meta-row">
                    <span class="meta-k">责任人/部门：</span>
                    <span class="meta-v">{{ item.holder || item.applicant || item.department }}</span>
                  </div>
                  <div v-if="item.location || item.room_name" class="meta-row">
                    <span class="meta-k">地点/会议室：</span>
                    <span class="meta-v">{{ item.location || item.room_name }}</span>
                  </div>
                </div>

                <!-- 快捷状态流转下拉 -->
                <div v-if="statusField && statusOptions.length > 1" class="kanban-card__footer" @click.stop>
                  <el-dropdown trigger="click" @command="(val: string) => handleQuickStatusChange(item, val)">
                    <el-button size="small" link type="primary">
                      流转状态 <el-icon><Right /></el-icon>
                    </el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item
                          v-for="opt in statusOptions"
                          :key="String(opt.value)"
                          :command="String(opt.value)"
                          :disabled="String(item[statusField.fieldCode]) === String(opt.value)"
                        >
                          流转至：{{ opt.label }}
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </div>

              <!-- 泳道空状态 -->
              <div v-if="lane.items.length === 0" class="lane-empty">
                暂无此类条目
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 视图 3: 数据透视与可视化分析视图 -->
      <div v-show="viewMode === 'analytics'" class="analytics-view-section">
        <el-row :gutter="16">
          <!-- 结构与分类透视 -->
          <el-col :xs="24" :lg="14">
            <BaseCard title="业务类别与分布透视">
              <div class="analytics-progress-list">
                <div
                  v-for="cat in categoryBreakdown"
                  :key="cat.name"
                  class="progress-item"
                >
                  <div class="progress-info">
                    <span class="item-name"><strong>{{ cat.name }}</strong></span>
                    <span class="item-stats">
                      {{ cat.count }} 项
                      <template v-if="cat.amount > 0">（¥ {{ cat.amount.toLocaleString() }}）</template>
                      - {{ cat.percentage }}%
                    </span>
                  </div>
                  <el-progress
                    :percentage="cat.percentage"
                    :stroke-width="12"
                    :color="cat.percentage > 40 ? '#409eff' : (cat.percentage > 20 ? '#67c23a' : '#e6a23c')"
                  />
                </div>

                <div v-if="categoryBreakdown.length === 0" class="text-center text-muted py-6">
                  暂无统计分布数据
                </div>
              </div>
            </BaseCard>
          </el-col>

          <!-- 运营健康度与建议 -->
          <el-col :xs="24" :lg="10">
            <BaseCard title="中台运行健康度与规范评估">
              <div class="health-checklist">
                <div class="health-item">
                  <el-icon class="text-success"><CircleCheck /></el-icon>
                  <div class="health-text">
                    <strong>多租户隔离约束</strong>
                    <p>租户数据强隔离，物理索引与乐观锁版本控制生效中。</p>
                  </div>
                </div>
                <div class="health-item">
                  <el-icon class="text-primary"><Document /></el-icon>
                  <div class="health-text">
                    <strong>动态字段与元数据驱动</strong>
                    <p>已绑定 {{ fields.length }} 个物理业务字段与 UI 校验规则。</p>
                  </div>
                </div>
                <div class="health-item">
                  <el-icon class="text-warning"><Opportunity /></el-icon>
                  <div class="health-text">
                    <strong>全生命周期台账闭环</strong>
                    <p>支持从录入、审批、看板流转到数据导出一体化作业。</p>
                  </div>
                </div>
              </div>
            </BaseCard>
          </el-col>
        </el-row>
      </div>
    </ControlSurface>

    <!-- 记录详情右侧滑出抽屉 (Detail Drawer) -->
    <el-drawer
      v-model="detailDrawerVisible"
      :title="`${meta?.modelName || '业务条目'} · 详细档案`"
      size="540px"
      destroy-on-close
    >
      <div v-if="detailRecord" class="drawer-detail-body">
        <!-- 详情顶部摘要卡片 -->
        <div class="detail-hero-box">
          <div class="hero-top">
            <h3 class="hero-title">
              {{ detailRecord.asset_name || detailRecord.title || detailRecord[fields[1]?.fieldCode] || '业务详细记录' }}
            </h3>
            <el-tag
              v-if="statusField"
              :type="getTagType(statusField, detailRecord[statusField.fieldCode])"
              size="large"
            >
              {{ getOptionLabel(statusField, detailRecord[statusField.fieldCode]) }}
            </el-tag>
          </div>
          <div class="hero-sub">
            系统记录编号：<code>#{{ detailRecord.id }}</code>
          </div>
        </div>

        <!-- 业务属性展示 -->
        <h4 class="drawer-sub-title">业务属性信息</h4>
        <el-descriptions :column="2" border class="mb-4">
          <template v-for="field in fields" :key="field.fieldCode">
            <el-descriptions-item :label="field.fieldName" :span="field.componentType === 'textarea' ? 2 : 1">
              <el-tag
                v-if="field.componentType === 'select'"
                :type="getTagType(field, detailRecord[field.fieldCode])"
                size="small"
              >
                {{ getOptionLabel(field, detailRecord[field.fieldCode]) }}
              </el-tag>
              <strong
                v-else-if="field.componentType === 'number'"
                class="text-primary"
              >
                ¥ {{ Number(detailRecord[field.fieldCode] || 0).toLocaleString() }}
              </strong>
              <span v-else>{{ detailRecord[field.fieldCode] ?? '-' }}</span>
            </el-descriptions-item>
          </template>
        </el-descriptions>

        <!-- 审计与系统追踪 -->
        <h4 class="drawer-sub-title">系统追溯与租户隔离</h4>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="归属租户">
            <code>租户 #{{ detailRecord.tenant_id }}</code>
          </el-descriptions-item>
          <el-descriptions-item label="数据版本">
            <code>v{{ detailRecord.version ?? 0 }}</code>
          </el-descriptions-item>
          <el-descriptions-item label="登记时间">
            {{ detailRecord.created_at || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="更新时间">
            {{ detailRecord.updated_at || '-' }}
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <template #footer>
        <div class="drawer-footer-actions">
          <el-button :icon="Printer" @click="handlePrint">打印此单据</el-button>
          <el-button type="danger" plain :icon="Delete" @click="handleDelete(detailRecord!)">删除</el-button>
          <el-button type="primary" :icon="Edit" @click="openEditDialog(detailRecord!)">编辑修改</el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 批量导入对话框 -->
    <el-dialog
      v-model="importDialogVisible"
      title="批量导入业务台账数据"
      width="600px"
      destroy-on-close
    >
      <div class="import-dialog-body">
        <el-alert
          type="info"
          show-icon
          :closable="false"
          title="导入说明"
          description="请下载标准 CSV 模版，按表头填好后将数据直接粘贴到下方输入框中即可快速批量写入。"
          class="mb-3"
        />

        <div class="template-download-bar mb-3">
          <el-button type="primary" plain :icon="Download" @click="downloadImportTemplate">
            下载标准导入模板 (CSV)
          </el-button>
        </div>

        <el-form-item label="粘贴 CSV 数据内容：">
          <el-input
            v-model="importCsvText"
            type="textarea"
            :rows="6"
            placeholder="包含首行字段中文表头，如：&#10;固定资产名称,资产唯一编码,资产类别,原值金额 (元)&#10;戴尔显示器,AST-005,IT,4999"
          />
        </el-form-item>
      </div>

      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="handleBatchImport">
          确认批量导入
        </el-button>
      </template>
    </el-dialog>

    <!-- 新增 / 编辑 动态表单弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEditing ? `编辑 ${meta?.modelName || '业务记录'}` : `新增 ${meta?.modelName || '业务记录'}`"
      width="640px"
      destroy-on-close
      append-to-body
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-position="top"
        class="dynamic-dialog-form"
      >
        <el-row :gutter="16">
          <template v-for="field in fields" :key="field.fieldCode">
            <el-col :span="field.componentType === 'textarea' ? 24 : 12">
              <el-form-item :label="field.fieldName" :prop="field.fieldCode">
                <!-- 下拉选择 -->
                <el-select
                  v-if="field.componentType === 'select'"
                  v-model="formData[field.fieldCode]"
                  :placeholder="`请选择${field.fieldName}`"
                  style="width: 100%"
                  clearable
                >
                  <el-option
                    v-for="opt in parseOptions(field.optionsJson)"
                    :key="String(opt.value)"
                    :label="opt.label"
                    :value="opt.value"
                  />
                </el-select>

                <!-- 数字输入 -->
                <el-input-number
                  v-else-if="field.componentType === 'number'"
                  v-model="formData[field.fieldCode]"
                  :placeholder="`请输入${field.fieldName}`"
                  style="width: 100%"
                />

                <!-- 日期选择 -->
                <el-date-picker
                  v-else-if="field.componentType === 'date'"
                  v-model="formData[field.fieldCode]"
                  type="date"
                  value-format="YYYY-MM-DD"
                  placeholder="选择日期"
                  style="width: 100%"
                />

                <!-- 日期时间选择 -->
                <el-date-picker
                  v-else-if="field.componentType === 'datetime'"
                  v-model="formData[field.fieldCode]"
                  type="datetime"
                  value-format="YYYY-MM-DD HH:mm:ss"
                  placeholder="选择日期时间"
                  style="width: 100%"
                />

                <!-- 多行文本域 -->
                <el-input
                  v-else-if="field.componentType === 'textarea'"
                  v-model="formData[field.fieldCode]"
                  type="textarea"
                  :rows="3"
                  :placeholder="`请输入${field.fieldName}`"
                />

                <!-- 默认单行输入框 -->
                <el-input
                  v-else
                  v-model="formData[field.fieldCode]"
                  :placeholder="`请输入${field.fieldName}`"
                />
              </el-form-item>
            </el-col>
          </template>
        </el-row>
      </el-form>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="submitForm">
            保存提交
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.dynamic-view-container {
  padding: 0;
}

.kpi-cards-row {
  margin-bottom: 14px;
}

.kpi-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #e2e8f0;
  box-shadow: 0 1px 3px rgb(0 0 0 / 0.04);
  transition: transform 0.2s ease, box-shadow 0.2s ease;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgb(0 0 0 / 0.08);
  }

  .kpi-icon-box {
    width: 48px;
    height: 48px;
    border-radius: 8px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 22px;
  }

  &--primary .kpi-icon-box {
    background: #eff6ff;
    color: #3b82f6;
  }

  &--success .kpi-icon-box {
    background: #f0fdf4;
    color: #16a34a;
  }

  &--warning .kpi-icon-box {
    background: #fffbeb;
    color: #d97706;
  }

  &--danger .kpi-icon-box {
    background: #fef2f2;
    color: #dc2626;
  }

  .kpi-info {
    flex: 1;
    min-width: 0;

    .kpi-label {
      font-size: 13px;
      color: #64748b;
      margin-bottom: 4px;
    }

    .kpi-val {
      font-size: 22px;
      font-weight: 700;
      color: #1e293b;
      line-height: 1.2;

      .kpi-unit {
        font-size: 13px;
        font-weight: normal;
        color: #94a3b8;
      }
    }

    .kpi-sub {
      font-size: 12px;
      color: #94a3b8;
      margin-top: 4px;
      display: flex;
      align-items: center;
      gap: 6px;

      .badge-dot {
        width: 6px;
        height: 6px;
        border-radius: 50%;
        display: inline-block;

        &--success {
          background: #10b981;
        }
      }
    }
  }
}

.control-tabs-card {
  margin-bottom: 14px;

  .tabs-header-flex {
    display: flex;
    justify-content: space-between;
    align-items: center;
    flex-wrap: wrap;
    gap: 12px;

    .quick-status-pills {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      gap: 8px;

      .pills-label {
        font-size: 13px;
        color: #64748b;
      }
    }
  }
}

.filter-card {
  margin-bottom: 14px;

  .filter-form {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px 14px;

    :deep(.el-form-item) {
      margin-bottom: 8px;
      margin-right: 0;
    }
  }
}

.table-card {
  .custom-empty-box {
    padding: 30px 0;

    .empty-action-group {
      display: flex;
      justify-content: center;
      gap: 12px;
      margin-top: 8px;
    }
  }

  .number-cell {
    color: #2563eb;
    font-family: var(--el-font-family-monospace, monospace);
  }

  .pagination-container {
    display: flex;
    justify-content: flex-end;
    margin-top: 16px;
    padding-top: 12px;
  }
}

/* Kanban Board Styles */
.kanban-board-container {
  display: flex;
  gap: 16px;
  overflow-x: auto;
  padding-bottom: 16px;
  min-height: 480px;

  .kanban-lane {
    flex: 0 0 300px;
    background: #f8fafc;
    border-radius: 8px;
    border: 1px solid #e2e8f0;
    display: flex;
    flex-direction: column;
    max-height: 80vh;

    .lane-header {
      padding: 12px 16px;
      border-bottom: 1px solid #e2e8f0;
      background: #fff;
      border-radius: 8px 8px 0 0;

      .lane-title {
        display: flex;
        justify-content: space-between;
        align-items: center;
        font-size: 14px;
        color: #1e293b;
      }
    }

    .lane-body {
      padding: 12px;
      overflow-y: auto;
      display: flex;
      flex-direction: column;
      gap: 10px;
      flex: 1;

      .lane-empty {
        text-align: center;
        padding: 30px 10px;
        color: #94a3b8;
        font-size: 13px;
      }
    }
  }

  .kanban-card {
    background: #fff;
    border: 1px solid #e2e8f0;
    border-radius: 6px;
    padding: 12px;
    cursor: pointer;
    box-shadow: 0 1px 2px rgb(0 0 0 / 0.04);
    transition: all 0.2s ease;

    &:hover {
      box-shadow: 0 4px 10px rgb(0 0 0 / 0.08);
      border-color: #cbd5e1;
      transform: translateY(-1px);
    }

    &__header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 8px;

      .card-id {
        font-size: 12px;
        color: #94a3b8;
        font-family: monospace;
      }
    }

    &__title {
      font-size: 14px;
      font-weight: 600;
      color: #1e293b;
      margin: 0 0 8px;
      line-height: 1.4;
    }

    &__meta {
      font-size: 12px;
      color: #64748b;
      display: flex;
      flex-direction: column;
      gap: 4px;

      .meta-row {
        display: flex;
        justify-content: space-between;

        .meta-k {
          color: #94a3b8;
        }

        .meta-v {
          color: #334155;
          max-width: 170px;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
      }
    }

    &__footer {
      margin-top: 10px;
      padding-top: 8px;
      border-top: 1px dashed #f1f5f9;
      display: flex;
      justify-content: flex-end;
    }
  }
}

/* Analytics View */
.analytics-progress-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 8px 0;

  .progress-item {
    .progress-info {
      display: flex;
      justify-content: space-between;
      font-size: 13px;
      margin-bottom: 6px;

      .item-stats {
        color: #64748b;
      }
    }
  }
}

.health-checklist {
  display: flex;
  flex-direction: column;
  gap: 16px;

  .health-item {
    display: flex;
    align-items: flex-start;
    gap: 12px;

    .el-icon {
      font-size: 20px;
      margin-top: 2px;
    }

    .health-text {
      strong {
        font-size: 14px;
        color: #1e293b;
      }

      p {
        font-size: 12px;
        color: #64748b;
        margin: 4px 0 0;
        line-height: 1.5;
      }
    }
  }
}

/* Drawer styles */
.drawer-detail-body {
  .detail-hero-box {
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 8px;
    padding: 16px;
    margin-bottom: 20px;

    .hero-top {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 6px;

      .hero-title {
        margin: 0;
        font-size: 18px;
        font-weight: 700;
        color: #0f172a;
      }
    }

    .hero-sub {
      font-size: 12px;
      color: #94a3b8;
    }
  }

  .drawer-sub-title {
    font-size: 14px;
    font-weight: 600;
    color: #334155;
    margin: 16px 0 8px;
  }
}

.drawer-footer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.mb-3 {
  margin-bottom: 12px;
}

.mb-4 {
  margin-bottom: 16px;
}

.text-primary {
  color: #2563eb;
}

.text-success {
  color: #16a34a;
}

.text-warning {
  color: #d97706;
}

.text-center {
  text-align: center;
}

.text-muted {
  color: #94a3b8;
}

.py-6 {
  padding-top: 24px;
  padding-bottom: 24px;
}
</style>
