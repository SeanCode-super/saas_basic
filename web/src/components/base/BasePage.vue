<script setup lang="ts">
import { useSlots } from "vue";

withDefaults(
  defineProps<{
    title: string;
    description?: string;
    eyebrow?: string;
  }>(),
  {
    eyebrow: "控制台页面"
  }
);

const slots = useSlots();
</script>

<template>
  <section class="base-page" :aria-label="title">
    <header v-if="slots.actions" class="base-page__actions">
      <slot name="actions" />
    </header>
    <div class="base-page__body">
      <slot />
    </div>
  </section>
</template>

<style scoped lang="scss">
.base-page,
.base-page__body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.base-page__actions {
  display: flex;
  min-height: 34px;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
