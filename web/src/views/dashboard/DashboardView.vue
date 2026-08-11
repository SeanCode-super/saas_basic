<script setup lang="ts">
import { computed } from "vue";
import { useRouter } from "vue-router";
import NavigationAtlas from "@/components/platform/NavigationAtlas.vue";
import { resolveAppMeta } from "@/config/app-taxonomy";
import { useMenuStore, type MenuNavItem } from "@/stores/modules/menu";
import { useTenantStore } from "@/stores/modules/tenant";

type NavigableItem = {
  path: string;
  query: Record<string, string>;
};

const router = useRouter();
const menuStore = useMenuStore();
const tenantStore = useTenantStore();

const summaryStats = computed(() => [
  { label: "应用", value: String(menuStore.navigation.length) },
  { label: "功能", value: String(menuStore.navigation.reduce((sum, item) => sum + item.children.length, 0)) },
  { label: "当前租户", value: tenantStore.currentTenant?.name ?? "平台空间" },
  { label: "隔离模式", value: tenantStore.currentTenant?.isolationMode ?? "PLATFORM" }
]);

const featuredApps = computed(() =>
  menuStore.navigation
    .filter((item) => item.code !== "dashboard")
    .slice(0, 8)
    .map((item) => ({
      id: item.id,
      title: item.title,
      badge: resolveAppMeta(item.code, item.title).badge,
      target: item.children[0] ?? item
    }))
);

function openItem(item: NavigableItem | MenuNavItem) {
  router.push({ path: item.path, query: item.query });
}
</script>

<template>
  <section class="dashboard" aria-label="平台总览">
    <div class="dashboard__stats">
      <article v-for="stat in summaryStats" :key="stat.label" class="dashboard__stat">
        <span>{{ stat.label }}</span>
        <strong>{{ stat.value }}</strong>
      </article>
    </div>

    <section class="dashboard__section">
      <header class="dashboard__section-header">
        <h2>常用功能</h2>
      </header>
      <div class="dashboard__shortcuts">
        <button v-for="item in featuredApps" :key="item.id" class="dashboard__shortcut" @click="openItem(item.target)">
          <span>{{ item.badge }}</span>
          <strong>{{ item.title }}</strong>
        </button>
      </div>
    </section>

    <section class="dashboard__section">
      <header class="dashboard__section-header">
        <h2>全部功能</h2>
      </header>
      <NavigationAtlas :apps="menuStore.navigation" compact @select="openItem" />
    </section>
  </section>
</template>

<style scoped lang="scss">
.dashboard {
  display: grid;
  gap: 16px;
}

.dashboard__stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border: 1px solid var(--sb-border-color);
  border-radius: var(--sb-radius-md);
  background: #fff;
}

.dashboard__stat {
  min-width: 0;
  padding: 15px 18px;
  border-right: 1px solid var(--sb-border-color);

  &:last-child {
    border-right: 0;
  }

  span,
  strong {
    display: block;
  }

  span {
    color: var(--sb-text-tertiary);
    font-size: 12px;
  }

  strong {
    margin-top: 6px;
    overflow: hidden;
    font-size: 20px;
    line-height: 1.2;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.dashboard__section {
  border: 1px solid var(--sb-border-color);
  border-radius: var(--sb-radius-md);
  background: #fff;
}

.dashboard__section-header {
  padding: 13px 16px;
  border-bottom: 1px solid var(--sb-border-color);

  h2 {
    margin: 0;
    font-size: 15px;
  }
}

.dashboard__shortcuts {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  padding: 8px;
}

.dashboard__shortcut {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border: 1px solid transparent;
  border-radius: 5px;
  background: transparent;
  color: var(--sb-text-primary);
  font-family: inherit;
  text-align: left;
  cursor: pointer;

  &:hover {
    border-color: var(--sb-border-color);
    background: #f7f8fa;
  }

  span {
    width: 30px;
    height: 30px;
    display: grid;
    flex: 0 0 30px;
    place-items: center;
    border-radius: 5px;
    background: #eaf1ff;
    color: #245fca;
    font-size: 9px;
    font-weight: 700;
  }

  strong {
    min-width: 0;
    overflow: hidden;
    font-size: 13px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.dashboard__section :deep(.navigation-atlas) {
  padding: 16px;
}

@media (max-width: 900px) {
  .dashboard__stats,
  .dashboard__shortcuts {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .dashboard__stat:nth-child(2) {
    border-right: 0;
  }

  .dashboard__stat:nth-child(-n + 2) {
    border-bottom: 1px solid var(--sb-border-color);
  }
}
</style>
