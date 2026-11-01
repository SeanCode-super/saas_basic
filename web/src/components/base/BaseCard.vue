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
  border: 1px solid rgba(255, 255, 255, 0.85);
  border-radius: var(--sb-radius-glass, 24px);
  background: rgba(255, 255, 255, 0.58);
  backdrop-filter: blur(28px) saturate(210%) contrast(104%);
  -webkit-backdrop-filter: blur(28px) saturate(210%) contrast(104%);
  box-shadow:
    inset 0 1.5px 2px 0 rgba(255, 255, 255, 0.98),
    inset 0 0 0 1px rgba(255, 255, 255, 0.35),
    0 16px 40px -10px rgba(12, 34, 56, 0.08);
  transition: transform 180ms cubic-bezier(0.16, 1, 0.3, 1), box-shadow 180ms ease, border-color 180ms ease;

  &:hover {
    box-shadow:
      inset 0 1.5px 2px 0 rgba(255, 255, 255, 1),
      inset 0 0 0 1px rgba(255, 255, 255, 0.45),
      0 22px 50px -12px rgba(12, 34, 56, 0.12);
  }
}

.base-card :deep(.el-card__header) {
  padding: 14px 20px;
  border-bottom: 1px solid rgba(226, 232, 240, 0.6);
  font-weight: 650;
  color: var(--sb-text-primary);
  background: rgba(255, 255, 255, 0.40);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border-radius: var(--sb-radius-glass, 24px) var(--sb-radius-glass, 24px) 0 0;
}

.base-card :deep(.el-card__body) {
  padding: 18px 20px;
  background: transparent;
}

.base-card--soft,
.base-card--contrast {
  background: rgba(255, 255, 255, 0.52);
}

.base-card--accent {
  border-color: rgba(147, 197, 253, 0.85);
  background: rgba(239, 246, 255, 0.55);
}
</style>
