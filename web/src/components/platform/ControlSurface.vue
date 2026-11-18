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
  gap: 10px 14px;
  min-height: 46px;
  padding: 8px 16px;
  margin-bottom: 10px;
  border-radius: var(--sb-radius-glass-md, 14px);
  background: var(--sb-tier1-bg, rgba(255, 255, 255, 0.88));
  border: 1px solid var(--sb-border-subtle, #e2e8f0);
  backdrop-filter: var(--sb-glass-blur-sm, blur(14px) saturate(150%));
  -webkit-backdrop-filter: var(--sb-glass-blur-sm, blur(14px) saturate(150%));
  box-shadow: var(--sb-shadow-xs);
}

.control-surface__heading {
  min-width: 0;
  flex: 1 1 280px;
  display: flex;
  align-items: center;
  gap: 10px;
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
  padding: 2px 8px;
  border-radius: 9999px;
  background: var(--sb-badge-primary-bg, rgba(37, 99, 235, 0.10));
  border: 1px solid var(--sb-badge-primary-border, rgba(37, 99, 235, 0.24));
  color: var(--sb-badge-primary-text, #1d4ed8);
  font-size: 11px;
  font-weight: 600;
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
  min-width: 120px;
  font-size: 12.5px;
  color: var(--sb-text-muted);
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
