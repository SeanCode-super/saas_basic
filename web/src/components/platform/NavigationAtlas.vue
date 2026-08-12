<script setup lang="ts">
import { computed } from "vue";
import { resolveAppMeta } from "@/config/app-taxonomy";
import { useLocaleStore } from "@/stores/modules/locale";

interface AtlasNavItem {
  id: number | string;
  code: string;
  title: string;
  path: string;
  query: Record<string, string>;
  children: AtlasNavItem[];
}

defineEmits<{
  select: [item: AtlasNavItem];
}>();

const props = defineProps<{
  apps: AtlasNavItem[];
  activeCode?: string;
  title?: string;
  description?: string;
  compact?: boolean;
}>();

const localeStore = useLocaleStore();

const groupedApps = computed(() => {
  const groups = new Map<string, AtlasNavItem[]>();

  for (const app of props.apps) {
    const key = resolveAppMeta(app.code, app.title).cluster;
    const current = groups.get(key) ?? [];
    current.push(app);
    groups.set(key, current);
  }

  return Array.from(groups.entries()).map(([key, items]) => ({
    key,
    label: localeStore.t(`cluster.${key}`),
    items
  }));
});
</script>

<template>
  <div class="navigation-atlas" :aria-label="title || '功能索引'">
    <section v-for="group in groupedApps" :key="group.key" class="navigation-atlas__group">
      <strong class="navigation-atlas__group-title">{{ group.label }}</strong>

      <div class="navigation-atlas__list">
        <article
          v-for="item in group.items"
          :key="item.id"
          class="navigation-atlas__app"
          :class="{ 'is-active': activeCode === item.code }"
        >
          <button class="navigation-atlas__app-button" @click="$emit('select', item)">
            {{ item.title }}
          </button>
          <div v-if="item.children.length" class="navigation-atlas__sections">
            <button
              v-for="child in item.children"
              :key="child.id"
              class="navigation-atlas__section-button"
              @click="$emit('select', child)"
            >
              {{ child.title }}
            </button>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<style scoped lang="scss">
.navigation-atlas,
.navigation-atlas__group,
.navigation-atlas__list {
  display: grid;
}

.navigation-atlas {
  gap: 20px;
}

.navigation-atlas__group {
  gap: 8px;
}

.navigation-atlas__group-title {
  color: var(--sb-text-tertiary);
  font-size: 12px;
}

.navigation-atlas__list {
  gap: 6px;
}

.navigation-atlas__app {
  padding: 10px 12px;
  border: 1px solid var(--sb-border-color);
  border-radius: var(--sb-radius-md);
  background: #fff;

  &.is-active {
    border-color: #9db9ee;
    box-shadow: inset 3px 0 #3478f6;
  }
}

.navigation-atlas__app-button,
.navigation-atlas__section-button {
  border: none;
  background: transparent;
  color: var(--sb-text-primary);
  font-family: inherit;
  text-align: left;
  cursor: pointer;
}

.navigation-atlas__app-button {
  width: 100%;
  padding: 2px 0 8px;
  font-size: 14px;
  font-weight: 700;
}

.navigation-atlas__sections {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.navigation-atlas__section-button {
  padding: 5px 8px;
  border-radius: 4px;
  color: var(--sb-text-secondary);
  font-size: 12px;

  &:hover {
    background: #f0f3f7;
    color: var(--sb-text-primary);
  }
}
</style>
