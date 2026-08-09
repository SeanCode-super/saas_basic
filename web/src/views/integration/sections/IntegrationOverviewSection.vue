<script setup lang="ts">
import BaseCard from "@/components/base/BaseCard.vue";

defineProps<{
  connectionPolicy: {
    defaultTimeoutMs: number;
    rotationCycleDays: number;
  };
  webhookPolicy: {
    callbackTimeoutMs: number;
  };
}>();
</script>

<template>
  <div class="overview-grid">
    <BaseCard>
      <template #header>
        <div class="panel-header">
          <h3>集成能力矩阵</h3>
          <p>企业级集成底座不仅要连通，更要可追踪、可补偿、可治理。</p>
        </div>
      </template>
      <div class="matrix-grid">
        <article class="matrix-item">
          <strong>连接安全</strong>
          <p>超时、连通性检测、凭证加密、网络访问边界。</p>
        </article>
        <article class="matrix-item">
          <strong>回调投递</strong>
          <p>签名、重试、去重、超时、死信和异常补偿。</p>
        </article>
        <article class="matrix-item">
          <strong>接入台账</strong>
          <p>数据源、HTTP Client、Webhook 和第三方系统注册表。</p>
        </article>
      </div>
    </BaseCard>

    <BaseCard>
      <template #header>
        <div class="panel-header">
          <h3>运行信号</h3>
          <p>平台级集成健康情况应该作为运行底座的一部分被持续观察。</p>
        </div>
      </template>
      <div class="signal-stack">
        <article class="signal-item">
          <span>默认超时</span>
          <strong>{{ connectionPolicy.defaultTimeoutMs }} ms</strong>
        </article>
        <article class="signal-item">
          <span>轮换周期</span>
          <strong>{{ connectionPolicy.rotationCycleDays }} 天</strong>
        </article>
        <article class="signal-item">
          <span>回调超时</span>
          <strong>{{ webhookPolicy.callbackTimeoutMs }} ms</strong>
        </article>
      </div>
    </BaseCard>
  </div>
</template>
