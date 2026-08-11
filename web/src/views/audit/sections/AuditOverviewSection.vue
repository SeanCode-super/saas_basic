<script setup lang="ts">
import BaseCard from "@/components/base/BaseCard.vue";
</script>

<template>
  <div class="overview-grid">
    <BaseCard>
      <template #header>
        <div class="panel-header">
          <h3>审计与风控矩阵</h3>
          <p>平台底座要把行为留痕、风险识别、黑名单和高危目录做成闭环。</p>
        </div>
      </template>
      <div class="risk-grid">
        <article class="risk-item">
          <strong>留痕</strong>
        </article>
        <article class="risk-item">
          <strong>预警</strong>
        </article>
        <article class="risk-item">
          <strong>处置</strong>
        </article>
      </div>
    </BaseCard>
  </div>
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
