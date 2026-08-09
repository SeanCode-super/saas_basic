<script setup lang="ts">
import { computed } from "vue";
import type { SystemConfigRow } from "@/api/modules/system";
import type { PortalClientRow, PortalTerminalRow } from "@/api/modules/portal";
import BaseStatusTag from "@/components/base/BaseStatusTag.vue";
import ProTable, { type ProTableColumn } from "@/components/pro/ProTable.vue";
import SchemaForm, { type SchemaField } from "@/components/pro/SchemaForm.vue";

const props = defineProps<{
  query: Record<string, string>;
  schema: SchemaField[];
  columns: ProTableColumn[];
  rows: SystemConfigRow[];
  portalClientColumns: ProTableColumn[];
  portalClients: PortalClientRow[];
  portalTerminalColumns: ProTableColumn[];
  portalTerminals: PortalTerminalRow[];
  loading: boolean;
  resetQuery: () => void;
  loadData: () => Promise<void> | void;
  openCreateDialog: () => void;
  openImportDialog: () => void;
  openCreatePortalClient: () => void;
  openCreatePortalTerminal: () => void;
  editPortalClient: (row: PortalClientRow) => void;
  editPortalTerminal: (row: PortalTerminalRow) => void;
  previewPortalClient: (row: PortalClientRow) => void;
  previewPortalTerminal: (row: PortalTerminalRow) => void;
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
        <span class="module-registry-shell__eyebrow">系统注册表</span>
        <h3>配置注册表</h3>
        <p>系统级参数都要纳入统一注册表，而不是散落在代码常量或业务表中。</p>
      </div>
    </div>

    <SchemaForm v-model="queryModel" :schema="schema">
      <template #actions>
        <el-button @click="resetQuery">重置</el-button>
        <el-button type="primary" @click="loadData">查询</el-button>
      </template>
    </SchemaForm>

    <ProTable
      title="配置注册表"
      subtitle="把系统参数、开关和平台约束统一纳入可审计的注册表，而不是散落在各模块内。"
      :loading="loading"
      :columns="columns"
      :data="rows"
      selectable
      empty-title="当前没有配置项"
      empty-description="可以先新增配置，或导入一组参数清单快速建立系统注册表。"
    >
      <template #toolbar>
        <el-button @click="openImportDialog">批量导入</el-button>
        <el-button type="primary" @click="openCreateDialog">新增配置</el-button>
      </template>
      <template #status="{ row }">
        <BaseStatusTag :status="row.status" />
      </template>
    </ProTable>

    <ProTable
      title="门户 Client"
      subtitle="查询登录入口 client 配置，确认 clientId、主题、验证码模式和策略绑定。"
      :loading="loading"
      :columns="portalClientColumns"
      :data="portalClients"
      compact
      empty-title="当前没有门户 client"
      empty-description="先配置门户 client，登录页才能根据 clientId 动态变化。"
    >
      <template #toolbar>
        <el-button @click="openCreatePortalClient">新增 Client</el-button>
      </template>
      <template #status="{ row }">
        <BaseStatusTag :status="row.status" />
      </template>
      <template #actions="{ row }">
        <el-button link @click="editPortalClient(row)">编辑</el-button>
        <el-button link type="primary" @click="previewPortalClient(row)">预览登录页</el-button>
      </template>
    </ProTable>

    <ProTable
      title="门户 Terminal"
      subtitle="查询终端入口配置，确认 terminalCode、终端类型、验证码模式和默认终端。"
      :loading="loading"
      :columns="portalTerminalColumns"
      :data="portalTerminals"
      compact
      empty-title="当前没有终端入口"
      empty-description="至少要有一条默认终端，用户才能从统一入口进入系统。"
    >
      <template #toolbar>
        <el-button @click="openCreatePortalTerminal">新增终端</el-button>
      </template>
      <template #status="{ row }">
        <BaseStatusTag :status="row.status" />
      </template>
      <template #actions="{ row }">
        <el-button link @click="editPortalTerminal(row)">编辑</el-button>
        <el-button link type="primary" @click="previewPortalTerminal(row)">预览终端入口</el-button>
      </template>
    </ProTable>
  </section>
</template>
