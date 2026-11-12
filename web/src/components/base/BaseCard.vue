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
  border: 1px solid rgba(226, 232, 240, 0.92);
  border-radius: var(--sb-radius-glass, 22px);
  background: var(--sb-glass-bg, rgba(255, 255, 255, 0.90));
  backdrop-filter: blur(22px) saturate(165%);
  -webkit-backdrop-filter: blur(22px) saturate(165%);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.98),
    0 10px 28px -8px rgba(15, 23, 42, 0.06);
  transition: transform 180ms cubic-bezier(0.16, 1, 0.3, 1), box-shadow 180ms ease, border-color 180ms ease;

  &:hover {
    box-shadow:
      inset 0 1px 0 rgba(255, 255, 255, 1),
      0 14px 34px -10px rgba(15, 23, 42, 0.09);
  }
}

.base-card :deep(.el-card__header) {
  padding: 14px 20px;
  border-bottom: 1px solid rgba(226, 232, 240, 0.85);
  font-weight: 650;
  color: var(--sb-text-primary);
  background: rgba(248, 250, 252, 0.94);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-radius: var(--sb-radius-glass, 22px) var(--sb-radius-glass, 22px) 0 0;
}

.base-card :deep(.el-card__body) {
  padding: 18px 20px;
  background: transparent;
}

.base-card--soft,
.base-card--contrast {
  background: rgba(248, 250, 252, 0.92);
}

.base-card--accent {
  border-color: rgba(147, 197, 253, 0.95);
  background: rgba(239, 246, 255, 0.94);
}
</style>
