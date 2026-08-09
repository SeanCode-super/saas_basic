<script setup lang="ts">
import BaseCard from "@/components/base/BaseCard.vue";

defineProps<{
  securityPolicy: { sessionTimeoutMinutes: number };
  localePolicy: { fallbackLocale: string };
  sequencePolicy: { tenantPrefix: string };
}>();
</script>

<template>
  <div class="overview-grid">
    <BaseCard>
      <template #header>
        <div class="panel-header">
          <h3>系统基础控制面</h3>
          <p>企业级系统底座必须同时回答安全、国际化、编号和参数注册这四类问题。</p>
        </div>
      </template>
      <div class="matrix-grid">
        <article class="matrix-item">
          <strong>安全边界</strong>
          <p>密码复杂度、会话时长、密码历史、多因子和设备信任。</p>
        </article>
        <article class="matrix-item">
          <strong>语言字典</strong>
          <p>默认语言、回退语言、缓存刷新、字典发布与国际化资源。</p>
        </article>
        <article class="matrix-item">
          <strong>参数与序列</strong>
          <p>参数分组、值类型、编号规则和重置周期一体治理。</p>
        </article>
      </div>
    </BaseCard>

    <BaseCard>
      <template #header>
        <div class="panel-header">
          <h3>当前运行信号</h3>
          <p>平台公共行为应先看系统底座，业务模块只做受控覆盖。</p>
        </div>
      </template>
      <div class="signal-stack">
        <article class="signal-item">
          <span>会话超时</span>
          <strong>{{ securityPolicy.sessionTimeoutMinutes }} 分钟</strong>
        </article>
        <article class="signal-item">
          <span>回退语言</span>
          <strong>{{ localePolicy.fallbackLocale }}</strong>
        </article>
        <article class="signal-item">
          <span>租户编码前缀</span>
          <strong>{{ sequencePolicy.tenantPrefix }}</strong>
        </article>
      </div>
    </BaseCard>
  </div>
</template>
