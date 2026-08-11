<script setup lang="ts">
import { computed, ref } from "vue";
import { Expand, Fold, Grid, Search } from "@element-plus/icons-vue";
import { useRoute, useRouter } from "vue-router";
import NavigationAtlas from "@/components/platform/NavigationAtlas.vue";
import TenantSwitcher from "@/components/platform/TenantSwitcher.vue";
import { resolveAppIcon } from "@/config/app-icons";
import { resolveAppMeta, resolveSectionMeta } from "@/config/app-taxonomy";
import { useAuthStore } from "@/stores/modules/auth";
import { useLocaleStore } from "@/stores/modules/locale";
import { useMenuStore, type MenuNavItem } from "@/stores/modules/menu";
import { useTenantStore } from "@/stores/modules/tenant";
import { buildPortalLoginQuery } from "@/utils/portal-context";

type QuickEntry = {
  value: string;
  target: MenuNavItem;
};

type NavigableTarget = {
  path: string;
  query: Record<string, string>;
};

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const localeStore = useLocaleStore();
const menuStore = useMenuStore();
const tenantStore = useTenantStore();
const directoryVisible = ref(false);
const railCollapsed = ref(false);

if (!tenantStore.currentTenant) {
  tenantStore.bootstrap();
}

const apps = computed(() => menuStore.navigation);

const appGroups = computed(() => {
  const groups = new Map<string, { key: string; label: string; items: MenuNavItem[] }>();

  for (const app of apps.value) {
    const meta = resolveAppMeta(app.code, app.title);
    const current = groups.get(meta.cluster) ?? {
      key: meta.cluster,
      label: localeStore.t(`cluster.${meta.cluster}`),
      items: []
    };
    current.items.push(app);
    groups.set(meta.cluster, current);
  }

  return Array.from(groups.values());
});

const currentApp = computed<MenuNavItem | null>(() => {
  for (const item of apps.value) {
    if (isSectionActive(item)) {
      return item;
    }
  }
  return apps.value[0] ?? null;
});

const currentSection = computed<MenuNavItem | null>(() => {
  const app = currentApp.value;
  if (!app) {
    return null;
  }
  return app.children.find((item) => item.path && isExactActive(item)) ?? app;
});

const currentSectionMeta = computed(() =>
  resolveSectionMeta({
    appCode: currentApp.value?.code,
    fallbackTitle: currentSection.value?.title,
    query: currentSection.value?.query,
    path: currentSection.value?.path
  })
);

const quickEntries = computed<QuickEntry[]>(() => {
  const entries: QuickEntry[] = [];

  for (const app of apps.value) {
    entries.push({
      value: app.title,
      target: app.children.find((item) => item.path) ?? app
    });

    for (const section of app.children) {
      entries.push({
        value: `${app.title} / ${section.title}`,
        target: section
      });
    }
  }

  return entries;
});

const userInitial = computed(() =>
  (authStore.currentUser?.nickname || authStore.currentUser?.username || "U").trim().slice(0, 1).toUpperCase()
);

function go(item: MenuNavItem) {
  router.push({ path: item.path, query: item.query });
}

function switchApp(app: MenuNavItem) {
  go(app.children.find((item) => item.path) ?? app);
}

function isExactActive(item: MenuNavItem): boolean {
  if (route.path !== item.path) {
    return false;
  }
  return Object.entries(item.query).every(([key, value]) => String(route.query[key] ?? "") === value);
}

function isSectionActive(item: MenuNavItem): boolean {
  return isExactActive(item) || item.children.some((child) => isSectionActive(child));
}

function querySearch(keyword: string, callback: (items: QuickEntry[]) => void) {
  const normalized = keyword.trim().toLowerCase();
  callback(
    quickEntries.value
      .filter((item) => !normalized || item.value.toLowerCase().includes(normalized))
      .slice(0, 10)
  );
}

function handleQuickSelect(item: Record<string, unknown>) {
  const target = item.target as MenuNavItem | undefined;
  if (target) {
    go(target);
  }
}

function handleAtlasSelect(item: NavigableTarget) {
  directoryVisible.value = false;
  router.push({ path: item.path, query: item.query });
}

async function handleLogout() {
  await authStore.logout();
  router.push({ path: "/login", query: buildPortalLoginQuery() });
}

async function handleRefreshNavigation() {
  await menuStore.refresh();
  if (!menuStore.routesReady) {
    router.replace({ path: route.path, query: route.query, hash: route.hash });
  }
}
</script>

