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
        <p>角色分配、权限码变更、菜单授权、数据权限调整。</p>
      </article>
      <article class="risk-item">
        <strong>配置与策略变更</strong>
        <p>登录策略、密码策略、系统参数、租户隔离和配额调整。</p>
      </article>
      <article class="risk-item">
        <strong>数据与文件外流</strong>
        <p>导出、下载、分享、批量同步和回调投递失败补偿。</p>
      </article>
    </div>
  </el-card>
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
