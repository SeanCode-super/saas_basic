<script setup lang="ts">
import { computed } from "vue";

const props = withDefaults(
  defineProps<{
    tone?: "default" | "soft" | "accent" | "contrast";
  }>(),
  {
    tone: "default"
  }
);

const cardClass = computed(() => [`base-card--${props.tone}`]);
</script>

<template>
  <el-card class="base-card" :class="cardClass" shadow="never">
    <template v-if="$slots.header" #header>
      <slot name="header" />
    </template>
    <slot />
  </el-card>
</template>

<style scoped lang="scss">
.base-card {
  border: 1px solid var(--sb-border-subtle, #e2e8f0);
  border-radius: var(--sb-radius-glass, 20px);
  background: var(--sb-tier2-bg, rgba(255, 255, 255, 0.96));
  box-shadow: var(--sb-shadow-sm);
  transition: transform 180ms cubic-bezier(0.16, 1, 0.3, 1), box-shadow 180ms ease, border-color 180ms ease;

  &:hover {
    box-shadow: var(--sb-shadow-md);
  }
}

.base-card :deep(.el-card__header) {
  padding: 12px 18px;
  border-bottom: 1px solid var(--sb-border-subtle, #e2e8f0);
  font-weight: 650;
  color: var(--sb-text-primary);
  background: var(--sb-tier2-header-bg, rgba(248, 250, 252, 0.98));
  border-radius: var(--sb-radius-glass, 20px) var(--sb-radius-glass, 20px) 0 0;
}

.base-card :deep(.el-card__body) {
  padding: 16px 18px;
  background: transparent;
}

.base-card--soft,
.base-card--contrast {
  background: var(--sb-surface-soft, rgba(248, 250, 252, 0.90));
}

.base-card--accent {
  border-color: var(--sb-badge-primary-border, rgba(37, 99, 235, 0.24));
  background: var(--sb-badge-primary-bg, rgba(37, 99, 235, 0.10));
}
</style>
