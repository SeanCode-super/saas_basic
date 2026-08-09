<script setup lang="ts">
import { computed } from "vue";
import {
  resolveAppMeta,
  resolveSectionMeta,
  type AppMeta
} from "@/config/app-taxonomy";
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

const atlasStats = computed(() => ({
  apps: props.apps.length,
  sections: props.apps.reduce((sum, app) => sum + app.children.length, 0)
}));

const groupedApps = computed(() => {
  const groups = new Map<string, Array<{ meta: AppMeta; item: AtlasNavItem }>>();

  for (const app of props.apps) {
    const meta = resolveAppMeta(app.code, app.title);
    const key = meta.cluster;
    const current = groups.get(key) ?? [];
    current.push({ meta, item: app });
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
  <div class="navigation-atlas">
    <header v-if="title || description" class="navigation-atlas__header">
      <div>
        <span class="navigation-atlas__eyebrow">{{ localeStore.t("atlas.eyebrow") }}</span>
        <h3 v-if="title">{{ title }}</h3>
        <p v-if="description">{{ description }}</p>
      </div>
      <div class="navigation-atlas__stats">
        <article class="navigation-atlas__stat">
          <span>{{ localeStore.t("atlas.apps") }}</span>
          <strong>{{ atlasStats.apps }}</strong>
        </article>
        <article class="navigation-atlas__stat">
          <span>{{ localeStore.t("atlas.sections") }}</span>
          <strong>{{ atlasStats.sections }}</strong>
        </article>
      </div>
    </header>

    <section v-for="group in groupedApps" :key="group.key" class="navigation-atlas__group">
      <div class="navigation-atlas__group-header">
        <strong>{{ group.label }}</strong>
        <span>{{ localeStore.t("atlas.appCount", { count: group.items.length }) }}</span>
      </div>

      <div class="navigation-atlas__grid" :class="{ 'is-compact': compact }">
        <article
          v-for="{ item, meta } in group.items"
          :key="item.id"
          class="navigation-atlas__card"
          :class="{ 'is-active': activeCode === item.code, 'is-compact': compact }"
        >
          <button class="navigation-atlas__card-head" :class="{ 'is-compact': compact }" @click="$emit('select', item)">
            <div class="navigation-atlas__title-wrap">
              <span class="navigation-atlas__badge">{{ meta.badge }}</span>
              <div>
                <strong>{{ item.title }}</strong>
              </div>
            </div>
            <small>{{ localeStore.t("atlas.sectionCount", { count: item.children.length }) }}</small>
          </button>

          <p v-if="!compact" class="navigation-atlas__summary">{{ meta.summary }}</p>

          <div class="navigation-atlas__section-list" :class="{ 'is-compact': compact }">
            <button
              v-for="child in item.children"
              :key="child.id"
              class="navigation-atlas__section"
              :class="{ 'is-compact': compact }"
              @click="$emit('select', child)"
            >
              <strong>{{ child.title }}</strong>
              <span v-if="!compact">{{ resolveSectionMeta({ appCode: item.code, fallbackTitle: child.title, query: child.query }).description }}</span>
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
.navigation-atlas__grid,
.navigation-atlas__section-list {
  display: grid;
  gap: 16px;
}

.navigation-atlas__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;

  h3 {
    margin: 0;
    font-size: 20px;
  }

  p {
    margin: 8px 0 0;
    color: var(--sb-text-secondary);
    line-height: 1.7;
  }
}

.navigation-atlas__eyebrow {
  display: inline-flex;
  margin-bottom: 8px;
  color: var(--sb-primary-strong);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.navigation-atlas__stats {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.navigation-atlas__stat {
  min-width: 90px;
  padding: 12px 14px;
  border: 1px solid var(--sb-border-subtle);
  border-radius: 16px;
  background: linear-gradient(180deg, rgb(255 255 255 / 0.98), rgb(246 250 255 / 0.94));

  span,
  strong {
    display: block;
  }

  span {
    color: var(--sb-text-secondary);
    font-size: 11px;
  }

  strong {
    margin-top: 6px;
    font-size: 20px;
    line-height: 1;
  }
}

.navigation-atlas__group-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 4px;

  strong {
    font-size: 14px;
  }

  span {
    color: var(--sb-text-tertiary);
    font-size: 12px;
    letter-spacing: 0.08em;
    text-transform: uppercase;
  }
}

.navigation-atlas__grid {
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));

  &.is-compact {
    grid-template-columns: 1fr;
  }
}

.navigation-atlas__card {
  padding: 18px;
  border-radius: var(--sb-radius-lg);
  border: 1px solid var(--sb-border-color);
  background:
    radial-gradient(circle at top right, rgb(30 94 255 / 0.08), transparent 28%),
    linear-gradient(180deg, rgb(255 255 255 / 0.96), rgb(248 251 255 / 0.9)),
    var(--sb-surface-strong);
  box-shadow: var(--sb-shadow-sm);
  position: relative;

  &.is-active {
    border-color: rgb(30 94 255 / 0.2);
    box-shadow: 0 16px 34px rgb(17 44 84 / 0.12);
  }

  &.is-compact {
    display: grid;
    gap: 14px;
    padding: 16px 18px;
    border-radius: var(--sb-radius-md);
  }

  &::before {
    content: "";
    position: absolute;
    inset: 0 auto 0 0;
    width: 3px;
    background: linear-gradient(180deg, var(--sb-primary-color), transparent 70%);
    opacity: 0.7;
  }
}

.navigation-atlas__card-head,
.navigation-atlas__section {
  width: 100%;
  border: none;
  background: transparent;
  text-align: left;
  cursor: pointer;
  padding: 0;
}

.navigation-atlas__card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;

  strong {
    display: block;
    font-size: 18px;
    line-height: 1.2;
  }

  small {
    color: var(--sb-text-tertiary);
    font-size: 11px;
    letter-spacing: 0.08em;
    text-transform: uppercase;
  }

  &.is-compact {
    align-items: center;

    strong {
      font-size: 16px;
    }
  }
}

.navigation-atlas__title-wrap {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.navigation-atlas__badge {
  display: inline-flex;
  margin-bottom: 8px;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgb(30 94 255 / 0.08);
  color: var(--sb-primary-color);
  font-size: 11px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.navigation-atlas__summary {
  margin: 14px 0 0;
  color: var(--sb-text-secondary);
  line-height: 1.7;
}

.navigation-atlas__section {
  padding: 12px 14px;
  border-radius: 14px;
  background: linear-gradient(180deg, rgb(248 250 255 / 0.92), rgb(244 248 255 / 0.88));
  border: 1px solid rgb(30 94 255 / 0.08);
  transition: 180ms ease;

  &:hover {
    border-color: rgb(30 94 255 / 0.18);
    background: rgb(255 255 255 / 0.96);
    transform: translateY(-1px);
  }

  &.is-compact {
    width: auto;
    padding: 7px 12px;
    border-radius: 12px;
    background: rgb(255 255 255 / 0.88);
  }

  strong,
  span {
    display: block;
  }

  strong {
    font-size: 14px;
  }

  span {
    margin-top: 6px;
    color: var(--sb-text-secondary);
    font-size: 12px;
    line-height: 1.6;
  }
}

.navigation-atlas__section-list.is-compact {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

@media (max-width: 860px) {
  .navigation-atlas__header {
    flex-direction: column;
  }

  .navigation-atlas__stats {
    justify-content: flex-start;
  }
}
</style>
