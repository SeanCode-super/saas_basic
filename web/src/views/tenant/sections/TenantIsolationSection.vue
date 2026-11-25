<script setup lang="ts">
import type { TenantRow } from "@/api/modules/tenant";

defineProps<{ rows: TenantRow[] }>();
</script>

<template>
  <el-card shadow="never">
    <template #header>租户隔离</template>
    <p>当前服务使用共享数据库，业务记录按租户过滤。独立库迁移尚未开放。</p>
    <el-table :data="rows">
      <el-table-column prop="tenantName" label="租户" />
      <el-table-column prop="isolationMode" label="台账登记模式" />
    </el-table>
  </el-card>
</template>
