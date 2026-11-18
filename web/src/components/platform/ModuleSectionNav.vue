<script setup lang="ts">
defineProps<{
  modelValue: string;
  items: Array<{
    key: string;
    label: string;
    description?: string;
  }>;
}>();

const emit = defineEmits<{
  "update:modelValue": [value: string];
}>();

function update(value: string) {
  emit("update:modelValue", value);
}
</script>

<template>
  <section class="module-section-nav">
    <div class="module-section-nav__grid">
      <button
        v-for="item in items"
        :key="item.key"
        type="button"
        class="module-section-nav__item"
        :class="{ 'is-active': modelValue === item.key }"
        @click="update(item.key)"
      >
        <strong>{{ item.label }}</strong>
      </button>
    </div>
  </section>
</template>

<style scoped lang="scss">
.module-section-nav {
  display: block;
  background: var(--sb-tier1-bg, rgba(255, 255, 255, 0.88));
  border: 1px solid var(--sb-border-subtle, #e2e8f0);
  border-radius: 12px;
  padding: 4px;
  backdrop-filter: var(--sb-glass-blur-sm, blur(14px));
  -webkit-backdrop-filter: var(--sb-glass-blur-sm, blur(14px));
  box-shadow: var(--sb-shadow-xs);
}

.module-section-nav__grid {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
}

.module-section-nav__item {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 30px;
  padding: 0 13px;
  border: 1px solid transparent;
  border-radius: var(--sb-radius-control, 8px);
  background: transparent;
  color: var(--sb-text-muted);
  text-align: center;
  cursor: pointer;
  white-space: nowrap;
  transition: background 140ms ease, color 140ms ease, border-color 140ms ease, box-shadow 140ms ease;

  &:hover {
    color: var(--sb-text-primary);
    background: var(--sb-surface-soft);
  }

  &.is-active {
    background: var(--sb-tier2-bg);
    border-color: var(--sb-border-subtle);
    color: var(--sb-primary-color);
    box-shadow: var(--sb-shadow-xs);
  }

  strong {
    font-size: 13px;
    font-weight: 600;
  }
}
</style>
