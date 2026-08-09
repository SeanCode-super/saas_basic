<script setup lang="ts">
</script>

<template>
  <el-card shadow="never">
    <template #header>
      <div class="panel-header">
        <h3>执行链路</h3>
        <p>任务治理必须清楚展示从定义到日志留存的完整执行路径。</p>
      </div>
    </template>
    <el-steps direction="vertical" :active="4">
      <el-step title="定义任务" description="定义作业与业务参数" />
      <el-step title="绑定触发器" description="Cron、延时、依赖触发" />
      <el-step title="分发执行器" description="按路由策略投递到执行节点" />
      <el-step title="结果归档" description="统一写入日志、告警和审计" />
    </el-steps>
  </el-card>
</template>
