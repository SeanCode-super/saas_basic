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
  Search,
  Back,
  Edit,
  Document
} from "@element-plus/icons-vue";
import ControlSurface from "@/components/platform/ControlSurface.vue";
import BaseCard from "@/components/base/BaseCard.vue";
import {
  fetchDynamicMeta,
  fetchDynamicPage,
  createDynamicRecord,
  updateDynamicRecord,
  deleteDynamicRecord,
  batchDeleteDynamicRecords
} from "@/api/modules/codegen/ai-builder";
import type { DynamicModelItem, DynamicFieldItem } from "@/api/modules/codegen/ai-builder";

const route = useRoute();
const router = useRouter();

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
const submitting = ref(false);
const meta = ref<DynamicModelItem | null>(null);
const fields = ref<DynamicFieldItem[]>([]);
const records = ref<Record<string, unknown>[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(10);
const selectedRows = ref<Record<string, unknown>[]>([]);

// Query filter state
const queryParams = reactive<Record<string, any>>({});

// Dialog / Form state
const dialogVisible = ref(false);
const isEditing = ref(false);
const formRef = ref<FormInstance>();
const formData = reactive<Record<string, any>>({});

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
    // fallback comma separated
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
  if (str.includes("成功") || str.includes("已完成") || str.includes("通过") || str.includes("良好") || str.includes("已发放")) return "success";
  if (str.includes("待") || str.includes("中") || str.includes("预定") || str.includes("跟进")) return "warning";
  if (str.includes("紧急") || str.includes("驳回") || str.includes("失败") || str.includes("停用") || str.includes("报废")) return "danger";
  return "primary";
}

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
      // Initialize query params
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
    // Filter out undefined query params
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

function handleSearch() {
  pageNum.value = 1;
  loadData();
}

function handleReset() {
  for (const k of Object.keys(queryParams)) {
    queryParams[k] = undefined;
  }
  pageNum.value = 1;
  loadData();
}

function handleSelectionChange(rows: Record<string, unknown>[]) {
  selectedRows.value = rows;
}

// Open create dialog
function openCreateDialog() {
  isEditing.value = false;
  for (const k of Object.keys(formData)) {
    delete formData[k];
  }
  // Initialize default values
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
function openEditDialog(row: Record<string, unknown>) {
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
      await loadData();
    } catch (err: unknown) {
      ElMessage.error(err instanceof Error ? err.message : "保存数据失败");
    } finally {
      submitting.value = false;
    }
  });
}

