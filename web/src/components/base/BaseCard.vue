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
  border: 1px solid var(--sb-border-color);
  border-radius: var(--sb-radius-md);
  background: #ffffff;
  box-shadow: var(--sb-shadow-xs);
  transition: box-shadow 160ms ease, border-color 160ms ease;
}

.base-card :deep(.el-card__header) {
  padding: 14px 18px;
  border-bottom: 1px solid var(--sb-border-subtle);
  font-weight: 600;
  color: var(--sb-text-primary);
}

.base-card :deep(.el-card__body) {
  padding: 16px;
}

.base-card--soft,
.base-card--contrast {
  background: #ffffff;
}

.base-card--accent {
  border-color: #93c5fd;
  background: #ffffff;
}
</style>
