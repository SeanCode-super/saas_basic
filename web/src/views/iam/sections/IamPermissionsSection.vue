<script setup lang="ts">
import BaseStatusTag from "@/components/base/BaseStatusTag.vue";
import ProTable from "@/components/pro/ProTable.vue";
import type { ProTableColumn } from "@/components/pro/ProTable.vue";
import type { IamApiResourceRow } from "@/api/modules/iam";

defineProps<{
  apiColumns: ProTableColumn[];
  apiResources: IamApiResourceRow[];
  loading: boolean;
  openApiCreate: () => void;
  openApiEdit: (row: IamApiResourceRow) => void;
  toggleApiResource: (row: IamApiResourceRow) => void;
  batchEnableApiResources: (rows: IamApiResourceRow[]) => void | Promise<void>;
  batchDisableApiResources: (rows: IamApiResourceRow[]) => void | Promise<void>;
}>();

function asApiRows(rows: object[]) {
  return rows as IamApiResourceRow[];
}
</script>

<template>
    <ProTable
      :columns="apiColumns"
      :data="apiResources"
      :loading="loading"
      selectable
      compact
      title="接口权限注册表"
    subtitle="接口资源先注册、再分配、再审计，保证权限不依赖前端菜单和业务分支判断。"
  >
    <template #toolbar>
      <el-button v-button-permission="{ code: 'iam_api_resource:create', fallback: 'iam:api-resource:write' }" type="primary" @click="openApiCreate">新增权限资源</el-button>
    </template>
    <template #bulkActions="{ rows }">
      <el-button type="primary" plain @click="batchEnableApiResources(asApiRows(rows))">批量启用</el-button>
      <el-button plain @click="batchDisableApiResources(asApiRows(rows))">批量停用</el-button>
    </template>
    <template #status="{ row }">
      <BaseStatusTag :status="String(row.status)" :type="row.status === 'ENABLED' ? 'success' : 'danger'" />
    </template>
    <template #actions="{ row }">
      <el-button v-button-permission="{ code: 'iam_api_resource:edit', fallback: 'iam:api-resource:write' }" link type="primary" @click="openApiEdit(row)">编辑</el-button>
      <el-button v-button-permission="{ code: 'iam_api_resource:toggle', fallback: 'iam:api-resource:write' }" link @click="toggleApiResource(row)">{{ row.status === "ENABLED" ? "停用" : "启用" }}</el-button>
    </template>
  </ProTable>
</template>
