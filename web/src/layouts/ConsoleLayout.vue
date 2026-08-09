<script setup lang="ts">
import { computed, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import NavigationAtlas from "@/components/platform/NavigationAtlas.vue";
import TenantSwitcher from "@/components/platform/TenantSwitcher.vue";
import { APP_CLUSTER_LABELS, resolveAppMeta, resolveSectionMeta } from "@/config/app-taxonomy";
import { useAuthStore } from "@/stores/modules/auth";
import { useLocaleStore } from "@/stores/modules/locale";
import { useMenuStore, type MenuNavItem } from "@/stores/modules/menu";
import { useTenantStore } from "@/stores/modules/tenant";

type QuickEntry = {
  value: string;
  title: string;
  hint: string;
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

if (!tenantStore.currentTenant) {
  tenantStore.bootstrap();
}

const apps = computed(() => menuStore.navigation);

const appGroups = computed(() => {
  const groups = new Map<string, { key: string; label: string; items: MenuNavItem[] }>();

  for (const app of apps.value) {
    const meta = resolveAppMeta(app.code, app.title);
    const key = meta.cluster;
    const current = groups.get(key) ?? {
      key,
      label: localeStore.t(`cluster.${key}`),
      items: []
    };
    current.items.push(app);
    groups.set(key, current);
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
  const children = app.children.filter((item) => item.path);
  return children.find((item) => isExactActive(item)) ?? app;
});

const currentSubmenu = computed(() => {
  const app = currentApp.value;
  if (!app) {
    return [];
  }
  const children = app.children.filter((item) => item.path);
  return children.length > 0 ? children : [app];
});

const currentAppMeta = computed(() => resolveAppMeta(currentApp.value?.code, currentApp.value?.title));

const currentSectionMeta = computed(() =>
  resolveSectionMeta({
    appCode: currentApp.value?.code,
    fallbackTitle: currentSection.value?.title,
    query: currentSection.value?.query,
    path: currentSection.value?.path
  })
);

const breadcrumbItems = computed(() =>
  [localeStore.t(`cluster.${currentAppMeta.value.cluster}`), currentApp.value?.title, currentSectionMeta.value.label].filter(Boolean)
);

const workspaceSignals = computed(() => [
  { label: "当前租户", value: tenantStore.currentTenant?.name ?? "平台空间" },
  { label: "租户套餐", value: tenantStore.currentTenant?.plan ?? "SUPREME" },
  { label: "隔离模型", value: tenantStore.currentTenant?.isolationMode ?? "PLATFORM" },
  { label: "权限数量", value: String(authStore.currentUser?.permissions.length ?? 0) }
]);

const navigationModeLabel = computed(() => {
  if (menuStore.loading) {
    return localeStore.t("shell.refreshing");
  }
  if (menuStore.lastError) {
    return localeStore.t("shell.unavailable");
  }
  return localeStore.t("shell.realtime");
});

const quickEntries = computed<QuickEntry[]>(() => {
  const entries: QuickEntry[] = [];

  for (const app of apps.value) {
    entries.push({
      value: app.title,
      title: app.title,
      hint: resolveAppMeta(app.code, app.title).summary,
      target: app.children.find((item) => item.path) ?? app
    });

    for (const section of app.children) {
      entries.push({
        value: `${app.title} / ${section.title}`,
        title: section.title,
        hint: resolveSectionMeta({
          appCode: app.code,
          fallbackTitle: section.title,
          query: section.query,
          path: section.path
        }).description,
        target: section
      });
    }
  }

  return entries;
});

function go(item: MenuNavItem) {
  router.push({
    path: item.path,
    query: item.query
  });
}

function switchApp(app: MenuNavItem) {
  const firstChild = app.children.find((item) => item.path);
  go(firstChild ?? app);
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
  if (!normalized) {
    callback(quickEntries.value.slice(0, 8));
    return;
  }
  callback(
    quickEntries.value
      .filter((item) => `${item.value} ${item.hint}`.toLowerCase().includes(normalized))
      .slice(0, 10)
  );
}

function handleQuickSelect(item: Record<string, unknown>) {
  const target = item.target as MenuNavItem | undefined;
  if (target) {
    go(target);
  }
}

function openDirectory() {
  directoryVisible.value = true;
}

function handleAtlasSelect(item: NavigableTarget) {
  directoryVisible.value = false;
  router.push({
    path: item.path,
    query: item.query
  });
}

async function handleLogout() {
  await authStore.logout();
  router.push("/login");
}

async function handleRefreshNavigation() {
  await menuStore.refresh();
  if (!menuStore.routesReady) {
    router.replace({
      path: route.path,
      query: route.query,
      hash: route.hash
    });
  }
}
</script>

<template>
  <div class="console-shell">
    <aside class="console-shell__rail">
      <div class="console-shell__brand">
        <span class="console-shell__brand-mark">SB</span>
        <div>
          <span class="console-shell__brand-eyebrow">{{ localeStore.t("shell.brandEyebrow") }}</span>
          <strong>{{ localeStore.t("shell.brandTitle") }}</strong>
          <p>企业级开发底座</p>
        </div>
      </div>

      <div class="console-shell__rail-groups">
        <section v-for="group in appGroups" :key="group.key" class="console-shell__rail-group">
          <span class="console-shell__rail-label">{{ group.label }}</span>
          <button
            v-for="app in group.items"
            :key="app.id"
            class="console-shell__rail-app"
            :class="{ 'is-active': currentApp?.id === app.id }"
            @click="switchApp(app)"
          >
            <span class="console-shell__rail-badge">{{ resolveAppMeta(app.code, app.title).badge }}</span>
            <div>
              <strong>{{ app.title }}</strong>
            </div>
          </button>
        </section>
      </div>

      <button class="console-shell__directory-trigger" @click="openDirectory">
        <span>{{ localeStore.t("shell.directory") }}</span>
        <small>{{ localeStore.t("shell.directoryHint") }}</small>
      </button>

      <section class="console-shell__rail-status">
        <article>
          <span>{{ localeStore.t("shell.navMode") }}</span>
          <strong>{{ navigationModeLabel }}</strong>
        </article>
        <article>
          <span>{{ localeStore.t("shell.currentApp") }}</span>
          <strong>{{ currentApp?.title ?? "平台总览" }}</strong>
        </article>
      </section>
    </aside>

    <div class="console-shell__main">
      <header class="console-shell__topbar">
        <div class="console-shell__breadcrumbs">
          <span
            v-for="(crumb, index) in breadcrumbItems"
            :key="`${crumb}-${index}`"
            class="console-shell__crumb"
          >
            {{ crumb }}
          </span>
        </div>

        <div class="console-shell__topbar-row">
          <div class="console-shell__title-block">
            <div class="console-shell__surface-chips">
              <span class="console-shell__surface-chip">{{ currentAppMeta.badge }}</span>
            </div>
            <h1>{{ currentSectionMeta.label }}</h1>
            <p>{{ currentSectionMeta.description }}</p>
          </div>

          <div class="console-shell__topbar-actions">
            <el-autocomplete
              class="console-shell__quick-jump"
              popper-class="console-shell__quick-popper"
              :fetch-suggestions="querySearch"
              placeholder="搜索应用、控制面或策略"
              clearable
              @select="handleQuickSelect"
            >
              <template #default="{ item }">
                <div class="console-shell__quick-option">
                  <strong>{{ item.value }}</strong>
                  <span>{{ item.hint }}</span>
                </div>
              </template>
            </el-autocomplete>

            <el-button plain @click="openDirectory">{{ localeStore.t("shell.directory") }}</el-button>
            <el-select :model-value="localeStore.locale" size="small" style="width: 110px" @change="(value) => localeStore.setLocale(String(value) as 'zh-CN' | 'en-US')">
              <el-option label="中文" value="zh-CN" />
              <el-option label="English" value="en-US" />
            </el-select>
            <TenantSwitcher />

            <div class="console-shell__user">
              <div>
                <strong>{{ authStore.currentUser?.nickname }}</strong>
                <p>{{ authStore.currentUser?.username }}</p>
              </div>
              <el-button text @click="handleLogout">退出登录</el-button>
            </div>
          </div>
        </div>
      </header>

      <div class="console-shell__content-shell">
        <div class="console-shell__content">
        <aside class="console-shell__sidebar">
          <section class="console-shell__panel">
            <span class="console-shell__eyebrow">{{ currentAppMeta.badge }}</span>
            <strong>{{ currentApp?.title ?? "平台总览" }}</strong>
            <p>{{ currentAppMeta.summary }}</p>
          </section>

          <section class="console-shell__panel">
            <header class="console-shell__panel-header">
              <strong>控制面目录</strong>
              <span>{{ currentSubmenu.length }}</span>
            </header>
            <nav class="console-shell__section-list">
              <button
                v-for="item in currentSubmenu"
                :key="item.id"
                class="console-shell__section-item"
                :class="{ 'is-active': isExactActive(item) }"
                @click="go(item)"
              >
                <strong>{{ item.title }}</strong>
              </button>
            </nav>
          </section>

          <section v-if="menuStore.loading || menuStore.lastError" class="console-shell__panel console-shell__degraded-panel">
            <header class="console-shell__panel-header">
              <strong>导航状态</strong>
              <el-button text :loading="menuStore.loading" @click="handleRefreshNavigation">重试加载</el-button>
            </header>
            <p>
              {{
                menuStore.loading
                  ? "正在刷新实时导航。"
                  : "导航加载失败，当前不会使用缓存或降级菜单。"
              }}
            </p>
            <small v-if="menuStore.lastError">{{ menuStore.lastError }}</small>
          </section>

          <section class="console-shell__panel console-shell__signal-panel">
            <header class="console-shell__panel-header">
              <strong>工作上下文</strong>
            </header>
            <article v-for="signal in workspaceSignals" :key="signal.label" class="console-shell__signal">
              <span>{{ signal.label }}</span>
              <strong>{{ signal.value }}</strong>
            </article>
          </section>
        </aside>

        <main class="console-shell__workspace">
          <router-view />
        </main>
      </div>
      </div>
    </div>

    <el-drawer v-model="directoryVisible" title="功能索引" size="560px">
      <div class="console-shell__drawer-copy">
        <strong>全部应用与控制面</strong>
        <p>这里展示当前真实导航结构。点击任意应用或控制面，会直接进入对应页面。</p>
      </div>
      <NavigationAtlas :apps="apps" :active-code="currentApp?.code" compact @select="handleAtlasSelect" />
    </el-drawer>
  </div>
</template>

<style scoped lang="scss">
.console-shell {
  height: 100vh;
  max-height: 100vh;
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  overflow: hidden;
  background:
    radial-gradient(circle at top left, rgb(30 94 255 / 0.18), transparent 20%),
    linear-gradient(180deg, #101828 0%, #0f1726 100%);
}

.console-shell__rail {
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-height: 0;
  padding: 18px 16px;
  border-right: 1px solid var(--sb-shell-rail-border);
  background:
    radial-gradient(circle at top, rgb(116 150 245 / 0.16), transparent 28%),
    linear-gradient(180deg, rgb(255 255 255 / 0.03), rgb(255 255 255 / 0)),
    var(--sb-shell-rail);
}

.console-shell__brand {
  display: flex;
  align-items: center;
  gap: 12px;

  strong {
    display: block;
    font-size: 17px;
    color: var(--sb-shell-rail-text);
  }

  p {
    margin: 4px 0 0;
    color: var(--sb-shell-rail-muted);
    font-size: 12px;
  }
}

.console-shell__brand-eyebrow {
  display: inline-flex;
  margin-bottom: 4px;
  color: #9ec0ff;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.16em;
}

.console-shell__brand-mark {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  border-radius: 14px;
  background: linear-gradient(135deg, #2e7dff 0%, #8caeff 100%);
  color: white;
  font-weight: 800;
}

.console-shell__rail-groups {
  display: grid;
  gap: 18px;
  min-height: 0;
  overflow: auto;
}

.console-shell__rail-group {
  display: grid;
  gap: 10px;
}

.console-shell__rail-label {
  color: var(--sb-shell-rail-muted);
  font-size: 11px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.console-shell__rail-app {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 10px;
  align-items: center;
  padding: 10px 12px;
  border: 1px solid rgb(255 255 255 / 0.04);
  border-radius: 16px;
  background: rgb(255 255 255 / 0.03);
  cursor: pointer;
  text-align: left;
  transition: 180ms ease;
  position: relative;

  &:hover,
  &.is-active {
    border-color: rgb(255 255 255 / 0.08);
    background: linear-gradient(180deg, rgb(255 255 255 / 0.08), rgb(255 255 255 / 0.04));
    box-shadow: inset 0 0 0 1px rgb(30 94 255 / 0.28);
  }

  &.is-active::before {
    content: "";
    position: absolute;
    inset: 10px auto 10px 0;
    width: 3px;
    border-radius: 999px;
    background: linear-gradient(180deg, #5d93ff, transparent 80%);
  }

  strong {
    display: block;
  }

  strong {
    font-size: 14px;
    color: var(--sb-shell-rail-text);
  }
}

.console-shell__rail-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 54px;
  padding: 7px 8px;
  border-radius: 12px;
  background: rgb(255 255 255 / 0.08);
  color: #b7d2ff;
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.console-shell__directory-trigger {
  margin-top: auto;
  padding: 14px 16px;
  border: 1px dashed rgb(255 255 255 / 0.14);
  border-radius: 18px;
  background: rgb(255 255 255 / 0.04);
  text-align: left;
  cursor: pointer;

  span,
  small {
    display: block;
  }

  span {
    font-weight: 700;
    color: var(--sb-shell-rail-text);
  }

  small {
    margin-top: 4px;
    color: var(--sb-shell-rail-muted);
    line-height: 1.5;
  }
}

.console-shell__rail-status {
  display: grid;
  gap: 10px;
  padding: 14px 16px;
  border: 1px solid rgb(255 255 255 / 0.08);
  border-radius: 18px;
  background: rgb(255 255 255 / 0.04);

  article {
    display: grid;
    gap: 4px;
  }

  span,
  strong {
    display: block;
  }

  span {
    color: var(--sb-shell-rail-muted);
    font-size: 11px;
  }

  strong {
    color: var(--sb-shell-rail-text);
    font-size: 13px;
  }
}

.console-shell__main {
  min-width: 0;
  min-height: 0;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  overflow: hidden;
  background: var(--sb-shell-main);
}

.console-shell__topbar {
  flex-shrink: 0;
  padding: 18px 24px 16px;
  border-bottom: 1px solid var(--sb-border-color);
  background:
    radial-gradient(circle at top right, rgb(30 94 255 / 0.08), transparent 22%),
    linear-gradient(180deg, rgb(255 255 255 / 0.86), rgb(250 252 255 / 0.84));
  backdrop-filter: blur(12px);
}

.console-shell__breadcrumbs {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.console-shell__crumb {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--sb-text-secondary);
  font-size: 12px;

  &::after {
    content: "/";
    color: var(--sb-text-tertiary);
  }

  &:last-child {
    color: var(--sb-text-primary);
    font-weight: 600;

    &::after {
      display: none;
    }
  }
}

.console-shell__topbar-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.console-shell__title-block {
  min-width: 0;

  h1 {
    margin: 0;
    font-size: 28px;
    line-height: 1.1;
  }

  p {
    margin: 8px 0 0;
    max-width: 720px;
    color: var(--sb-text-secondary);
    line-height: 1.65;
  }
}

.console-shell__surface-chips {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.console-shell__surface-chip {
  display: inline-flex;
  align-items: center;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgb(15 27 45 / 0.05);
  color: var(--sb-text-secondary);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
}

.console-shell__topbar-actions,
.console-shell__user {
  display: flex;
  align-items: center;
  gap: 12px;
}

.console-shell__quick-jump {
  width: 300px;
}

.console-shell__quick-jump :deep(.el-input__wrapper) {
  border-radius: 14px;
  box-shadow: 0 0 0 1px rgb(15 27 45 / 0.08) inset;
}

.console-shell__quick-jump :deep(.el-input__prefix) {
  color: var(--sb-text-tertiary);
}

.console-shell__quick-option {
  display: grid;
  gap: 4px;
  padding: 2px 0;

  strong,
  span {
    display: block;
  }

  strong {
    font-size: 13px;
  }

  span {
    color: var(--sb-text-secondary);
    font-size: 12px;
    line-height: 1.5;
  }
}

.console-shell__user {
  padding-left: 12px;
  border-left: 1px solid var(--sb-border-color);

  strong {
    display: block;
  }

  p {
    margin: 4px 0 0;
    color: var(--sb-text-secondary);
    font-size: 12px;
  }
}

.console-shell__content {
  min-width: 0;
  min-height: 0;
  height: 100%;
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 20px;
  padding: 20px;
  max-width: var(--sb-content-max);
  width: 100%;
  margin: 0 auto;
}

.console-shell__content-shell {
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  padding: 0 10px 24px;
}

.console-shell__sidebar {
  display: grid;
  min-height: 0;
  align-content: start;
  gap: 14px;
  overflow: auto;
  padding-right: 4px;
}

.console-shell__panel {
  display: grid;
  gap: 12px;
  padding: 18px;
  border: 1px solid var(--sb-border-color);
  border-radius: var(--sb-radius-lg);
  background:
    radial-gradient(circle at top right, rgb(30 94 255 / 0.06), transparent 28%),
    linear-gradient(180deg, rgb(255 255 255 / 0.96), rgb(247 250 254 / 0.92)),
    white;
  box-shadow: var(--sb-shadow-sm);
  position: relative;

  strong {
    display: block;
  }

  p {
    margin: 0;
    color: var(--sb-text-secondary);
    line-height: 1.65;
  }

  &::before {
    content: "";
    position: absolute;
    inset: 0 0 auto;
    height: 3px;
    background: var(--sb-card-highlight);
    opacity: 0.5;
  }
}

.console-shell__panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;

  span {
    color: var(--sb-text-tertiary);
    font-size: 12px;
  }
}

.console-shell__eyebrow {
  display: inline-flex;
  color: var(--sb-primary-color);
  font-size: 11px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.console-shell__section-list {
  display: grid;
  gap: 10px;
}

.console-shell__section-item {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid transparent;
  border-radius: 14px;
  background: var(--sb-panel-muted);
  text-align: left;
  cursor: pointer;
  transition: 180ms ease;

  &:hover,
  &.is-active {
    border-color: rgb(30 94 255 / 0.16);
    background: rgb(255 255 255 / 0.98);
    box-shadow: inset 0 0 0 1px rgb(30 94 255 / 0.08);
  }

  strong {
    display: block;
    font-size: 14px;
  }
}

.console-shell__signal-panel {
  gap: 0;
}

.console-shell__degraded-panel {
  p,
  small {
    margin: 0;
    line-height: 1.6;
  }

  p {
    color: var(--sb-text-secondary);
  }

  small {
    margin-top: 8px;
    color: var(--sb-text-tertiary);
    display: block;
  }
}

.console-shell__signal {
  padding: 12px 0;
  border-bottom: 1px solid var(--sb-border-color);

  &:last-child {
    border-bottom: none;
    padding-bottom: 0;
  }

  span,
  strong {
    display: block;
  }

  span {
    color: var(--sb-text-secondary);
    font-size: 12px;
  }

  strong {
    margin-top: 6px;
    font-size: 15px;
  }
}

.console-shell__workspace {
  min-width: 0;
  min-height: 0;
  overflow: auto;
  padding-bottom: 12px;
}

.console-shell__drawer-copy {
  margin-bottom: 18px;

  strong {
    display: block;
    font-size: 16px;
  }

  p {
    margin: 8px 0 0;
    color: var(--sb-text-secondary);
    line-height: 1.65;
  }
}

:global(.console-shell__quick-popper) {
  border-radius: 18px;
  border: 1px solid var(--sb-border-subtle);
  box-shadow: var(--sb-shadow-lg);
  padding: 8px;
  background:
    radial-gradient(circle at top right, rgb(30 94 255 / 0.08), transparent 24%),
    linear-gradient(180deg, rgb(255 255 255 / 0.98), rgb(246 250 255 / 0.95));
  backdrop-filter: blur(12px);
}

:global(.console-shell__quick-popper .el-autocomplete-suggestion__list) {
  padding: 0;
}

:global(.console-shell__quick-popper li) {
  margin: 0;
  padding: 10px 12px;
  border-radius: 14px;
  transition: 160ms ease;
}

:global(.console-shell__quick-popper li:hover),
:global(.console-shell__quick-popper li.highlighted) {
  background: rgb(30 94 255 / 0.08);
}

@media (max-width: 1400px) {
  .console-shell {
    grid-template-columns: 220px minmax(0, 1fr);
  }

  .console-shell__content {
    grid-template-columns: 280px minmax(0, 1fr);
  }
}

@media (max-width: 1180px) {
  .console-shell {
    height: auto;
    max-height: none;
    grid-template-columns: 1fr;
    overflow: visible;
  }

  .console-shell__rail {
    border-right: none;
    border-bottom: 1px solid var(--sb-shell-rail-border);
  }

  .console-shell__content {
    height: auto;
    grid-template-columns: 1fr;
  }

  .console-shell__content-shell {
    overflow: visible;
    padding: 0 0 20px;
  }

  .console-shell__sidebar {
    overflow: visible;
    padding-right: 0;
  }

  .console-shell__main {
    min-height: auto;
    overflow: visible;
  }

  .console-shell__workspace {
    overflow: visible;
  }

  .console-shell__topbar-row {
    flex-direction: column;
  }

  .console-shell__topbar-actions {
    width: 100%;
    flex-wrap: wrap;
  }

  .console-shell__quick-jump {
    width: 100%;
  }
}
</style>
