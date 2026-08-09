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
  gap: 10px;
}

.module-section-nav__item {
  display: inline-flex;
  align-items: center;
  border: 1px solid var(--sb-border-color);
  border-radius: 999px;
  background:
    linear-gradient(180deg, rgb(255 255 255 / 0.98), rgb(247 250 255 / 0.94)),
    var(--sb-surface-strong);
  padding: 10px 14px;
  text-align: left;
  cursor: pointer;
  transition: 180ms ease;

  &:hover,
  &.is-active {
    border-color: rgb(15 98 254 / 0.2);
    background: linear-gradient(180deg, rgb(15 98 254 / 0.08), rgb(255 255 255 / 0.98));
    box-shadow: var(--sb-shadow-sm);
  }

  strong {
    font-size: 13px;
    font-weight: 700;
  }
}
</style>
