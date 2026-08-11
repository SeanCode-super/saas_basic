<script setup lang="ts">
import { computed } from "vue";
import { ArrowRight } from "@element-plus/icons-vue";
import { useRouter } from "vue-router";
import { resolveAppIcon } from "@/config/app-icons";
import { useAuthStore } from "@/stores/modules/auth";
import { useMenuStore, type MenuNavItem } from "@/stores/modules/menu";
import { useTenantStore } from "@/stores/modules/tenant";

type NavigableItem = {
  path: string;
  query: Record<string, string>;
};

const router = useRouter();
const authStore = useAuthStore();
const menuStore = useMenuStore();
const tenantStore = useTenantStore();

const packageLabels: Record<string, string> = {
  SUPREME: "至尊版",
  ENTERPRISE: "企业版"
};

const isolationLabels: Record<string, string> = {
  PLATFORM: "平台级",
  SHARED_SCHEMA: "共享 Schema",
  DEDICATED_SCHEMA: "独立 Schema",
  DEDICATED_DATABASE: "独立数据库"
};

const sessionLabels: Record<string, string> = {
  ONLINE: "在线",
  OFFLINE: "离线",
  LOCKED: "已锁定"
};

function localize(value: string | undefined, labels: Record<string, string>, fallback = "-") {
  return value ? (labels[value] ?? value) : fallback;
}

const summaryStats = computed(() => [
  { label: "已接入应用", value: String(menuStore.navigation.length) },
  { label: "可用功能", value: String(menuStore.navigation.reduce((sum, item) => sum + item.children.length, 0)) },
  { label: "当前空间", value: tenantStore.currentTenant?.name ?? "平台空间" },
  { label: "隔离模式", value: localize(tenantStore.currentTenant?.isolationMode, isolationLabels, "平台级") }
]);

const featuredApps = computed(() =>
  menuStore.navigation
    .filter((item) => item.code !== "dashboard")
    .slice(0, 8)
    .map((item) => ({
      id: item.id,
      title: item.title,
      code: item.code,
      target: item.children[0] ?? item
    }))
);

const environmentRows = computed(() => [
  { label: "租户名称", value: tenantStore.currentTenant?.name ?? "平台空间" },
  { label: "租户编码", value: tenantStore.currentTenant?.code ?? "platform" },
  { label: "服务套餐", value: localize(tenantStore.currentTenant?.plan, packageLabels) },
  { label: "数据隔离", value: localize(tenantStore.currentTenant?.isolationMode, isolationLabels, "平台级") },
  { label: "当前用户", value: authStore.currentUser?.nickname || authStore.currentUser?.username || "-" },
  { label: "会话状态", value: localize(authStore.currentUser?.sessionStatus, sessionLabels) }
]);

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

    <div class="dashboard__workspace">
      <section class="dashboard__section">
        <header class="dashboard__section-header">
          <h2>快捷入口</h2>
        </header>
        <div class="dashboard__shortcuts">
          <button v-for="item in featuredApps" :key="item.id" class="dashboard__shortcut" @click="openItem(item.target)">
            <span class="dashboard__shortcut-icon"><component :is="resolveAppIcon(item.code)" /></span>
            <span class="dashboard__shortcut-copy">
              <strong>{{ item.title }}</strong>
            </span>
            <ArrowRight class="dashboard__shortcut-arrow" />
          </button>
        </div>
      </section>

      <section class="dashboard__section dashboard__environment">
        <header class="dashboard__section-header">
          <h2>当前环境</h2>
        </header>
        <dl>
          <div v-for="row in environmentRows" :key="row.label">
            <dt>{{ row.label }}</dt>
            <dd>{{ row.value }}</dd>
          </div>
        </dl>
      </section>
    </div>
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

.dashboard__workspace {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(280px, 1fr);
  gap: 16px;
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
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.dashboard__shortcut {
  min-width: 0;
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) 16px;
  align-items: center;
  gap: 11px;
  min-height: 58px;
  padding: 10px 16px;
  border: 0;
  border-bottom: 1px solid #edf0f3;
  background: transparent;
  color: var(--sb-text-primary);
  font-family: inherit;
  text-align: left;
  cursor: pointer;

  &:nth-child(odd) {
    border-right: 1px solid #edf0f3;
  }

  &:nth-last-child(-n + 2) {
    border-bottom: 0;
  }

  &:hover {
    background: #f4f7fb;
  }

  .dashboard__shortcut-icon {
    width: 32px;
    height: 32px;
    display: grid;
    place-items: center;
    border-radius: 5px;
    background: #eef2f7;
    color: #3f5f85;

    svg {
      width: 16px;
      height: 16px;
    }
  }

  .dashboard__shortcut-copy {
    min-width: 0;
    display: grid;
    gap: 2px;
  }

  strong {
    overflow: hidden;
    font-size: 13px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

}

.dashboard__shortcut-arrow {
  width: 14px;
  color: #a1aab6;
}

.dashboard__environment dl {
  margin: 0;
  padding: 4px 16px 12px;
}

.dashboard__environment dl > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 43px;
  border-bottom: 1px solid #edf0f3;

  &:last-child {
    border-bottom: 0;
  }
}

.dashboard__environment dt {
  color: var(--sb-text-tertiary);
  font-size: 12px;
}

.dashboard__environment dd {
  margin: 0;
  overflow: hidden;
  color: var(--sb-text-primary);
  font-size: 13px;
  font-weight: 600;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 900px) {
  .dashboard__stats,
  .dashboard__shortcuts {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .dashboard__workspace {
    grid-template-columns: minmax(0, 1fr);
  }

  .dashboard__stat:nth-child(2) {
    border-right: 0;
  }

  .dashboard__stat:nth-child(-n + 2) {
    border-bottom: 1px solid var(--sb-border-color);
  }
}

@media (max-width: 560px) {
  .dashboard__shortcuts {
    grid-template-columns: minmax(0, 1fr);
  }

  .dashboard__shortcut,
  .dashboard__shortcut:nth-child(odd),
  .dashboard__shortcut:nth-last-child(-n + 2) {
    border-right: 0;
    border-bottom: 1px solid #edf0f3;
  }

  .dashboard__shortcut:last-child {
    border-bottom: 0;
  }
}
</style>
