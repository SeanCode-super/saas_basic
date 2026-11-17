<script setup lang="ts">
import { computed, ref, watch } from "vue";
import type { TableInstance } from "element-plus";
import { Download } from "@element-plus/icons-vue";
import BaseCard from "@/components/base/BaseCard.vue";

export interface ProTableColumn {
  prop: string;
  label: string;
  minWidth?: number;
  slot?: string;
  formatter?: (row: Record<string, unknown>) => string;
  fixed?: boolean | "left" | "right";
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
    pagination?: boolean;
    pageSize?: number;
  }>(),
  {
    selectable: false,
    rowKey: "id",
    emptyTitle: "当前暂无记录",
    emptyDescription: "调整筛选条件或新增记录。",
    compact: false,
    pagination: true,
    pageSize: 10
  }
);

const emit = defineEmits<{
  selectionChange: [object[]];
  rowClick: [object];
  pageChange: [number];
  sizeChange: [number];
}>();

const currentPage = ref(1);
const currentPageSize = ref(props.pageSize ?? 10);

const total = computed(() => props.count ?? props.data.length);

const paginatedData = computed(() => {
  if (!props.pagination) return props.data;
  // If count is explicitly provided and different from data.length, it's server-side pagination, don't slice
  if (props.count !== undefined && props.count !== props.data.length) {
    return props.data;
  }
  const start = (currentPage.value - 1) * currentPageSize.value;
  return props.data.slice(start, start + currentPageSize.value);
});

watch(
  () => props.data.length,
  () => {
    const maxPage = Math.max(1, Math.ceil(total.value / currentPageSize.value));
    if (currentPage.value > maxPage) {
      currentPage.value = 1;
    }
  }
);

const selectedRows = ref<object[]>([]);
const tableRef = ref<TableInstance>();

function handleSelectionChange(rows: object[]) {
  selectedRows.value = rows;
  emit("selectionChange", rows);
}

function handleRowClick(row: object) {
  emit("rowClick", row);
}

function handlePageChange(page: number) {
  currentPage.value = page;
  emit("pageChange", page);
}

function handleSizeChange(size: number) {
  currentPageSize.value = size;
  currentPage.value = 1;
  emit("sizeChange", size);
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
          <el-tooltip v-if="data.length" content="导出当前数据" placement="top">
            <el-button :icon="Download" aria-label="导出当前数据" @click="exportRows(data, 'current')" />
          </el-tooltip>
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
      :data="paginatedData"
      :loading="loading"
      :row-key="resolveRowKey"
      class="pro-table__grid"
      stripe
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
        :fixed="column.fixed"
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

    <!-- 内置通用分页器 -->
    <div v-if="pagination && total > 0" class="pro-table__pagination">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="currentPageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </div>
  </BaseCard>
</template>

<style scoped lang="scss">
.pro-table {
  min-width: 0;
  max-width: 100%;
}

.pro-table :deep(.el-card__body) {
  padding: 0;
  overflow-x: auto;
}

.pro-table__grid {
  width: 100%;
  min-width: 0;
}

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
  margin: 0;
  padding: 12px 14px;
  border-bottom: 1px solid var(--sb-border-color);
}

.pro-table__bulkbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin: 12px 14px;
  padding: 9px 12px;
  border: 1px solid rgba(37, 99, 235, 0.25);
  border-radius: var(--sb-radius-sm, 6px);
  background: rgba(239, 246, 255, 0.65);
  backdrop-filter: blur(14px);
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

.pro-table__grid :deep(.el-table) {
  background: transparent !important;
  border: none !important;
  box-shadow: none !important;
  backdrop-filter: none !important;
  -webkit-backdrop-filter: none !important;
}

.pro-table__grid :deep(.el-table__header th) {
  height: 42px;
  background: rgba(248, 250, 252, 0.98) !important;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
  color: var(--sb-text-primary) !important;
  font-size: 13px;
  font-weight: 650;
  border-bottom: 1.5px solid #e2e8f0 !important;
  border-right: none !important;
  border-left: none !important;
}

.pro-table__grid :deep(.el-table__header th.el-table-fixed-column--right),
.pro-table__grid :deep(.el-table__header th.el-table-fixed-column--left) {
  background: rgba(248, 250, 252, 0.98) !important;
  backdrop-filter: none !important;
  -webkit-backdrop-filter: none !important;
  z-index: 5 !important;
  box-shadow: none !important;
  border-right: none !important;
  border-left: none !important;
}

.pro-table__grid :deep(.el-table-fixed-column--right::before),
.pro-table__grid :deep(.el-table-fixed-column--right::after),
.pro-table__grid :deep(.el-table-fixed-column--left::before),
.pro-table__grid :deep(.el-table-fixed-column--left::after) {
  display: none !important;
  content: none !important;
  box-shadow: none !important;
}

.pro-table__grid :deep(.el-table__body tr:nth-child(odd) > td.el-table-fixed-column--right),
.pro-table__grid :deep(.el-table__body tr:nth-child(odd) > td.el-table-fixed-column--left) {
  background: rgba(255, 255, 255, 0.98) !important;
  backdrop-filter: none !important;
  -webkit-backdrop-filter: none !important;
  box-shadow: none !important;
  border-right: none !important;
  border-left: none !important;
}

.pro-table__grid :deep(.el-table__body tr:nth-child(even) > td.el-table-fixed-column--right),
.pro-table__grid :deep(.el-table__body tr:nth-child(even) > td.el-table-fixed-column--left) {
  background: rgba(248, 250, 252, 0.98) !important;
  backdrop-filter: none !important;
  -webkit-backdrop-filter: none !important;
  box-shadow: none !important;
  border-right: none !important;
  border-left: none !important;
}

.pro-table__grid :deep(.el-table__body tr:hover > td.el-table-fixed-column--right),
.pro-table__grid :deep(.el-table__body tr:hover > td.el-table-fixed-column--left) {
  background: rgba(239, 246, 255, 0.98) !important;
  backdrop-filter: none !important;
  -webkit-backdrop-filter: none !important;
  box-shadow: none !important;
  border-right: none !important;
  border-left: none !important;
}

.pro-table__grid :deep(.el-table td),
.pro-table__grid :deep(.el-table th.el-table__cell) {
  padding: 10px 0;
  border-right: none !important;
  border-left: none !important;
}

.pro-table__grid :deep(.el-table__row:hover > td.el-table__cell) {
  background: rgba(239, 246, 255, 0.98) !important;
  backdrop-filter: none;
}

.pro-table__pagination {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  padding: 12px 16px;
  border-top: 1px solid #e2e8f0;
  background: rgba(248, 250, 252, 0.94);
  backdrop-filter: none;
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

  .pro-table__pagination {
    justify-content: center;
    overflow-x: auto;
  }
}
</style>
