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
}

.module-section-nav__grid {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  border-bottom: 1px solid var(--sb-border-color);
}

.module-section-nav__item {
  display: inline-flex;
  align-items: center;
  margin-bottom: -1px;
  padding: 9px 2px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: 140ms ease;

  &:hover {
    color: var(--sb-primary-color);
  }

  &.is-active {
    border-bottom-color: var(--sb-primary-color);
    color: var(--sb-primary-color);
  }

  strong {
    font-size: 13px;
    font-weight: 700;
  }
}
</style>
