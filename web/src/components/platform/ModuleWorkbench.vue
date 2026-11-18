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
  gap: 10px;
}

.module-workbench__toolbar {
  display: flex;
  min-height: 32px;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.module-workbench__summary {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  border: 0;
  border-radius: 0;
  background: transparent;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
  box-shadow: none;
  overflow: visible;
}

.module-workbench__summary-item {
  min-width: 0;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 32px;
  padding: 0 12px;
  border-radius: 9999px;
  border: 1px solid var(--sb-border-subtle, #e2e8f0);
  background: var(--sb-tier2-bg, rgba(255, 255, 255, 0.80));
  backdrop-filter: var(--sb-glass-blur-sm, blur(18px));
  -webkit-backdrop-filter: var(--sb-glass-blur-sm, blur(18px));
  box-shadow: var(--sb-shadow-xs);

  span,
  strong {
    display: inline-block;
  }

  span {
    color: var(--sb-text-secondary);
    font-size: 12px;
    font-weight: 500;
    white-space: nowrap;
  }

  strong {
    margin-top: 0;
    overflow: hidden;
    color: var(--sb-text-primary);
    font-size: 14px;
    font-weight: 700;
    font-variant-numeric: tabular-nums;
    line-height: 1.2;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

@media (max-width: 860px) {
  .module-workbench__summary {
    gap: 6px;
  }
}
</style>
