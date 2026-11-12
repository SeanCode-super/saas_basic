<script setup lang="ts">
defineProps<{
  eyebrow: string;
  title: string;
  description: string;
  summary: Array<{
    label: string;
    value: string | number;
    note?: string;
  }>;
}>();
</script>

<template>
  <section class="module-workbench" :aria-label="title">
    <div v-if="$slots.actions" class="module-workbench__toolbar">
      <slot name="actions" />
    </div>

    <div v-if="summary.length" class="module-workbench__summary">
      <article v-for="item in summary" :key="item.label" class="module-workbench__summary-item">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
      </article>
    </div>

    <div class="module-workbench__content">
      <slot />
    </div>
  </section>
</template>

<style scoped lang="scss">
.module-workbench,
.module-workbench__content {
  display: grid;
  gap: 14px;
}

.module-workbench__toolbar {
  display: flex;
  min-height: 34px;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.module-workbench__summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border: 1px solid rgba(226, 232, 240, 0.92);
  border-radius: var(--sb-radius-glass-md, 18px);
  background: var(--sb-glass-bg, rgba(255, 255, 255, 0.90));
  backdrop-filter: blur(20px) saturate(160%);
  -webkit-backdrop-filter: blur(20px) saturate(160%);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.98),
    0 8px 24px -6px rgba(15, 23, 42, 0.06);
  overflow: hidden;
}

.module-workbench__summary-item {
  min-width: 0;
  padding: 14px 18px;
  border-right: 1px solid rgba(226, 232, 240, 0.5);

  &:last-child {
    border-right: 0;
  }

  span,
  strong {
    display: block;
  }

  span {
    color: var(--sb-text-tertiary);
    font-size: 12px;
  }

  strong {
    margin-top: 5px;
    overflow: hidden;
    font-size: 20px;
    line-height: 1.2;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

@media (max-width: 860px) {
  .module-workbench__summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .module-workbench__summary-item {
    border-bottom: 1px solid var(--sb-border-color);

    &:nth-child(2n) {
      border-right: 0;
    }

    &:nth-last-child(-n + 2) {
      border-bottom: 0;
    }
  }
}
</style>
