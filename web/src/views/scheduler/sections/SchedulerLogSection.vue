<script setup lang="ts">
import ProTable from "@/components/pro/ProTable.vue";
import type { ProTableColumn } from "@/components/pro/ProTable.vue";
defineProps<{
  schedulerPolicy: {
    logRetentionDays: number;
  };
  loading?: boolean;
  logs: Array<Record<string, unknown>>;
  savePolicy: (message: string) => void;
  retryJob: (row: Record<string, unknown>) => void | Promise<void>;
}>();

const logColumns: ProTableColumn[] = [
  { prop: "executionNo", label: "执行号", minWidth: 220 },
  { prop: "jobId", label: "任务 ID", minWidth: 100 },
  { prop: "runStatus", label: "状态", minWidth: 120 },
  { prop: "traceId", label: "Trace ID", minWidth: 180 },
  { prop: "actions", label: "操作", minWidth: 140, slot: "actions" }
];
</script>

<template>
  <el-card shadow="never">
    <template #header>
      <div class="panel-header">
        <h3>日志治理</h3>
        <p>日志保留、查询维度、审计关联和归档策略属于平台统一能力。</p>
      </div>
    </template>

    <el-form label-position="top">
      <el-form-item label="日志保留天数">
        <el-input-number v-model="schedulerPolicy.logRetentionDays" :min="1" :max="365" style="width: 100%" />
      </el-form-item>
      <el-button type="primary" @click="savePolicy('调度日志治理策略已保存')">保存日志策略</el-button>
    </el-form>

    <ProTable
      title="执行日志"
      subtitle="真实调度执行日志。"
      :loading="loading"
      :columns="logColumns"
      :data="logs"
      compact
      empty-title="当前没有执行日志"
      empty-description="手动触发任务后，这里会产生新的执行日志。"
    >
      <template #actions="{ row }">
        <el-button link type="primary" @click="retryJob(row)">重新执行</el-button>
      </template>
    </ProTable>
  </el-card>
</template>
