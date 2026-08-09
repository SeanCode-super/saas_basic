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
          <p>登录、配置、权限、数据导出、文件下载和任务执行。</p>
        </article>
        <article class="risk-item">
          <strong>预警</strong>
          <p>异常登录、权限提升、批量导出、异常回调和高危下载。</p>
        </article>
        <article class="risk-item">
          <strong>处置</strong>
          <p>账号封禁、设备封禁、IP 拉黑、租户隔离和人工复核。</p>
        </article>
      </div>
    </BaseCard>
  </div>
</template>

<style scoped lang="scss">
.risk-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}

.risk-item {
  padding: 18px;
  border-radius: 20px;
  background: rgb(209 67 67 / 0.06);

  p {
    margin: 10px 0 0;
    color: var(--sb-text-secondary);
    line-height: 1.7;
  }
}

@media (max-width: 1080px) {
  .risk-grid {
    grid-template-columns: 1fr;
  }
}
</style>
