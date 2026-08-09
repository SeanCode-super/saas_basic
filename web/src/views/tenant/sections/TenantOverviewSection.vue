<script setup lang="ts">
import BaseCard from "@/components/base/BaseCard.vue";

defineProps<{
  runtimePolicy: {
    trialDays: number;
    defaultLocale: string;
  };
  packagePolicy: {
    seatQuota: number;
    storageQuotaGb: number;
  };
}>();
</script>

<template>
  <div class="overview-grid">
    <BaseCard>
      <template #header>
        <div class="panel-header">
          <h3>租户治理雷达</h3>
          <p>企业级租户控制面要同时覆盖注册、套餐、隔离和生命周期管理。</p>
        </div>
      </template>
      <div class="matrix-grid">
        <article class="matrix-item">
          <strong>注册与审批</strong>
          <p>自助申请、人工审批、试用期和控制台自动开通规则。</p>
        </article>
        <article class="matrix-item">
          <strong>套餐与配额</strong>
          <p>席位、存储、流量、功能开关和灰度发布方式。</p>
        </article>
        <article class="matrix-item">
          <strong>隔离升级</strong>
          <p>共享、独 Schema、独库与读写分离升级路径。</p>
        </article>
      </div>
    </BaseCard>

    <BaseCard>
      <template #header>
        <div class="panel-header">
          <h3>租户画像</h3>
          <p>快速理解当前租户结构和平台分层治理的重心。</p>
        </div>
      </template>
      <div class="signal-stack">
        <article class="signal-item">
          <span>试用窗口</span>
          <strong>{{ runtimePolicy.trialDays }} 天</strong>
        </article>
        <article class="signal-item">
          <span>默认语言</span>
          <strong>{{ runtimePolicy.defaultLocale }}</strong>
        </article>
        <article class="signal-item">
          <span>配额模型</span>
          <strong>{{ packagePolicy.seatQuota }} 席位 / {{ packagePolicy.storageQuotaGb }} GB</strong>
        </article>
      </div>
    </BaseCard>
  </div>
</template>
