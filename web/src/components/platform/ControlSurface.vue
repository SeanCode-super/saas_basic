<script setup lang="ts">
withDefaults(
  defineProps<{
    title: string;
    description?: string;
    eyebrow?: string;
    badge?: string;
  }>(),
  {
    eyebrow: "控制面"
  }
);
</script>

<template>
  <section class="control-surface" :aria-label="title">
    <header v-if="title || $slots.actions || $slots.nav" class="control-surface__header">
      <div class="control-surface__heading">
        <span v-if="badge || eyebrow" class="control-surface__badge">
          {{ badge || eyebrow }}
        </span>
        <h2 class="control-surface__title">{{ title }}</h2>
        <span v-if="description" class="control-surface__divider" aria-hidden="true" />
        <p v-if="description" class="control-surface__desc" :title="description">{{ description }}</p>
      </div>
      <div v-if="$slots.nav" class="control-surface__nav">
        <slot name="nav" />
      </div>
      <div v-if="$slots.actions" class="control-surface__toolbar">
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
  min-width: 0;
}

.control-surface__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px 14px;
  min-height: 36px;
  padding: 2px 2px 6px;
  margin-bottom: 8px;
  border-radius: 0;
  background: transparent;
  border: 0;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
  box-shadow: none;
}

.control-surface__heading {
  min-width: 0;
  flex: 0 1 auto;
  max-width: 100%;
  min-height: 34px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: var(--sb-heading-capsule-padding, 0);
  border-radius: 9999px;
  background: var(--sb-heading-capsule-bg, transparent);
  border: 1px solid var(--sb-heading-capsule-border, transparent);
  backdrop-filter: var(--sb-heading-capsule-blur, none);
  -webkit-backdrop-filter: var(--sb-heading-capsule-blur, none);
  box-shadow: var(--sb-heading-capsule-shadow, none);
}

.control-surface__title {
  margin: 0;
  font-size: 15.5px;
  font-weight: 700;
  color: var(--sb-text-primary);
  line-height: 1.25;
  letter-spacing: -0.015em;
  white-space: nowrap;
  flex-shrink: 0;
}

.control-surface__badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 9px;
  border-radius: 9999px;
  background: var(--sb-pill-solid-bg, var(--sb-primary-color, #4f46e5));
  border: 1px solid transparent;
  color: #ffffff;
  font-size: 11px;
  font-weight: 650;
  line-height: 1.35;
  white-space: nowrap;
  flex-shrink: 0;
}

.control-surface__divider {
  width: 1px;
  height: 14px;
  background: var(--sb-border-color, #cbd5e1);
  flex-shrink: 0;
}

.control-surface__desc {
  margin: 0;
  flex: 1;
  min-width: 0;
  font-size: 12.5px;
  font-weight: 500;
  color: var(--sb-text-tertiary);
  line-height: 1.35;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.control-surface__nav,
.control-surface__toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  flex-shrink: 0;
}

.control-surface__body {
  min-width: 0;
}

@media (max-width: 900px) {
  .control-surface__divider,
  .control-surface__desc {
    display: none;
  }
}
</style>
