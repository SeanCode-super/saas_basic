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
    <header v-if="title || $slots.actions" class="control-surface__header">
      <div class="control-surface__heading">
        <div class="control-surface__title-row">
          <h2 class="control-surface__title">{{ title }}</h2>
          <span v-if="badge || eyebrow" class="control-surface__badge">
            {{ badge || eyebrow }}
          </span>
        </div>
        <p v-if="description" class="control-surface__desc">{{ description }}</p>
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
  gap: 12px 18px;
  padding: 14px 20px;
  margin-bottom: 14px;
  border-radius: 18px;
  background: var(--sb-glass-bg, rgba(255, 255, 255, 0.90));
  border: 1px solid rgba(226, 232, 240, 0.92);
  backdrop-filter: blur(18px) saturate(150%);
  -webkit-backdrop-filter: blur(18px) saturate(150%);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.96),
    0 6px 20px -6px rgba(15, 23, 42, 0.06);
}

.control-surface__heading {
  min-width: 0;
  flex: 1 1 360px;
}

.control-surface__title-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.control-surface__title {
  margin: 0;
  font-size: 16.5px;
  font-weight: 700;
  color: var(--sb-text-primary, #0f172a);
  line-height: 1.3;
  letter-spacing: -0.015em;
}

.control-surface__badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 9px;
  border-radius: 9999px;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  color: #1d4ed8;
  font-size: 11px;
  font-weight: 600;
  line-height: 1.4;
}

.control-surface__desc {
  margin: 4px 0 0;
  font-size: 12.5px;
  color: var(--sb-text-muted, #475569);
  line-height: 1.45;
}

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
</style>