<template>
  <div class="console-shell" :class="{ 'console-shell--collapsed': railCollapsed }">
    <aside class="console-shell__rail">
      <div class="console-shell__brand">
        <span class="console-shell__brand-mark"><Grid /></span>
        <strong>{{ localeStore.t("shell.brandTitle") }}</strong>
      </div>

      <nav class="console-shell__navigation" aria-label="主导航">
        <section v-for="group in appGroups" :key="group.key" class="console-shell__rail-group">
          <span class="console-shell__rail-label">{{ group.label }}</span>
          <div v-for="app in group.items" :key="app.id" class="console-shell__app-group">
            <button
              class="console-shell__rail-app"
              :class="{ 'is-active': currentApp?.id === app.id }"
              :title="app.title"
              @click="switchApp(app)"
            >
              <span class="console-shell__rail-icon">
                <component :is="resolveAppIcon(app.code)" />
              </span>
              <strong>{{ app.title }}</strong>
            </button>

            <div v-if="currentApp?.id === app.id && app.children.length" class="console-shell__submenu">
              <button
                v-for="item in app.children.filter((child) => child.path)"
                :key="item.id"
                class="console-shell__submenu-item"
                :class="{ 'is-active': isExactActive(item) }"
                @click="go(item)"
              >
                {{ item.title }}
              </button>
            </div>
          </div>
        </section>
      </nav>

      <div v-if="menuStore.loading || menuStore.lastError" class="console-shell__nav-state">
        <span>{{ menuStore.loading ? "导航加载中" : "导航加载失败" }}</span>
        <el-button link :loading="menuStore.loading" @click="handleRefreshNavigation">重试</el-button>
      </div>

      <footer class="console-shell__rail-footer">
        <button class="console-shell__directory-trigger" title="功能索引" @click="directoryVisible = true">
          <Grid class="console-shell__directory-icon" />
          <span>{{ localeStore.t("shell.directory") }}</span>
        </button>
        <button
          class="console-shell__collapse-trigger"
          :title="railCollapsed ? '展开导航' : '收起导航'"
          :aria-label="railCollapsed ? '展开导航' : '收起导航'"
          @click="railCollapsed = !railCollapsed"
        >
          <component :is="railCollapsed ? Expand : Fold" />
        </button>
      </footer>
    </aside>

    <div class="console-shell__main">
      <header class="console-shell__topbar">
        <div class="console-shell__title-block">
          <span>{{ currentApp?.title ?? "平台" }}</span>
          <h1>{{ currentSectionMeta.label }}</h1>
        </div>

        <div class="console-shell__topbar-actions">
          <div id="console-context-actions" class="console-shell__context-actions" />

          <div class="console-shell__quick-jump">
            <el-autocomplete
              popper-class="console-shell__quick-popper"
              :fetch-suggestions="querySearch"
              placeholder="搜索功能"
              clearable
              @select="handleQuickSelect"
            >
              <template #prefix>
                <Search />
              </template>
              <template #default="{ item }">
                <span class="console-shell__quick-option">{{ item.value }}</span>
              </template>
            </el-autocomplete>
          </div>

          <el-tooltip :content="localeStore.t('shell.directory')" placement="bottom">
            <el-button class="console-shell__directory-button" :icon="Grid" aria-label="功能索引" @click="directoryVisible = true" />
          </el-tooltip>

          <el-select
            class="console-shell__locale"
            :model-value="localeStore.locale"
            @change="(value) => localeStore.setLocale(String(value) as 'zh-CN' | 'en-US')"
          >
            <el-option label="中文" value="zh-CN" />
            <el-option label="English" value="en-US" />
          </el-select>

          <TenantSwitcher />

          <el-dropdown trigger="click">
            <button class="console-shell__user-trigger" aria-label="用户菜单">
              <span class="console-shell__user-avatar">{{ userInitial }}</span>
              <span class="console-shell__user-name">
                {{ authStore.currentUser?.nickname || authStore.currentUser?.username || "用户" }}
              </span>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="console-shell__workspace">
        <router-view />
      </main>
    </div>

    <el-drawer v-model="directoryVisible" title="功能索引" size="480px">
      <NavigationAtlas :apps="apps" :active-code="currentApp?.code" compact @select="handleAtlasSelect" />
    </el-drawer>
  </div>
</template>

