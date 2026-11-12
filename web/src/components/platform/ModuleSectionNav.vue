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
        class="module-section-nav__item"
        :class="{ 'is-active': modelValue === item.key }"
        @click="update(item.key)"
      >
        <div>
          <strong>{{ item.label }}</strong>
        </div>
      </button>
    </div>
  </section>
</template>

<style scoped lang="scss">
.module-section-nav {
  display: block;
  background: var(--sb-glass-bg, rgba(255, 255, 255, 0.90));
  border: 1px solid rgba(226, 232, 240, 0.92);
  border-radius: 14px;
  padding: 4px 16px 0;
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  box-shadow: 0 4px 14px -4px rgba(15, 23, 42, 0.05);
}

.module-section-nav__grid {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
}

.module-section-nav__item {
  display: inline-flex;
  align-items: center;
  margin-bottom: -1px;
  padding: 10px 4px;
  border: 0;
  border-bottom: 2.5px solid transparent;
  background: transparent;
  color: var(--sb-text-muted, #475569);
  text-align: left;
  cursor: pointer;
  transition: 140ms ease;

  &:hover {
    color: var(--sb-text-primary, #0f172a);
  }

  &.is-active {
    border-bottom-color: var(--sb-primary-color, #2563eb);
    color: var(--sb-primary-color, #2563eb);
  }

  strong {
    font-size: 13.5px;
    font-weight: 600;
  }
}
</style>
