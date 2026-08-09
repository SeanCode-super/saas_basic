<script setup lang="ts">
import { computed, ref } from "vue";
import type { TableInstance } from "element-plus";
import BaseCard from "@/components/base/BaseCard.vue";

export interface ProTableColumn {
  prop: string;
  label: string;
  minWidth?: number;
  slot?: string;
  formatter?: (row: Record<string, unknown>) => string;
}

const props = withDefaults(
  defineProps<{
    title: string;
    subtitle?: string;
    columns: ProTableColumn[];
    data: object[];
    loading?: boolean;
    count?: number;
    selectable?: boolean;
    rowKey?: string;
    emptyTitle?: string;
    emptyDescription?: string;
    compact?: boolean;
  }>(),
  {
    selectable: false,
    rowKey: "id",
    emptyTitle: "当前暂无记录",
    emptyDescription: "先完善筛选条件或新增主数据，统一台账会在这里持续沉淀。",
    compact: false
  }
);

const emit = defineEmits<{
  selectionChange: [object[]];
  rowClick: [object];
}>();

const total = computed(() => props.count ?? props.data.length);
const selectedRows = ref<object[]>([]);
const tableRef = ref<TableInstance>();

function handleSelectionChange(rows: object[]) {
  selectedRows.value = rows;
  emit("selectionChange", rows);
}

function handleRowClick(row: object) {
  emit("rowClick", row);
}

function clearSelection() {
  tableRef.value?.clearSelection();
}

function resolveRowKey(row: Record<string, unknown>) {
  return String(row[props.rowKey] ?? "");
}

