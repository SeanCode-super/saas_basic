<script setup lang="ts">
import BaseCard from "@/components/base/BaseCard.vue";
import ProTable from "@/components/pro/ProTable.vue";
import type { ProTableColumn } from "@/components/pro/ProTable.vue";

defineProps<{
  jobs: Array<Record<string, unknown>>;
  logs: Array<Record<string, unknown>>;
}>();

const jobColumns: ProTableColumn[] = [
  { prop: "code", label: "任务编码", minWidth: 160 },
  { prop: "name", label: "任务名称", minWidth: 180 },
  { prop: "status", label: "状态", minWidth: 100 }
];
</script>

<template>
  <div class="overview-grid">
    <BaseCard>
      <template #header>
        <div class="panel-header">
          <h3>调度控制面</h3>
          <p>企业级调度底座必须同时覆盖执行、补偿、告警和日志四条链路。</p>
        </div>
      </template>
      <div class="matrix-grid">
        <article class="matrix-item">
          <strong>执行控制</strong>
          <p>重试、Misfire、并发、路由和隔离执行器策略。</p>
        </article>
        <article class="matrix-item">
          <strong>异常补偿</strong>
          <p>失败补偿、超时恢复、阻塞冲突和人工介入。</p>
        </article>
        <article class="matrix-item">
          <strong>日志审计</strong>
          <p>执行日志、失败原因、关联审计和事件追踪。</p>
        </article>
      </div>
    </BaseCard>

    <ProTable
      title="调度任务"
      subtitle="真实调度任务台账。"
      :columns="jobColumns"
      :data="jobs"
      compact
      empty-title="当前没有调度任务"
      empty-description="先在执行策略页新增任务。"
    />
  </div>
</template>
