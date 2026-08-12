<script setup lang="ts">
</script>

<template>
  <el-card shadow="never">
    <template #header>
      <div class="panel-header">
        <h3>高风险动作目录</h3>
        <p>企业级审计首先要回答“什么动作必须被看见”。</p>
      </div>
    </template>

    <div class="risk-grid">
      <article class="risk-item">
        <strong>权限与身份变更</strong>
      </article>
      <article class="risk-item">
        <strong>配置与策略变更</strong>
      </article>
      <article class="risk-item">
        <strong>数据与文件外流</strong>
      </article>
    </div>
  </el-card>
</template>

<style scoped lang="scss">
.risk-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.risk-item {
  padding: 13px 14px;
  border: 1px solid var(--sb-border-color);
  border-radius: var(--sb-radius-md);
  background: #fafbfc;
}

@media (max-width: 1080px) {
  .risk-grid {
    grid-template-columns: 1fr;
  }
}
</style>
