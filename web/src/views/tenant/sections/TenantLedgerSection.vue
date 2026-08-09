<script setup lang="ts">
import { computed } from "vue";
import type { TenantRow } from "@/api/modules/tenant";
import BaseStatusTag from "@/components/base/BaseStatusTag.vue";
import ProTable, { type ProTableColumn } from "@/components/pro/ProTable.vue";
import SchemaForm, { type SchemaField } from "@/components/pro/SchemaForm.vue";

const props = defineProps<{
  query: Record<string, string>;
  schema: SchemaField[];
  columns: ProTableColumn[];
  rows: TenantRow[];
  loading: boolean;
  resetQuery: () => void;
  loadData: () => Promise<void> | void;
  openCreateDialog: () => void;
  openImportDialog: () => void;
}>();

const queryModel = computed({
  get: () => props.query,
  set: (value) => Object.assign(props.query, value)
});
</script>

<template>
  <section class="module-registry-shell">
    <div class="module-registry-shell__header">
      <div>
        <span class="module-registry-shell__eyebrow">TENANT LEDGER</span>
        <h3>租户台账</h3>
        <p>查看租户编码、状态、套餐和隔离模式，并作为治理末端的台账入口。</p>
      </div>
    </div>

    <SchemaForm v-model="queryModel" :schema="schema">
      <template #actions>
        <el-button @click="resetQuery">重置</el-button>
        <el-button type="primary" @click="loadData">查询</el-button>
      </template>
    </SchemaForm>

    <ProTable
      title="租户台账"
      subtitle="统一查看租户编码、套餐版本、隔离模式和当前状态，保证平台治理口径一致。"
      :loading="loading"
      :columns="columns"
      :data="rows"
      selectable
      empty-title="当前没有租户台账"
      empty-description="可以先新建租户，或通过导入清单一次性导入租户基础档案。"
    >
      <template #toolbar>
        <el-button @click="openImportDialog">批量导入</el-button>
        <el-button type="primary" @click="openCreateDialog">新建租户</el-button>
      </template>
      <template #status="{ row }">
        <BaseStatusTag :status="row.status" />
      </template>
    </ProTable>
  </section>
</template>
