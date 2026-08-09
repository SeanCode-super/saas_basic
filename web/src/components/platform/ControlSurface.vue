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
  <section class="control-surface">
    <header class="control-surface__header">
      <div class="control-surface__copy">
        <span class="control-surface__eyebrow">{{ eyebrow }}</span>
        <h2>{{ title }}</h2>
        <p v-if="description">{{ description }}</p>
      </div>
      <div v-if="$slots.actions" class="control-surface__actions">
        <slot name="actions" />
      </div>
    </header>

    <div class="control-surface__body">
      <slot />
    </div>
  </section>
</template>

<style scoped lang="scss">
.control-surface {
  display: grid;
  gap: 16px;
}

.control-surface__header,
.control-surface__body {
  border: 1px solid var(--sb-border-color);
  border-radius: var(--sb-radius-lg);
  background:
    linear-gradient(180deg, rgb(255 255 255 / 0.98), rgb(248 250 254 / 0.96)),
    var(--sb-surface-strong);
  box-shadow: var(--sb-shadow-sm);
}

.control-surface__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 22px;
}

.control-surface__copy {
  min-width: 0;

  h2 {
    margin: 6px 0 0;
    font-size: 22px;
    line-height: 1.2;
    letter-spacing: -0.02em;
  }

  p {
    margin: 8px 0 0;
    color: var(--sb-text-secondary);
    line-height: 1.68;
    max-width: 760px;
  }
}

.control-surface__eyebrow {
  display: inline-flex;
  color: var(--sb-primary-strong);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.control-surface__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 12px;
}

.control-surface__body {
  padding: 20px 22px 22px;
}

@media (max-width: 960px) {
  .control-surface__header {
    flex-direction: column;
  }
}
</style>