// Delete single record
async function handleDelete(row: Record<string, unknown>) {
  const id = Number(row.id);
  if (!id) return;
  try {
    await ElMessageBox.confirm("确定删除该条业务数据吗？此操作不可逆！", "删除确认", {
      type: "warning",
      confirmButtonText: "确定删除",
      cancelButtonText: "取消"
    });
    await deleteDynamicRecord(modelCode.value, id);
    ElMessage.success("删除成功");
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

// Export CSV
function exportCsv() {
  if (records.value.length === 0) {
    ElMessage.warning("暂无数据可导出");
    return;
  }
  const headers = ["ID", ...fields.value.map((f) => f.fieldName), "创建时间"];
  const rows = records.value.map((row) => [
    row.id ?? "",
    ...fields.value.map((f) => {
      const val = row[f.fieldCode];
      return val !== undefined && val !== null ? `"${String(val).replace(/"/g, '""')}"` : '""';
    }),
    row.created_at ?? ""
  ]);
  const csvContent = "\uFEFF" + [headers.join(","), ...rows.map((r) => r.join(","))].join("\n");
  const blob = new Blob([csvContent], { type: "text/csv;charset=utf-8;" });
  const link = document.createElement("a");
  link.href = URL.createObjectURL(blob);
  link.download = `${meta.value?.modelName || modelCode.value}-export.csv`;
  link.click();
  URL.revokeObjectURL(link.href);
  ElMessage.success("数据导出成功");
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
      :eyebrow="`AI 动态中台 · ${meta?.category || '业务应用'}`"
      :title="meta?.modelName || '动态业务中心'"
      :description="meta?.description || '由 AI 智能应用工坊自然语言驱动生成的企业级多租户业务中台。'"
    >
      <template #actions>
        <el-button :icon="Back" @click="router.push('/codegen/ai-builder')">返回工坊</el-button>
        <el-button :icon="RefreshRight" :loading="loading" @click="loadData">刷新</el-button>
        <el-button :icon="Download" @click="exportCsv">导出 CSV</el-button>
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

      <!-- 顶部过滤筛选卡片 (如果存在可筛选字段) -->
      <BaseCard v-if="fields.some((f) => f.isQuery)" class="filter-card">
        <el-form inline class="filter-form" @submit.prevent="handleSearch">
          <template v-for="field in fields" :key="field.fieldCode">
            <el-form-item v-if="field.isQuery" :label="field.fieldName">
              <!-- 下拉框筛选 -->
              <el-select
                v-if="field.componentType === 'select'"
                v-model="queryParams[field.fieldCode]"
                :placeholder="`选择${field.fieldName}`"
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
                style="width: 170px"
              />

              <!-- 普通文本模糊搜索 -->
              <el-input
                v-else
                v-model="queryParams[field.fieldCode]"
                :placeholder="`输入${field.fieldName}搜索`"
                clearable
                style="width: 190px"
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

      <!-- 动态数据表格 -->
      <BaseCard class="table-card">
        <div class="table-summary-bar">
          <div class="summary-left">
            <el-tag effect="plain" type="info" class="meta-tag">
              数据表: {{ meta?.tableName || `biz_dyn_${modelCode}` }}
            </el-tag>
            <el-tag effect="plain" type="success" class="meta-tag">多租户隔离已生效</el-tag>
            <span class="total-text">共 <strong>{{ total }}</strong> 条记录</span>
          </div>
        </div>

        <el-table
          v-loading="loading"
          :data="records"
          stripe
          border
          style="width: 100%"
          row-key="id"
          @selection-change="handleSelectionChange"
        >
          <el-table-column type="selection" width="48" align="center" />
          <el-table-column prop="id" label="ID" width="70" align="center" />

          <!-- 动态业务字段列 -->
          <template v-for="field in fields" :key="field.fieldCode">
            <el-table-column
              :prop="field.fieldCode"
              :label="field.fieldName"
              min-width="140"
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
                  {{ row[field.fieldCode] !== undefined && row[field.fieldCode] !== null ? Number(row[field.fieldCode]).toLocaleString() : '-' }}
                </strong>

                <!-- 普通文本与日期 -->
                <span v-else>{{ row[field.fieldCode] ?? '-' }}</span>
              </template>
            </el-table-column>
          </template>

          <!-- 审计创建时间 -->
          <el-table-column prop="created_at" label="创建时间" width="170" align="center" />

          <!-- 操作列 -->
          <el-table-column label="操作" width="140" fixed="right" align="center">
            <template #default="{ row }">
              <el-button link type="primary" :icon="Edit" @click="openEditDialog(row)">编辑</el-button>
              <el-button link type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 分页 -->
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
    </ControlSurface>

    <!-- 新增 / 编辑 动态表单弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEditing ? `编辑 ${meta?.modelName || '业务数据'}` : `新增 ${meta?.modelName || '业务数据'}`"
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

.filter-card {
  margin-bottom: 16px;

  .filter-form {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px 16px;

    :deep(.el-form-item) {
      margin-bottom: 8px;
      margin-right: 0;
    }
  }
}

.table-card {
  .table-summary-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 12px;

    .summary-left {
      display: flex;
      align-items: center;
      gap: 10px;

      .meta-tag {
        font-family: var(--el-font-family-monospace, monospace);
        font-size: 12px;
      }

      .total-text {
        font-size: 13px;
        color: var(--el-text-color-secondary);

        strong {
          color: var(--el-color-primary);
        }
      }
    }
  }

  .number-cell {
    color: var(--el-color-primary);
    font-family: var(--el-font-family-monospace, monospace);
  }

  .pagination-container {
    display: flex;
    justify-content: flex-end;
    margin-top: 16px;
    padding-top: 12px;
  }
}

.dynamic-dialog-form {
  padding: 8px 4px;
}
</style>
