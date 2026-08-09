<script setup lang="ts">
import { computed } from "vue";
import { useRouter } from "vue-router";
import BaseCard from "@/components/base/BaseCard.vue";
import BasePage from "@/components/base/BasePage.vue";
import NavigationAtlas from "@/components/platform/NavigationAtlas.vue";
import { APP_CLUSTER_LABELS, resolveAppMeta } from "@/config/app-taxonomy";
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
  { label: "应用域", value: String(menuStore.navigation.length) },
  { label: "控制面", value: String(menuStore.navigation.reduce((sum, item) => sum + item.children.length, 0)) },
  { label: "租户", value: tenantStore.currentTenant?.name ?? "平台空间" },
  { label: "隔离", value: tenantStore.currentTenant?.isolationMode ?? "PLATFORM" }
]);

const groupedSignals = computed(() => {
  const map = new Map<string, MenuNavItem[]>();

  for (const app of menuStore.navigation) {
    const meta = resolveAppMeta(app.code, app.title);
    if (meta.cluster === "overview") {
      continue;
    }
    const bucket = map.get(meta.cluster) ?? [];
    bucket.push(app);
    map.set(meta.cluster, bucket);
  }

  return Array.from(map.entries()).map(([key, items]) => ({
    key,
    label: APP_CLUSTER_LABELS[key as keyof typeof APP_CLUSTER_LABELS],
    summary: items.map((item) => item.title).slice(0, 3).join(" / "),
    totalApps: items.length,
    totalSections: items.reduce((sum, item) => sum + item.children.length, 0)
  }));
});

const featuredApps = computed(() =>
  menuStore.navigation
    .filter((item) => item.code !== "dashboard")
    .slice(0, 4)
    .map((item) => ({
      id: item.id,
      title: item.title,
      badge: resolveAppMeta(item.code, item.title).badge,
      summary: resolveAppMeta(item.code, item.title).summary,
      target: item.children[0] ?? item
    }))
);

function openItem(item: NavigableItem) {
  router.push({
    path: item.path,
    query: item.query
  });
}
</script>

<template>
  <BasePage
    eyebrow="平台指挥台"
    title="平台指挥台"
    description="先定位应用域，再进入具体控制面。首页只负责导航与上下文。"
  >
    <div class="dashboard-grid">
      <div class="dashboard-main">
        <BaseCard tone="accent">
          <div class="hero-board__stats hero-board__stats--flat">
            <article v-for="stat in summaryStats" :key="stat.label" class="hero-board__stat">
              <span>{{ stat.label }}</span>
              <strong>{{ stat.value }}</strong>
            </article>
          </div>
        </BaseCard>

        <BaseCard tone="soft">
          <template #header>
            <div class="section-header">
              <h3>优先入口</h3>
              <p>把最常进入的治理域放到前面，先进入应用，再沿二级控制面深入。</p>
            </div>
          </template>

          <div class="featured-grid">
            <button v-for="item in featuredApps" :key="item.id" class="featured-card" @click="openItem(item.target)">
              <span class="featured-card__badge">{{ item.badge }}</span>
              <strong>{{ item.title }}</strong>
              <p>{{ item.summary }}</p>
            </button>
          </div>
        </BaseCard>

        <BaseCard tone="contrast">
        <template #header>
          <div class="section-header">
            <h3>应用目录</h3>
            <p>按治理域整理全部应用与控制面，优先帮助你判断功能在哪，而不是在左侧层层试点。</p>
          </div>
        </template>

        <NavigationAtlas :apps="menuStore.navigation" compact @select="openItem" />
        </BaseCard>
      </div>

      <div class="dashboard-side">
        <BaseCard tone="soft">
          <template #header>
            <div class="section-header">
              <h3>治理域分布</h3>
              <p>每个治理域承载的应用数和控制面数。</p>
            </div>
          </template>

          <div class="cluster-list">
            <article v-for="group in groupedSignals" :key="group.key" class="cluster-card">
              <strong>{{ group.label }}</strong>
              <span>{{ group.totalApps }} 个应用 / {{ group.totalSections }} 个控制面</span>
              <p>{{ group.summary }}</p>
            </article>
          </div>
        </BaseCard>

        <BaseCard tone="contrast">
          <template #header>
            <div class="section-header">
              <h3>当前上下文</h3>
              <p>始终明确当前所在租户空间和底座规模。</p>
            </div>
          </template>

          <div class="stat-list">
            <article v-for="stat in summaryStats" :key="stat.label" class="stat-item">
              <span>{{ stat.label }}</span>
              <strong>{{ stat.value }}</strong>
            </article>
          </div>
        </BaseCard>
      </div>
    </div>
  </BasePage>
</template>

<style scoped lang="scss">
.dashboard-grid,
.dashboard-main,
.dashboard-side,
.stat-list,
.cluster-list,
.featured-grid {
  display: grid;
  gap: 18px;
}

.dashboard-grid {
  grid-template-columns: minmax(0, 1.4fr) 320px;
}

.hero-board__stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.hero-board__stats--flat {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.hero-board__stat,
.featured-card,
.cluster-card {
  border: 1px solid var(--sb-border-color);
  border-radius: var(--sb-radius-md);
  background: linear-gradient(180deg, rgb(255 255 255 / 0.98), rgb(246 250 255 / 0.92));
}

.hero-board__stat {
  padding: 16px 18px;

  span,
  strong {
    display: block;
  }

  span {
    color: var(--sb-text-tertiary);
    font-size: 12px;
  }

  strong {
    margin-top: 8px;
    font-size: 28px;
    line-height: 1;
  }
}

.section-header {
  h3 {
    margin: 0;
  }

  p {
    margin: 6px 0 0;
    color: var(--sb-text-secondary);
    line-height: 1.7;
  }
}

.featured-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.featured-card {
  display: grid;
  gap: 8px;
  padding: 18px;
  text-align: left;
  cursor: pointer;
  transition: 180ms ease;

  &:hover {
    transform: translateY(-1px);
    box-shadow: var(--sb-shadow-sm);
    border-color: rgb(30 94 255 / 0.18);
  }

  strong {
    font-size: 17px;
  }

  p {
    margin: 0;
    color: var(--sb-text-secondary);
    line-height: 1.65;
  }
}

.featured-card__badge {
  display: inline-flex;
  width: fit-content;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgb(30 94 255 / 0.08);
  color: var(--sb-primary-color);
  font-size: 11px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.stat-item,
.cluster-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 14px;
}

.cluster-card {
  padding: 16px 18px;

  span {
    color: var(--sb-text-secondary);
    font-size: 13px;
  }

  strong {
    font-size: 15px;
  }

  p {
    margin: 0;
    color: var(--sb-text-tertiary);
    font-size: 12px;
    line-height: 1.6;
  }
}

.stat-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 14px 0;
  border-bottom: 1px solid var(--sb-border-color);

  &:last-child {
    border-bottom: none;
    padding-bottom: 0;
  }

  span {
    color: var(--sb-text-secondary);
    font-size: 13px;
  }

  strong {
    font-size: 15px;
  }
}

@media (max-width: 1180px) {
  .dashboard-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 860px) {
  .featured-grid,
  .hero-board__stats,
  .hero-board__stats--flat {
    grid-template-columns: 1fr;
  }
}
</style>
