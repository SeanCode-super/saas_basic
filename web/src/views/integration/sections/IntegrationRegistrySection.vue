<script setup lang="ts">
import { computed } from "vue";
import type { DatasourceRow } from "@/api/modules/integration";
import BaseStatusTag from "@/components/base/BaseStatusTag.vue";
import ProTable, { type ProTableColumn } from "@/components/pro/ProTable.vue";
import SchemaForm, { type SchemaField } from "@/components/pro/SchemaForm.vue";

const props = defineProps<{
  query: Record<string, string>;
  schema: SchemaField[];
  columns: ProTableColumn[];
  rows: DatasourceRow[];
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
        <span class="module-registry-shell__eyebrow">集成注册表</span>
        <h3>接入台账</h3>
        <p>所有数据源和接入对象都必须进入统一注册表，而不是散落在各系统配置中。</p>
      </div>
    </div>

    <SchemaForm v-model="queryModel" :schema="schema">
      <template #actions>
        <el-button @click="resetQuery">重置</el-button>
        <el-button type="primary" @click="loadData">查询</el-button>
      </template>
    </SchemaForm>

    <ProTable
      title="接入台账"
      subtitle="统一登记数据源、连通性和检测状态，避免接入配置散落在各业务系统。"
      :loading="loading"
      :columns="columns"
      :data="rows"
      selectable
      empty-title="当前没有接入对象"
      empty-description="可以先新增数据源，或导入接入清单快速建立连接台账。"
    >
      <template #toolbar>
        <el-button @click="openImportDialog">批量导入</el-button>
        <el-button type="primary" @click="openCreateDialog">新增数据源</el-button>
      </template>
      <template #testStatus="{ row }">
        <BaseStatusTag :status="row.testStatus" />
      </template>
      <template #status="{ row }">
        <BaseStatusTag :status="row.status" />
      </template>
    </ProTable>
  </section>
</template>
