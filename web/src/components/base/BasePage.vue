<script setup lang="ts">
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
</script>

<template>
  <section class="base-page">
    <header class="base-page__header">
      <div class="base-page__copy">
        <span class="base-page__eyebrow">{{ eyebrow }}</span>
        <h1>{{ title }}</h1>
        <p v-if="description">{{ description }}</p>
      </div>
      <div class="base-page__actions">
        <slot name="actions" />
      </div>
    </header>
    <div class="base-page__body">
      <slot />
    </div>
  </section>
</template>

<style scoped lang="scss">
.base-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.base-page__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  padding: 22px 26px;
  border: 1px solid var(--sb-border-color);
  border-radius: var(--sb-radius-lg);
  background:
    linear-gradient(0deg, transparent, transparent),
    var(--sb-card-overlay);
  box-shadow: var(--sb-shadow-md);
  position: relative;
  overflow: hidden;

  &::before {
    content: "";
    position: absolute;
    inset: 0 auto 0 0;
    width: 6px;
    background: linear-gradient(180deg, var(--sb-primary-color), rgb(101 142 255 / 0.22));
  }

  &::after {
    content: "";
    position: absolute;
    inset: 0;
    background-image:
      linear-gradient(90deg, transparent 0, transparent calc(100% - 1px), rgb(15 27 45 / 0.03) calc(100% - 1px)),
      linear-gradient(180deg, transparent 0, transparent calc(100% - 1px), rgb(15 27 45 / 0.03) calc(100% - 1px));
    background-size: 120px 120px;
    mask-image: linear-gradient(135deg, rgb(0 0 0 / 0.04), transparent 68%);
    pointer-events: none;
  }

  h1 {
    margin: 0;
    font-size: 28px;
    line-height: 1.15;
    letter-spacing: -0.03em;
  }

  p {
    margin: 8px 0 0;
    color: var(--sb-text-secondary);
    max-width: 760px;
    line-height: 1.72;
  }
}

.base-page__copy {
  min-width: 0;
  padding-left: 8px;
}

.base-page__eyebrow {
  display: inline-flex;
  color: var(--sb-primary-strong);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.2em;
  text-transform: uppercase;
}

.base-page__actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.base-page__body {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

@media (max-width: 960px) {
  .base-page__header {
    flex-direction: column;
    padding: 18px 20px;
  }
}
</style>