<style scoped lang="scss">
.console-shell {
  height: 100vh;
  display: grid;
  grid-template-columns: 224px minmax(0, 1fr);
  overflow: hidden;
  background: var(--sb-shell-main);
  transition: grid-template-columns 160ms ease;
}

.console-shell--collapsed {
  grid-template-columns: 64px minmax(0, 1fr);

  .console-shell__brand {
    justify-content: center;
    padding: 0;
  }

  .console-shell__brand strong,
  .console-shell__rail-label,
  .console-shell__rail-app strong,
  .console-shell__submenu,
  .console-shell__directory-trigger,
  .console-shell__nav-state span {
    display: none;
  }

  .console-shell__navigation {
    padding: 10px 7px;
  }

  .console-shell__rail-app {
    grid-template-columns: 1fr;
    justify-items: center;
    padding: 4px;
  }

  .console-shell__collapse-trigger {
    width: 100%;
    flex-basis: 100%;
    justify-content: center;
    padding: 0;
    border-left: 0;
  }
}

.console-shell__rail {
  display: flex;
  flex-direction: column;
  min-height: 0;
  border-right: 1px solid #2d3640;
  background: #1c232b;
  color: #f7f9fb;
}

.console-shell__brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 58px;
  padding: 0 16px;
  border-bottom: 1px solid #2d3640;

  strong {
    min-width: 0;
    overflow: hidden;
    font-size: 14px;
    font-weight: 650;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.console-shell__brand-mark {
  width: 26px;
  height: 26px;
  display: grid;
  flex: 0 0 26px;
  place-items: center;
  border-radius: 4px;
  background: #2f6bd8;
  color: #fff;
  font-size: 12px;
  font-weight: 700;

  svg {
    width: 15px;
    height: 15px;
  }
}

.console-shell__navigation {
  flex: 1;
  min-height: 0;
  padding: 14px 10px 18px;
  overflow: auto;
}

.console-shell__rail-group {
  display: grid;
  gap: 3px;
  margin-bottom: 16px;
}

.console-shell__rail-label {
  padding: 0 8px 5px;
  color: #83909d;
  font-size: 10px;
}

.console-shell__app-group {
  display: grid;
  gap: 2px;
}

.console-shell__rail-app,
.console-shell__submenu-item,
.console-shell__directory-trigger,
.console-shell__user-trigger {
  border: none;
  font-family: inherit;
  cursor: pointer;
}

.console-shell__rail-app {
  width: 100%;
  min-height: 38px;
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr);
  align-items: center;
  gap: 9px;
  padding: 4px 10px;
  border-radius: 5px;
  background: transparent;
  color: #ced5dc;
  text-align: left;

  &:hover {
    background: #252e37;
    color: #fff;
  }

  &.is-active {
    background: #303a45;
    color: #fff;
  }

  strong {
    min-width: 0;
    overflow: hidden;
    font-size: 13px;
    font-weight: 600;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.console-shell__rail-icon {
  width: 18px;
  height: 18px;
  display: grid;
  place-items: center;
  color: #aeb8c3;

  svg {
    width: 16px;
    height: 16px;
  }

  .is-active & {
    color: #79a6ff;
  }
}

.console-shell__submenu {
  display: grid;
  gap: 1px;
  margin: 2px 0 5px 31px;
  padding-left: 8px;
  border-left: 1px solid #37414c;
}

.console-shell__submenu-item {
  min-height: 30px;
  padding: 5px 8px;
  border-radius: 4px;
  background: transparent;
  color: #9fa9b4;
  overflow: hidden;
  font-size: 12px;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;

  &:hover,
  &.is-active {
    background: #252e37;
    color: #fff;
  }

  &.is-active {
    box-shadow: inset 2px 0 #5d8fe8;
  }
}

.console-shell__nav-state {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 14px;
  border-top: 1px solid #2d3640;
  color: #f0b35a;
  font-size: 12px;
}

.console-shell__directory-trigger {
  min-width: 0;
  height: 43px;
  flex: 1;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 0 17px;
  background: #1c232b;
  color: #cbd2d9;
  font-size: 13px;
  text-align: left;

  &:hover {
    background: #252e37;
    color: #fff;
  }
}

.console-shell__rail-footer {
  display: flex;
  align-items: center;
  border-top: 1px solid #2d3640;
}

.console-shell__collapse-trigger {
  width: 43px;
  height: 43px;
  display: grid;
  flex: 0 0 43px;
  place-items: center;
  border: 0;
  border-left: 1px solid #2d3640;
  background: #1c232b;
  color: #9fa9b4;
  cursor: pointer;

  &:hover {
    background: #252e37;
    color: #fff;
  }

  svg {
    width: 16px;
    height: 16px;
  }
}

.console-shell__directory-icon {
  width: 16px;
}

.console-shell__main {
  min-width: 0;
  min-height: 0;
  display: grid;
  grid-template-rows: 58px minmax(0, 1fr);
  overflow: hidden;
}

.console-shell__topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 0 18px 0 20px;
  border-bottom: 1px solid var(--sb-border-color);
  background: #fff;
}

.console-shell__title-block {
  min-width: 140px;

  span {
    display: block;
    color: #778396;
    font-size: 10px;
    line-height: 1.2;
  }

  h1 {
    margin: 2px 0 0;
    overflow: hidden;
    font-size: 16px;
    line-height: 1.2;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.console-shell__topbar-actions {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.console-shell__context-actions {
  display: flex;
  align-items: center;
}

.console-shell__quick-jump {
  flex: 0 1 260px;
  width: min(260px, 22vw);
}

.console-shell__quick-jump :deep(.el-autocomplete) {
  width: 100%;
}

.console-shell__quick-jump :deep(.el-input__wrapper) {
  border-radius: 5px;
  background: #f5f6f8;
  box-shadow: 0 0 0 1px #e2e6eb inset;
}

.console-shell__quick-option {
  display: block;
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.console-shell__directory-button {
  width: 32px;
  padding: 0;
}

.console-shell__locale {
  width: 92px;
}

.console-shell__user-trigger {
  min-height: 32px;
  max-width: 150px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 8px 0 10px;
  border-left: 1px solid var(--sb-border-color);
  background: transparent;
  color: var(--sb-text-primary);
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.console-shell__user-avatar {
  width: 26px;
  height: 26px;
  display: grid;
  flex: 0 0 26px;
  place-items: center;
  border-radius: 50%;
  background: #e9edf3;
  color: #445264;
  font-size: 11px;
  font-weight: 700;
}

.console-shell__workspace {
  min-width: 0;
  min-height: 0;

  --sb-workspace-x: 20px;

  padding: 16px var(--sb-workspace-x) 28px;
  overflow: auto;
}

.console-shell :deep(.el-drawer__header) {
  margin-bottom: 0;
  padding: 16px 18px;
  border-bottom: 1px solid var(--sb-border-color);
}

.console-shell :deep(.el-drawer__body) {
  padding: 16px;
}

@media (max-width: 1120px) {
  .console-shell {
    grid-template-columns: 64px minmax(0, 1fr);
  }

  .console-shell__brand {
    justify-content: center;
    padding: 0;

    strong {
      display: none;
    }
  }

  .console-shell__navigation {
    padding: 10px 7px;
  }

  .console-shell__rail-label,
  .console-shell__rail-app strong,
  .console-shell__submenu,
  .console-shell__directory-trigger span,
  .console-shell__nav-state span {
    display: none;
  }

  .console-shell__collapse-trigger {
    display: none;
  }

  .console-shell__rail-app {
    display: grid;
    grid-template-columns: 1fr;
    justify-items: center;
    padding: 4px;
  }

  .console-shell__directory-trigger {
    justify-content: center;
    padding: 0;
  }

  .console-shell__quick-jump {
    flex-basis: 220px;
    width: min(220px, 24vw);
  }
}

@media (max-width: 760px) {
  .console-shell {
    grid-template-columns: minmax(0, 1fr);
  }

  .console-shell__rail {
    display: none;
  }

  .console-shell__topbar {
    gap: 12px;
    padding: 0 12px;
  }

  .console-shell__title-block {
    min-width: 0;
    flex: 1;

    h1 {
      font-size: 15px;
    }
  }

  .console-shell__topbar-actions {
    flex: 0 0 auto;
  }

  .console-shell__quick-jump,
  .console-shell__locale {
    display: none !important;
  }

  .console-shell__context-actions {
    order: 1;
  }

  .console-shell__workspace {
    --sb-workspace-x: 12px;

    padding: 12px;
  }

  .console-shell__user-trigger {
    width: 32px;
    justify-content: center;
    padding: 0;
    border-left: 0;
  }

  .console-shell__user-name {
    display: none;
  }

  .console-shell :deep(.el-drawer) {
    width: 92vw !important;
  }
}
</style>
