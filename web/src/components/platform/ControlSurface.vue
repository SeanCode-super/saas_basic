<script setup lang="ts">
withDefaults(
  defineProps<{
    title: string;
    description?: string;
    eyebrow?: string;
  }>(),
  {
    eyebrow: "控制面"
  }
);
</script>

<template>
  <section class="control-surface" :aria-label="title">
    <header v-if="$slots.actions" class="control-surface__toolbar">
      <slot name="actions" />
    </header>
    <div class="control-surface__body">
      <slot />
    </div>
  </section>
</template>

<style scoped lang="scss">
.control-surface {
  display: grid;
  gap: 12px;
}

.control-surface__toolbar {
  display: flex;
  min-height: 34px;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.control-surface__body {
  min-width: 0;
}
</style>
