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
  background: #fff;
  box-shadow: none;
}

.base-card :deep(.el-card__header) {
  padding: 14px 16px;
  border-bottom: 1px solid var(--sb-border-color);
}

.base-card :deep(.el-card__body) {
  padding: 16px;
}

.base-card--soft,
.base-card--contrast {
  background: #fafbfc;
}

.base-card--accent {
  border-color: #b9c9e8;
  background: #fff;
}
</style>
