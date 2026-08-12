<script setup lang="ts">
import { nextTick, onMounted, ref } from "vue";

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

const actionsReady = ref(false);

onMounted(async () => {
  await nextTick();
  actionsReady.value = document.querySelector("#console-context-actions") !== null;
});
</script>

<template>
  <section class="control-surface" :aria-label="title">
    <Teleport v-if="$slots.actions && actionsReady" to="#console-context-actions">
      <div class="control-surface__toolbar">
        <slot name="actions" />
      </div>
    </Teleport>
    <div class="control-surface__body">
      <slot />
    </div>
  </section>
</template>

<style scoped lang="scss">
.control-surface {
  min-width: 0;
}

.control-surface__toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
}

.control-surface__body {
  min-width: 0;
}
</style>
