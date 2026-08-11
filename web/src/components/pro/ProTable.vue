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
    emptyDescription: "调整筛选条件或新增记录。",
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
          <h3>{{ title }}</h3>
          <span>{{ total }} 条</span>
        </div>
        <div class="pro-table__actions">
          <el-button v-if="data.length" plain @click="exportRows(data, 'current')">导出</el-button>
          <slot name="toolbar" />
        </div>
      </div>
    </template>

    <div v-if="props.selectable && selectedRows.length" class="pro-table__bulkbar">
      <div class="pro-table__bulkcopy">
        <strong>已选 {{ selectedRows.length }} 条</strong>
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
  min-height: 32px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.pro-table--compact .pro-table__header h3 {
  font-size: 14px;
}

.pro-table__headline {
  min-width: 0;
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.pro-table__header h3 {
  margin: 0;
  font-size: 15px;
}

.pro-table__headline > span {
  color: var(--sb-text-tertiary);
  font-size: 12px;
}

.pro-table__search {
  margin-bottom: 12px;
}

.pro-table__bulkbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
  padding: 9px 12px;
  border: 1px solid rgb(30 94 255 / 0.12);
  border-radius: 5px;
  background: #f5f8ff;
}

.pro-table__bulkcopy {
  strong {
    color: var(--sb-text-primary);
    font-size: 13px;
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
}

.pro-table__grid :deep(.el-table td),
.pro-table__grid :deep(.el-table th.el-table__cell) {
  padding: 10px 0;
}

.pro-table__empty {
  display: grid;
  justify-items: center;
  gap: 6px;
  padding: 32px 16px;

  strong {
    font-size: 15px;
  }

  p {
    margin: 0;
    max-width: 360px;
    color: var(--sb-text-secondary);
    font-size: 12px;
    line-height: 1.5;
    text-align: center;
  }
}

@media (max-width: 960px) {
  .pro-table__header {
    flex-direction: column;
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