function escapeCsvCell(value: unknown) {
  const source = value == null ? "" : String(value);
  const normalized = source.replace(/"/g, '""').replace(/\n/g, " ").replace(/\r/g, " ");
  return `"${normalized}"`;
}

function exportRows(rows: object[], mode: "selected" | "current") {
  if (!rows.length) {
    return;
  }

  const header = props.columns.map((column) => escapeCsvCell(column.label)).join(",");
  const body = rows
    .map((row) =>
      props.columns
        .map((column) => {
          const raw = column.formatter ? column.formatter(row as Record<string, unknown>) : (row as Record<string, unknown>)[column.prop];
          return escapeCsvCell(raw);
        })
        .join(",")
    )
    .join("\n");

  const csv = [header, body].join("\n");
  const blob = new Blob(["\uFEFF", csv], { type: "text/csv;charset=utf-8;" });
  const link = document.createElement("a");
  const stamp = new Date().toISOString().slice(0, 19).replace(/:/g, "-");
  link.href = URL.createObjectURL(blob);
  link.download = `${props.title}-${mode === "selected" ? "selected" : "all"}-${stamp}.csv`;
  link.click();
  URL.revokeObjectURL(link.href);
}
</script>

<template>
  <BaseCard class="pro-table" :class="{ 'pro-table--compact': compact }">
    <template #header>
      <div class="pro-table__header">
        <div class="pro-table__headline">
          <span class="pro-table__eyebrow">台账表格</span>
          <h3>{{ title }}</h3>
          <p>{{ subtitle ?? "面向高密度企业控制台场景的统一表格基座。" }}</p>
        </div>
        <div class="pro-table__actions">
          <el-button v-if="data.length" plain @click="exportRows(data, 'current')">导出当前视图</el-button>
          <slot name="toolbar" />
        </div>
      </div>
    </template>

    <div v-if="!compact" class="pro-table__summary">
      <div class="pro-table__signal">
        <span>当前记录</span>
        <strong>{{ total }}</strong>
      </div>
      <div class="pro-table__summary-copy">
        <span>统一口径</span>
        <p>所有主数据、策略和注册项必须进入同一控制台台账，不再散落到业务页面。</p>
      </div>
    </div>

    <div v-if="props.selectable && selectedRows.length" class="pro-table__bulkbar">
      <div class="pro-table__bulkcopy">
        <span>批量选择</span>
        <strong>已选 {{ selectedRows.length }} 条记录</strong>
      </div>
      <div class="pro-table__bulkactions">
        <el-button plain @click="exportRows(selectedRows, 'selected')">导出所选</el-button>
        <slot name="bulkActions" :rows="selectedRows" :clear-selection="clearSelection" />
        <el-button @click="clearSelection">取消选择</el-button>
      </div>
    </div>

    <div v-if="$slots.search" class="pro-table__search">
      <slot name="search" />
    </div>

    <el-table
      ref="tableRef"
      :data="data"
      :loading="loading"
      :row-key="resolveRowKey"
      stripe
      class="pro-table__grid"
      @selection-change="handleSelectionChange"
      @row-click="handleRowClick"
    >
      <el-table-column v-if="props.selectable" type="selection" width="48" reserve-selection />
      <el-table-column
        v-for="column in columns"
        :key="column.prop"
        :prop="column.prop"
        :label="column.label"
        :min-width="column.minWidth ?? 120"
      >
        <template v-if="column.slot" #default="scope">
          <slot :name="column.slot" v-bind="scope" />
        </template>
        <template v-else-if="column.formatter" #default="scope">
          {{ column.formatter?.(scope.row) }}
        </template>
      </el-table-column>
      <template #empty>
        <slot name="empty">
          <div class="pro-table__empty">
            <span class="pro-table__empty-eyebrow">暂无数据</span>
            <strong>{{ emptyTitle }}</strong>
            <p>{{ emptyDescription }}</p>
          </div>
        </slot>
      </template>
    </el-table>
  </BaseCard>
</template>

<style scoped lang="scss">
.pro-table__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.pro-table--compact .pro-table__header {
  margin-bottom: 12px;
}

.pro-table--compact .pro-table__eyebrow {
  margin-bottom: 4px;
}

.pro-table--compact .pro-table__header h3 {
  font-size: 16px;
}

.pro-table--compact .pro-table__header p {
  margin-top: 4px;
  font-size: 12px;
}

.pro-table__headline {
  min-width: 0;
}

.pro-table__eyebrow {
  display: inline-flex;
  margin-bottom: 8px;
  color: var(--sb-primary-strong);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.18em;
}

.pro-table__header h3 {
  margin: 0;
  font-size: 18px;
}

.pro-table__header p {
  margin: 6px 0 0;
  color: var(--sb-text-secondary);
  line-height: 1.7;
}

.pro-table__summary {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  gap: 14px;
  margin-bottom: 16px;
}

.pro-table__signal,
.pro-table__summary-copy {
  padding: 16px 18px;
  border-radius: 18px;
  background: rgb(15 98 254 / 0.05);
}

.pro-table__signal {
  span,
  strong {
    display: block;
  }

  span {
    color: var(--sb-text-secondary);
    font-size: 12px;
  }

  strong {
    margin-top: 10px;
    font-size: 28px;
    line-height: 1;
    color: var(--sb-text-primary);
  }
}

.pro-table__summary-copy {
  span {
    display: block;
    color: var(--sb-text-secondary);
    font-size: 12px;
    font-weight: 600;
  }

  p {
    margin: 10px 0 0;
    color: var(--sb-text-primary);
    line-height: 1.7;
  }
}

.pro-table__search {
  margin-bottom: 16px;
}

.pro-table__bulkbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
  padding: 14px 16px;
  border: 1px solid rgb(30 94 255 / 0.12);
  border-radius: 16px;
  background: linear-gradient(180deg, rgb(245 249 255 / 0.96), rgb(239 246 255 / 0.9));
}

.pro-table__bulkcopy {
  span,
  strong {
    display: block;
  }

  span {
    color: var(--sb-text-secondary);
    font-size: 12px;
  }

  strong {
    margin-top: 4px;
    color: var(--sb-text-primary);
    font-size: 14px;
  }
}

.pro-table__bulkactions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.pro-table__actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.pro-table__grid :deep(.el-table__header th) {
  color: var(--sb-text-secondary);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.pro-table__grid :deep(.el-table td),
.pro-table__grid :deep(.el-table th.el-table__cell) {
  padding: 14px 0;
}

.pro-table__empty {
  display: grid;
  justify-items: center;
  gap: 8px;
  padding: 40px 16px;

  strong {
    font-size: 18px;
  }

  p {
    margin: 0;
    max-width: 360px;
    color: var(--sb-text-secondary);
    line-height: 1.7;
    text-align: center;
  }
}

.pro-table__empty-eyebrow {
  color: var(--sb-primary-strong);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.16em;
}

@media (max-width: 960px) {
  .pro-table__header {
    flex-direction: column;
  }

  .pro-table__summary {
    grid-template-columns: 1fr;
  }

  .pro-table__actions {
    width: 100%;
    justify-content: flex-start;
  }

  .pro-table__bulkbar {
    flex-direction: column;
    align-items: flex-start;
  }

  .pro-table__bulkactions {
    width: 100%;
    justify-content: flex-start;
  }
}
</style>
