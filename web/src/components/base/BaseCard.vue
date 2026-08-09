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
  border-radius: var(--sb-radius-lg);
  background:
    linear-gradient(180deg, rgb(255 255 255 / 0.96), rgb(247 250 254 / 0.94)),
    var(--sb-surface-strong);
  backdrop-filter: blur(10px);
  position: relative;
  overflow: hidden;
  box-shadow: var(--sb-shadow-sm);

  &::before {
    content: "";
    position: absolute;
    inset: 0 0 auto;
    height: 3px;
    background: var(--sb-card-highlight);
    opacity: 0.8;
  }

  &::after {
    content: "";
    position: absolute;
    top: -30%;
    right: -10%;
    width: 220px;
    height: 220px;
    background: radial-gradient(circle, rgb(30 94 255 / 0.08), transparent 68%);
    pointer-events: none;
  }
}

.base-card :deep(.el-card__header) {
  position: relative;
  z-index: 1;
  padding: 22px 24px 0;
  border-bottom: none;
}

.base-card :deep(.el-card__body) {
  position: relative;
  z-index: 1;
  padding: 22px 24px 24px;
}

.base-card--soft {
  background:
    linear-gradient(180deg, rgb(250 252 255 / 0.98), rgb(245 249 255 / 0.96)),
    var(--sb-surface-soft);
}

.base-card--contrast {
  background:
    linear-gradient(180deg, rgb(245 248 255 / 0.98), rgb(239 245 255 / 0.95)),
    var(--sb-surface-contrast);
  border-color: var(--sb-border-subtle);
}

.base-card--accent {
  border-color: rgb(199 134 47 / 0.18);
  background:
    radial-gradient(circle at top right, rgb(199 134 47 / 0.12), transparent 30%),
    linear-gradient(180deg, rgb(255 252 247 / 0.98), rgb(250 247 242 / 0.94));
  box-shadow: var(--sb-shadow-md);

  &::before {
    background: var(--sb-card-highlight-accent);
  }

  &::after {
    background: radial-gradient(circle, rgb(199 134 47 / 0.12), transparent 70%);
  }
}
</style>
