<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import {
  ArrowDown,
  ArrowRight,
  Bell,
  Check,
  Expand,
  Fold,
  Grid,
  Menu,
  Monitor,
  Operation,
  RefreshRight,
  Search,
  Setting,
  Cpu,
  DataBoard,
  Document,
  Printer,
  SwitchButton,
  User,
  Promotion,
  DocumentChecked,
  Switch as SwitchIcon
} from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { useRoute, useRouter } from "vue-router";
import NavigationAtlas from "@/components/platform/NavigationAtlas.vue";
import NotificationCenter from "@/components/platform/NotificationCenter.vue";
import AiAssistantDrawer from "@/components/platform/AiAssistantDrawer.vue";
import CommandPaletteDialog from "@/components/platform/CommandPaletteDialog.vue";
import TagsView from "@/components/platform/TagsView.vue";
import TenantSwitcher from "@/components/platform/TenantSwitcher.vue";
import ProStatusDot from "@/components/pro/ProStatusDot.vue";
import { resolveAppIcon } from "@/config/app-icons";
import { resolveAppMeta, resolveSectionMeta } from "@/config/app-taxonomy";
import { useAuthStore } from "@/stores/modules/auth";
import { useLocaleStore } from "@/stores/modules/locale";
import { useMenuStore, type MenuNavItem } from "@/stores/modules/menu";
import { useTenantStore } from "@/stores/modules/tenant";
import { buildPortalLoginQuery } from "@/utils/portal-context";


export type LayoutMode = "mix" | "side" | "top";

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

// Dual-Nav (Top-Side Mix) vs Classic Full Sidebar vs Top Menu Layout
const layoutMode = ref<LayoutMode>(
  (localStorage.getItem("sb_layout_mode") as LayoutMode) || "mix"
);
const showBreadcrumbs = ref<boolean>(
  localStorage.getItem("sb_show_breadcrumbs") !== "false"
);
const sidebarAccordion = ref<boolean>(
  localStorage.getItem("sb_sidebar_accordion") === "true"
);
const settingsDrawerVisible = ref(false);
const commandPaletteVisible = ref(false);
const enableWatermark = ref(localStorage.getItem("sb_enable_watermark") !== "false");

const watermarkContent = computed(() => {
  if (!enableWatermark.value) return "";
  const u = authStore.currentUser?.nickname || authStore.currentUser?.username || "SaaS用户";
  const t = authStore.currentUser?.tenantCode || "platform";
  return `${u} [${t}]`;
});

function toggleWatermark(val: any) {
  enableWatermark.value = Boolean(val);
  localStorage.setItem("sb_enable_watermark", String(val));
}


function handleGlobalKeydown(e: KeyboardEvent) {
  if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
    e.preventDefault();
    commandPaletteVisible.value = !commandPaletteVisible.value;
  }
}

onMounted(() => {
  window.addEventListener("keydown", handleGlobalKeydown);
});

onUnmounted(() => {
  window.removeEventListener("keydown", handleGlobalKeydown);
});

const activeCluster = ref<string>("overview");

const expandedAppIds = ref<number[]>([]);
const hasInitializedNav = ref(false);
const aiAssistantRef = ref<InstanceType<typeof AiAssistantDrawer> | null>(null);

// 顶层父级目录展开/收起状态管理 (用于左侧侧栏一级折叠树模式)
const expandedClusterKeys = ref<string[]>(
  JSON.parse(
    localStorage.getItem("sb_expanded_clusters") ||
      '["overview", "foundation", "runtime", "engineering", "assurance", "governance", "delivery", "audit"]'
  )
);

function isClusterExpanded(key: string): boolean {
  return expandedClusterKeys.value.includes(key);
}

function toggleClusterExpanded(key: string) {
  if (expandedClusterKeys.value.includes(key)) {
    expandedClusterKeys.value = expandedClusterKeys.value.filter((k) => k !== key);
  } else {
    if (sidebarAccordion.value) {
      expandedClusterKeys.value = [key];
    } else {
      expandedClusterKeys.value = [...expandedClusterKeys.value, key];
    }
  }
  localStorage.setItem("sb_expanded_clusters", JSON.stringify(expandedClusterKeys.value));
}

function ensureClusterExpanded(key: string) {
  if (!expandedClusterKeys.value.includes(key)) {
    if (sidebarAccordion.value) {
      expandedClusterKeys.value = [key];
    } else {
      expandedClusterKeys.value = [...expandedClusterKeys.value, key];
    }
    localStorage.setItem("sb_expanded_clusters", JSON.stringify(expandedClusterKeys.value));
  }
}

function isClusterActive(group: { key: string; items: MenuNavItem[] }): boolean {
  return group.items.some((item) => isSectionActive(item));
}

function setCategoryPosition(pos: "top" | "side") {
  if (pos === "top") {
    setLayoutMode("mix");
  } else {
    setLayoutMode("side");
  }
}

function openAiAssistant() {
  aiAssistantRef.value?.openDrawer();
}

function openDataScreen() {
  window.open("/screen", "_blank");
}

function openApiDocs() {
  window.open("http://localhost:8080/doc.html", "_blank");
}

function openServerMonitor() {
  router.push("/server-monitor");
}

function isAppExpanded(appId: number): boolean {
  return expandedAppIds.value.includes(appId);
}

function toggleAppExpanded(appId: number) {
  if (expandedAppIds.value.includes(appId)) {
    expandedAppIds.value = expandedAppIds.value.filter((id) => id !== appId);
  } else {
    if (sidebarAccordion.value) {
      expandedAppIds.value = [appId];
    } else {
      expandedAppIds.value = [...expandedAppIds.value, appId];
    }
  }
}

function ensureAppExpanded(appId: number) {
  if (!expandedAppIds.value.includes(appId)) {
    if (sidebarAccordion.value) {
      expandedAppIds.value = [appId];
    } else {
      expandedAppIds.value = [...expandedAppIds.value, appId];
    }
  }
}

if (!tenantStore.currentTenant) {
  tenantStore.bootstrap();
}

const apps = computed(() => menuStore.navigation);

const clusterMetaMap: Record<string, { label: string; icon: string }> = {
  overview: { label: "平台总览", icon: "DataBoard" },
  foundation: { label: "基础治理", icon: "Setting" },
  runtime: { label: "运行支撑", icon: "Cpu" },
  engineering: { label: "工程交付", icon: "Promotion" },
  assurance: { label: "审计追踪", icon: "DocumentChecked" },
  platform: { label: "平台总览", icon: "DataBoard" },
  governance: { label: "基础治理", icon: "Setting" },
  delivery: { label: "工程交付", icon: "Promotion" },
  audit: { label: "审计追踪", icon: "DocumentChecked" }
};

const appGroups = computed(() => {
  const groups = new Map<string, { key: string; label: string; items: MenuNavItem[] }>();

  for (const app of apps.value) {
    const meta = resolveAppMeta(app.code, app.title, app.metaJson);
    const current = groups.get(meta.cluster) ?? {
      key: meta.cluster,
      label: clusterMetaMap[meta.cluster]?.label || localeStore.t(`cluster.${meta.cluster}`),
      items: []
    };
    current.items.push(app);
    groups.set(meta.cluster, current);
  }

  return Array.from(groups.values());
});

const pinnedTopApps = computed(() => {
  return apps.value.filter((app) => app.navPosition === "TOP" || app.navPosition === "BOTH");
});

const isPinnedActive = computed(() => {
  return pinnedTopApps.value.some((app) => isSectionActive(app));
});

const clusterTabs = computed(() => {
  return appGroups.value
    .filter((group) => {
      // 若该分组下所有模块均已作为独立顶部菜单 pinnedTopApps 直接呈现，则不在顶部重复展示该主域大类按钮
      const remaining = group.items.filter((app) => !pinnedTopApps.value.some((p) => p.id === app.id));
      return remaining.length > 0;
    })
    .map((group) => ({
      key: group.key,
      label: clusterMetaMap[group.key]?.label || group.label,
      icon: clusterMetaMap[group.key]?.icon || "Grid",
      items: group.items.filter((app) => app.navPosition !== "SIDE")
    }))
    .filter((group) => group.items.length > 0);
});

const displayedGroups = computed(() => {
  let baseGroups = appGroups.value;
  if (layoutMode.value === "mix") {
    const target = appGroups.value.find((g) => g.key === activeCluster.value);
    const hasSideItems = target && target.items.some((app) => app.navPosition !== "TOP");
    if (target && hasSideItems) {
      baseGroups = [target];
    } else {
      const firstWithSide = appGroups.value.find((g) => g.items.some((app) => app.navPosition !== "TOP"));
      baseGroups = firstWithSide ? [firstWithSide] : appGroups.value;
    }
  }

  return baseGroups
    .map((group) => ({
      ...group,
      items: group.items.filter((app) => app.navPosition !== "TOP")
    }))
    .filter((group) => group.items.length > 0);
});

const topMenuApps = computed(() => {
  return apps.value.filter((app) => app.navPosition !== "SIDE");
});

const currentApp = computed<MenuNavItem | null>(() => {
  for (const item of apps.value) {
    if (isSectionActive(item)) {
      return item;
    }
  }
  return apps.value[0] ?? null;
});

// Sync active cluster and expand active menu on initial navigation or route module change
watch(
  () => currentApp.value,
  (newApp, oldApp) => {
    if (newApp) {
      const meta = resolveAppMeta(newApp.code, newApp.title, newApp.metaJson);
      if (meta?.cluster) {
        activeCluster.value = meta.cluster;
        ensureClusterExpanded(meta.cluster);
      }
      if (!hasInitializedNav.value || (oldApp && oldApp.id !== newApp.id)) {
        ensureAppExpanded(newApp.id);
        hasInitializedNav.value = true;
      }
    }
  },
  { immediate: true }
);

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
  ensureAppExpanded(app.id);
  const target = app.children.find((item) => item.path) ?? app;
  go(target);
}

function handleAppClick(app: MenuNavItem) {
  if (app.children.length > 0) {
    toggleAppExpanded(app.id);
  } else {
    switchApp(app);
  }
}

function switchCluster(key: string) {
  activeCluster.value = key;
  ensureClusterExpanded(key);
  const targetGroup = appGroups.value.find((g) => g.key === key);
  if (targetGroup && targetGroup.items.length > 0) {
    const isCurrentInGroup = targetGroup.items.some((item) => item.id === currentApp.value?.id);
    if (!isCurrentInGroup) {
      const firstApp = targetGroup.items[0];
      const targetNav = firstApp.children.find((child) => child.path) ?? firstApp;
      switchApp(targetNav);
    }
  }
}

const layoutModeOptions = [
  {
    key: "mix" as LayoutMode,
    title: "顶层主域置顶模式 (Top-Side Mix)",
    badge: "企业推荐",
    desc: "基础治理/运行支撑等顶层主域置于顶部，左侧专注对应二级功能"
  },
  {
    key: "side" as LayoutMode,
    title: "左侧多级折叠树模式 (Sidebar Tree)",
    badge: "经典树形",
    desc: "基础治理/运行支撑等顶层主域作为左侧一级折叠菜单，支持整树展开/收起"
  },
  {
    key: "top" as LayoutMode,
    title: "全顶部横向模式 (Full Horizontal)",
    badge: "最大画布",
    desc: "菜单导航完全横向置顶展开，隐藏侧边栏，最大化工作区空间"
  }
];

const layoutModeText = computed(() => {
  const match = layoutModeOptions.find((opt) => opt.key === layoutMode.value);
  return match?.title || "混合双导航模式";
});

function setLayoutMode(mode: LayoutMode) {
  layoutMode.value = mode;
  localStorage.setItem("sb_layout_mode", mode);
  const match = layoutModeOptions.find((opt) => opt.key === mode);
  ElMessage.success(`已切换至「${match?.title || mode}」`);
}

function toggleLayoutMode() {
  const order: LayoutMode[] = ["mix", "side", "top"];
  const nextIndex = (order.indexOf(layoutMode.value) + 1) % order.length;
  setLayoutMode(order[nextIndex]);
}

function toggleBreadcrumbs(val: any) {
  showBreadcrumbs.value = Boolean(val);
  localStorage.setItem("sb_show_breadcrumbs", String(showBreadcrumbs.value));
}

function toggleSidebarAccordion(val: any) {
  sidebarAccordion.value = Boolean(val);
  localStorage.setItem("sb_sidebar_accordion", String(sidebarAccordion.value));
  if (sidebarAccordion.value && currentApp.value) {
    expandedAppIds.value = [currentApp.value.id];
  }
}

function resetLayoutSettings() {
  setLayoutMode("mix");
  toggleBreadcrumbs(true);
  toggleSidebarAccordion(false);
  ElMessage.success("已恢复平台默认布局与显示设置");
}

function isExactActive(item: MenuNavItem): boolean {
  if (!item.path) {
    return false;
  }
  const pathMatches =
    route.path === item.path ||
    (item.path !== "/" && route.path.startsWith(`${item.path}/`));
  if (!pathMatches) {
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

onMounted(() => {
  if (!menuStore.bootstrapped || menuStore.rawMenus.length === 0) {
    void menuStore.bootstrap(true);
  }
  void authStore.bootstrap();
});
</script>

<template>
  <div class="console-shell" :class="{ 'console-shell--collapsed': railCollapsed, [`layout--${layoutMode}`]: true }">
    <!-- 左侧主导航（仅在非顶部模式时显示） -->
    <aside v-if="layoutMode !== 'top'" class="console-shell__rail">
      <div class="console-shell__brand">
        <span class="console-shell__brand-mark">
          <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M12 2L2 7L12 12L22 7L12 2Z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            <path d="M2 17L12 22L22 17" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            <path d="M2 12L12 17L22 12" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </span>
        <div class="console-shell__brand-text">
          <strong>{{ localeStore.t("shell.brandTitle") }}</strong>
          <span class="console-shell__brand-badge">v0.1</span>
        </div>
      </div>

      <nav class="console-shell__navigation" aria-label="主导航">
        <!-- 模式 A：顶层父级置顶模式 (Top-Side Mix) 侧栏展示当前主域下的二级应用 -->
        <template v-if="layoutMode === 'mix'">
          <div class="console-shell__cluster-title">
            {{ clusterMetaMap[activeCluster]?.label || '模块导航' }}
          </div>
          <section v-for="group in displayedGroups" :key="group.key" class="console-shell__rail-group">
            <div v-for="app in group.items" :key="app.id" class="console-shell__app-group">
              <button
                class="console-shell__rail-app"
                :class="{
                  'is-active': isSectionActive(app),
                  'is-expanded': isAppExpanded(app.id),
                  'has-children': app.children.length > 0
                }"
                :title="app.title"
                @click="handleAppClick(app)"
              >
                <el-icon class="console-shell__rail-icon">
                  <component :is="resolveAppIcon(app.code, app.icon)" />
                </el-icon>
                <span class="console-shell__rail-title">{{ app.title }}</span>
                <el-icon v-if="app.children.length" class="console-shell__rail-arrow" :class="{ 'is-open': isAppExpanded(app.id) }">
                  <ArrowRight />
                </el-icon>
              </button>

              <!-- 三级子菜单 -->
              <transition name="submenu-slide">
                <div
                  v-if="isAppExpanded(app.id) && app.children.length"
                  class="console-shell__submenu"
                >
                  <button
                    v-for="item in app.children.filter((child) => child.path)"
                    :key="item.id"
                    class="console-shell__submenu-item"
                    :class="{ 'is-active': isExactActive(item) }"
                    @click="go(item)"
                  >
                    <el-icon v-if="item.icon" class="console-shell__submenu-icon">
                      <component :is="item.icon" />
                    </el-icon>
                    <span v-else class="console-shell__submenu-dot" />
                    <span class="console-shell__submenu-text">{{ item.title }}</span>
                  </button>
                </div>
              </transition>
            </div>
          </section>
        </template>

        <!-- 模式 B：顶层父级在左侧模式 (Sidebar Tree) 顶级主域作为一级可折叠父菜单 -->
        <template v-else-if="layoutMode === 'side'">
          <section v-for="group in displayedGroups" :key="group.key" class="console-shell__cluster-section">
            <!-- 单一无子模块独立大类 (如平台总览) -->
            <div v-if="group.items.length === 1 && (!group.items[0].children || group.items[0].children.length === 0)" class="console-shell__cluster-parent-wrap">
              <button
                class="console-shell__cluster-parent console-shell__cluster-parent--single"
                :class="{ 'is-active': isSectionActive(group.items[0]) }"
                :title="group.label"
                @click="handleAppClick(group.items[0])"
              >
                <el-icon class="cluster-parent-icon">
                  <component :is="clusterMetaMap[group.key]?.icon || 'DataBoard'" />
                </el-icon>
                <span class="cluster-parent-title">{{ group.label }}</span>
              </button>
            </div>

            <!-- 包含多个二级模块的顶层父级 (如基础治理、运行支撑、工程交付、审计追踪) -->
            <div v-else class="console-shell__cluster-group">
              <!-- 一级顶层父菜单按钮：拥有大域图标、展开箭头、支持点击折叠/展开 (去除了臃肿的模块数Badge和卡片边框) -->
              <button
                class="console-shell__cluster-parent"
                :class="{
                  'is-expanded': isClusterExpanded(group.key),
                  'is-active': isClusterActive(group)
                }"
                :title="`${group.label} (点击展开/折叠)`"
                @click="toggleClusterExpanded(group.key)"
              >
                <el-icon class="cluster-parent-icon">
                  <component :is="clusterMetaMap[group.key]?.icon || 'Grid'" />
                </el-icon>
                <span class="cluster-parent-title">{{ group.label }}</span>
                <el-icon class="cluster-parent-arrow" :class="{ 'is-open': isClusterExpanded(group.key) }">
                  <ArrowRight />
                </el-icon>
              </button>

              <!-- 展开后的二级应用列表 (轻量内缩进树) -->
              <transition name="cluster-slide">
                <div v-if="isClusterExpanded(group.key)" class="console-shell__cluster-children">
                  <div v-for="app in group.items" :key="app.id" class="console-shell__app-group">
                    <button
                      class="console-shell__rail-app console-shell__rail-app--nested"
                      :class="{
                        'is-active': isSectionActive(app),
                        'is-expanded': isAppExpanded(app.id),
                        'has-children': app.children.length > 0
                      }"
                      :title="app.title"
                      @click="handleAppClick(app)"
                    >
                      <el-icon class="console-shell__rail-icon">
                        <component :is="resolveAppIcon(app.code, app.icon)" />
                      </el-icon>
                      <span class="console-shell__rail-title">{{ app.title }}</span>
                      <el-icon v-if="app.children.length" class="console-shell__rail-arrow" :class="{ 'is-open': isAppExpanded(app.id) }">
                        <ArrowRight />
                      </el-icon>
                    </button>

                    <!-- 三级子功能菜单 -->
                    <transition name="submenu-slide">
                      <div
                        v-if="isAppExpanded(app.id) && app.children.length"
                        class="console-shell__submenu console-shell__submenu--nested"
                      >
                        <button
                          v-for="item in app.children.filter((child) => child.path)"
                          :key="item.id"
                          class="console-shell__submenu-item"
                          :class="{ 'is-active': isExactActive(item) }"
                          @click="go(item)"
                        >
                          <el-icon v-if="item.icon" class="console-shell__submenu-icon">
                            <component :is="item.icon" />
                          </el-icon>
                          <span v-else class="console-shell__submenu-dot" />
                          <span class="console-shell__submenu-text">{{ item.title }}</span>
                        </button>
                      </div>
                    </transition>
                  </div>
                </div>
              </transition>
            </div>
          </section>
        </template>
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

        <!-- 顶层父级位置便捷切换按钮 (在顶部 vs 在左侧) -->
        <el-tooltip :content="layoutMode === 'side' ? '点击切换为「顶层主域置顶模式」' : '点击切换为「左侧多级折叠树模式」'" placement="top">
          <button
            class="console-shell__footer-layout-btn"
            @click="setCategoryPosition(layoutMode === 'side' ? 'top' : 'side')"
          >
            <el-icon class="footer-layout-icon"><SwitchIcon /></el-icon>
            <span class="footer-layout-text">{{ layoutMode === 'side' ? '顶层在左' : '顶层置顶' }}</span>
          </button>
        </el-tooltip>

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
        <!-- 顶部菜单模式下的品牌展示 -->
        <div v-if="layoutMode === 'top'" class="console-shell__brand console-shell__brand--top">
          <span class="console-shell__brand-mark">
            <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M12 2L2 7L12 12L22 7L12 2Z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              <path d="M2 17L12 22L22 17" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              <path d="M2 12L12 17L22 12" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </span>
          <div class="console-shell__brand-text">
            <strong>{{ localeStore.t("shell.brandTitle") }}</strong>
            <span class="console-shell__brand-badge">v0.1</span>
          </div>
        </div>

        <!-- 侧栏/混合模式下的面包屑导航 -->
        <div v-else-if="showBreadcrumbs" class="console-shell__breadcrumb-bar">
          <span class="console-shell__breadcrumb-app">{{ currentApp?.title ?? "控制台" }}</span>
          <template v-if="currentApp?.title && currentSectionMeta.label && currentApp.title !== currentSectionMeta.label">
            <span class="console-shell__breadcrumb-sep">/</span>
            <span class="console-shell__breadcrumb-page">{{ currentSectionMeta.label }}</span>
          </template>
        </div>
        <div v-else class="console-shell__title-block console-shell__title-block--minimal">
          <h1>{{ currentSectionMeta.label }}</h1>
        </div>

        <!-- 混合双导航模式 (Top-Side Mix) 顶部主域导航 -->
        <div v-if="layoutMode === 'mix'" class="console-shell__top-domains">
          <!-- 独立置顶模块 (如平台总览等直接在顶部点击的模块) -->
          <template v-for="app in pinnedTopApps" :key="`mix-pinned-${app.id}`">
            <button
              v-if="!app.children || app.children.length === 0"
              class="console-shell__domain-tab console-shell__domain-tab--pinned"
              :class="{ 'is-active': isSectionActive(app) }"
              :title="app.title"
              @click="switchApp(app)"
            >
              <el-icon class="console-shell__domain-icon">
                <component :is="resolveAppIcon(app.code, app.icon)" />
              </el-icon>
              <span class="console-shell__domain-label">{{ app.title }}</span>
            </button>
            <el-dropdown
              v-else
              trigger="hover"
              placement="bottom-start"
              popper-class="console-shell__top-dropdown"
            >
              <button
                class="console-shell__domain-tab console-shell__domain-tab--pinned"
                :class="{ 'is-active': isSectionActive(app) }"
                :title="app.title"
                @click="handleAppClick(app)"
              >
                <el-icon class="console-shell__domain-icon">
                  <component :is="resolveAppIcon(app.code, app.icon)" />
                </el-icon>
                <span class="console-shell__domain-label">{{ app.title }}</span>
                <el-icon class="console-shell__top-nav-arrow" style="font-size: 11px; margin-left: 2px;">
                  <ArrowDown />
                </el-icon>
              </button>
              <template #dropdown>
                <el-dropdown-menu class="console-shell__top-flyout">
                  <div class="console-shell__top-flyout-header">
                    <span class="console-shell__top-flyout-icon">
                      <component :is="resolveAppIcon(app.code, app.icon)" />
                    </span>
                    <strong>{{ app.title }}</strong>
                    <span class="console-shell__top-flyout-count">{{ app.children.filter((c) => c.path).length }} 个功能</span>
                  </div>
                  <el-dropdown-item
                    v-for="sub in app.children.filter((c) => c.path)"
                    :key="sub.id"
                    :class="{ 'is-sub-active': isExactActive(sub) }"
                    @click="go(sub)"
                  >
                    <div class="console-shell__flyout-item">
                      <el-icon v-if="sub.icon" class="flyout-icon">
                        <component :is="sub.icon" />
                      </el-icon>
                      <span v-else class="flyout-bullet" />
                      <span class="flyout-label">{{ sub.title }}</span>
                    </div>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>

          <span v-if="pinnedTopApps.length > 0 && clusterTabs.length > 0" class="console-shell__domain-divider" />

          <!-- 主域集群切换按钮 -->
          <button
            v-for="cluster in clusterTabs"
            :key="cluster.key"
            class="console-shell__domain-tab"
            :class="{ 'is-active': !isPinnedActive && activeCluster === cluster.key }"
            @click="switchCluster(cluster.key)"
          >
            <el-icon class="console-shell__domain-icon">
              <component :is="cluster.icon" />
            </el-icon>
            <span class="console-shell__domain-label">{{ cluster.label }}</span>
            <span v-if="cluster.items.length" class="console-shell__domain-badge">{{ cluster.items.length }}</span>
          </button>
        </div>

        <!-- 左侧菜单模式 (Side Menu) 且存在置顶/双显项目时的顶部快捷导航 -->
        <div v-else-if="layoutMode === 'side' && pinnedTopApps.length > 0" class="console-shell__header-pinned-wrap">
          <span class="console-shell__header-vdivider" />
          <div class="console-shell__pinned-pill-group">
            <template v-for="app in pinnedTopApps" :key="`side-pinned-${app.id}`">
              <!-- 无子菜单直接点击导航 -->
              <button
                v-if="!app.children || app.children.length === 0"
                class="console-shell__pinned-pill"
                :class="{ 'is-active': isSectionActive(app) }"
                :title="app.title"
                @click="switchApp(app)"
              >
                <el-icon class="pinned-pill-icon">
                  <component :is="resolveAppIcon(app.code, app.icon)" />
                </el-icon>
                <span class="pinned-pill-text">{{ app.title }}</span>
              </button>

              <!-- 拥有子菜单使用下拉浮层 -->
              <el-dropdown
                v-else
                trigger="hover"
                placement="bottom-start"
                popper-class="console-shell__top-dropdown"
              >
                <button
                  class="console-shell__pinned-pill"
                  :class="{ 'is-active': isSectionActive(app) }"
                  :title="app.title"
                  @click="handleAppClick(app)"
                >
                  <el-icon class="pinned-pill-icon">
                    <component :is="resolveAppIcon(app.code, app.icon)" />
                  </el-icon>
                  <span class="pinned-pill-text">{{ app.title }}</span>
                  <el-icon class="pinned-pill-arrow">
                    <ArrowDown />
                  </el-icon>
                </button>
                <template #dropdown>
                  <el-dropdown-menu class="console-shell__top-flyout">
                    <div class="console-shell__top-flyout-header">
                      <span class="console-shell__top-flyout-icon">
                        <component :is="resolveAppIcon(app.code, app.icon)" />
                      </span>
                      <strong>{{ app.title }}</strong>
                      <span class="console-shell__top-flyout-count">{{ app.children.filter((c) => c.path).length }} 个功能</span>
                    </div>
                    <el-dropdown-item
                      v-for="sub in app.children.filter((c) => c.path)"
                      :key="sub.id"
                      :class="{ 'is-sub-active': isExactActive(sub) }"
                      @click="go(sub)"
                    >
                      <div class="console-shell__flyout-item">
                        <el-icon v-if="sub.icon" class="flyout-icon">
                          <component :is="sub.icon" />
                        </el-icon>
                        <span v-else class="flyout-bullet" />
                        <span class="flyout-label">{{ sub.title }}</span>
                      </div>
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
          </div>
        </div>

        <!-- 顶部菜单模式 (Top Menu) 横向主菜单与下拉 -->
        <div v-else-if="layoutMode === 'top'" class="console-shell__top-menu-bar">
          <template v-for="app in topMenuApps" :key="app.id">
            <!-- 无子菜单直接点击导航 -->
            <button
              v-if="!app.children || app.children.length === 0"
              class="console-shell__top-nav-item"
              :class="{ 'is-active': currentApp?.id === app.id }"
              :title="app.title"
              @click="switchApp(app)"
            >
              <span class="console-shell__top-nav-icon">
                <component :is="resolveAppIcon(app.code, app.icon)" />
              </span>
              <span class="console-shell__top-nav-title">{{ app.title }}</span>
            </button>

            <!-- 拥有子菜单使用下拉浮层 -->
            <el-dropdown
              v-else
              trigger="hover"
              placement="bottom-start"
              popper-class="console-shell__top-dropdown"
            >
              <button
                class="console-shell__top-nav-item"
                :class="{ 'is-active': currentApp?.id === app.id }"
                :title="app.title"
                @click="handleAppClick(app)"
              >
                <span class="console-shell__top-nav-icon">
                  <component :is="resolveAppIcon(app.code, app.icon)" />
                </span>
                <span class="console-shell__top-nav-title">{{ app.title }}</span>
                <el-icon class="console-shell__top-nav-arrow">
                  <ArrowDown />
                </el-icon>
              </button>
              <template #dropdown>
                <el-dropdown-menu class="console-shell__top-flyout">
                  <div class="console-shell__top-flyout-header">
                    <span class="console-shell__top-flyout-icon">
                      <component :is="resolveAppIcon(app.code, app.icon)" />
                    </span>
                    <strong>{{ app.title }}</strong>
                    <span class="console-shell__top-flyout-count">{{ app.children.filter((c) => c.path).length }} 个功能</span>
                  </div>
                  <el-dropdown-item
                    v-for="sub in app.children.filter((c) => c.path)"
                    :key="sub.id"
                    :class="{ 'is-sub-active': isExactActive(sub) }"
                    @click="go(sub)"
                  >
                    <div class="console-shell__flyout-item">
                      <el-icon v-if="sub.icon" class="flyout-icon">
                        <component :is="sub.icon" />
                      </el-icon>
                      <span v-else class="flyout-bullet" />
                      <span class="flyout-label">{{ sub.title }}</span>
                      <span v-if="sub.code === 'iam_role'" class="flyout-badge">矩阵/抽屉</span>
                      <span v-else-if="sub.code === 'iam_user'" class="flyout-badge">诊断器</span>
                    </div>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </div>

        <div class="console-shell__topbar-actions">
          <div id="console-context-actions" class="console-shell__context-actions" />

          <!-- 导航布局快捷切换下拉 -->
          <el-dropdown trigger="click" @command="(cmd: LayoutMode) => setLayoutMode(cmd)">
            <button class="console-shell__icon-btn" :title="`当前布局: ${layoutModeText} (点击切换)`">
              <el-icon class="console-shell__icon-svg">
                <Menu v-if="layoutMode === 'mix'" />
                <Grid v-else-if="layoutMode === 'side'" />
                <Monitor v-else />
              </el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu class="console-shell__layout-quick-menu">
                <div class="layout-quick-menu__title">
                  <el-icon><Operation /></el-icon>
                  <span>导航布局模式</span>
                </div>
                <el-dropdown-item command="mix" :class="{ 'is-selected': layoutMode === 'mix' }">
                  <div class="quick-layout-item">
                    <el-icon><Menu /></el-icon>
                    <span class="quick-layout-label">顶层主域置顶模式 (Top Bar)</span>
                    <el-icon v-if="layoutMode === 'mix'" class="check-icon"><Check /></el-icon>
                  </div>
                </el-dropdown-item>
                <el-dropdown-item command="side" :class="{ 'is-selected': layoutMode === 'side' }">
                  <div class="quick-layout-item">
                    <el-icon><Grid /></el-icon>
                    <span class="quick-layout-label">左侧多级折叠树模式 (Sidebar Tree)</span>
                    <el-icon v-if="layoutMode === 'side'" class="check-icon"><Check /></el-icon>
                  </div>
                </el-dropdown-item>
                <el-dropdown-item command="top" :class="{ 'is-selected': layoutMode === 'top' }">
                  <div class="quick-layout-item">
                    <el-icon><Monitor /></el-icon>
                    <span class="quick-layout-label">全顶部横向模式 (Full Top)</span>
                    <el-icon v-if="layoutMode === 'top'" class="check-icon"><Check /></el-icon>
                  </div>
                </el-dropdown-item>
                <el-dropdown-item divided @click="settingsDrawerVisible = true">
                  <div class="quick-layout-item">
                    <el-icon><Setting /></el-icon>
                    <span class="quick-layout-label">更多布局与偏好设置...</span>
                  </div>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>

          <!-- 系统偏好与布局配置抽屉按钮 -->
          <el-tooltip content="系统布局与偏好设置" placement="bottom">
            <button class="console-shell__icon-btn" aria-label="系统布局与偏好设置" @click="settingsDrawerVisible = true">
              <el-icon class="console-shell__icon-svg">
                <Setting />
              </el-icon>
            </button>
          </el-tooltip>

          <div class="console-shell__quick-jump" @click="commandPaletteVisible = true">
            <el-autocomplete

              popper-class="console-shell__quick-popper"
              :fetch-suggestions="querySearch"
              placeholder="搜索功能或指令..."
              clearable
              @select="handleQuickSelect"
            >
              <template #prefix>
                <Search />
              </template>
              <template #suffix>
                <kbd class="console-shell__quick-kbd">⌘K</kbd>
              </template>
              <template #default="{ item }">
                <span class="console-shell__quick-option">{{ item.value }}</span>
              </template>
            </el-autocomplete>
          </div>

          <!-- 数字大屏驾驶舱快捷按钮 -->
          <el-tooltip content="企业级数字大屏驾驶舱" placement="bottom">
            <button class="console-shell__icon-btn" aria-label="数字驾驶舱" @click="openDataScreen">
              <el-icon class="console-shell__icon-svg"><DataBoard /></el-icon>
            </button>
          </el-tooltip>

          <!-- Knife4j 在线接口文档快捷按钮 -->
          <el-tooltip content="Knife4j 在线接口文档" placement="bottom">
            <button class="console-shell__icon-btn" aria-label="接口文档" @click="openApiDocs">
              <el-icon class="console-shell__icon-svg"><Document /></el-icon>
            </button>
          </el-tooltip>

          <!-- 服务与应用监控快捷按钮 -->
          <el-tooltip content="服务与应用监控 (CPU/JVM/连接池/死锁)" placement="bottom">
            <button class="console-shell__icon-btn" aria-label="服务监控" @click="openServerMonitor">
              <el-icon class="console-shell__icon-svg"><Monitor /></el-icon>
            </button>
          </el-tooltip>

          <el-tooltip content="AI 大模型智能助手 (DeepSeek / GPT-4o)" placement="bottom">
            <button
              class="console-shell__ai-btn"
              type="button"
              aria-label="打开 AI 助手"
              @click="openAiAssistant"
            >
              <el-icon class="ai-sparkle-icon"><Cpu /></el-icon>
              <span class="ai-btn-label">AI 助手</span>
            </button>
          </el-tooltip>

          <NotificationCenter />

          <TenantSwitcher />

          <el-select
            class="console-shell__locale"
            :model-value="localeStore.locale"
            @change="(value: any) => localeStore.setLocale(String(value) as 'zh-CN' | 'en-US')"
          >
            <el-option label="中文" value="zh-CN" />
            <el-option label="EN" value="en-US" />
          </el-select>

          <el-dropdown trigger="click">
            <button class="console-shell__user-trigger" aria-label="用户菜单">
              <span class="console-shell__user-avatar">{{ userInitial }}</span>
              <div class="console-shell__user-details">
                <span class="console-shell__user-name">
                  {{ authStore.currentUser?.nickname || authStore.currentUser?.username || "用户" }}
                </span>
                <span class="console-shell__user-tag">
                  {{ authStore.currentUser?.userType === 'SUPER_ADMIN' ? '超级管理员' : '平台管理员' }}
                </span>
              </div>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>
                  <ProStatusDot status="success" text="当前会话正常 (ONLINE)" />
                </el-dropdown-item>
                <el-dropdown-item @click="router.push('/profile')">
                  <el-icon><User /></el-icon>
                  <span>个人中心 (Profile)</span>
                </el-dropdown-item>
                <el-dropdown-item @click="openServerMonitor">
                  <el-icon><Monitor /></el-icon>
                  <span>服务监控 (APM)</span>
                </el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">
                  <el-icon><SwitchButton /></el-icon>
                  <span>退出登录</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>

          </el-dropdown>
        </div>
      </header>

      <!-- 多标签页 TagsView 导航条 -->
      <TagsView />

      <el-watermark class="console-shell__watermark-wrap" :content="watermarkContent" :font="{ color: 'rgba(0, 0, 0, 0.05)', fontSize: 13 }">
        <main class="console-shell__workspace">
          <!-- 顶部模式下的页头面包屑导航条 -->
          <div v-if="layoutMode === 'top' && showBreadcrumbs" class="console-shell__page-header-bar">
            <div class="console-shell__breadcrumbs">
              <span class="console-shell__breadcrumb-app">{{ currentApp?.title ?? "控制台" }}</span>
              <template v-if="currentApp?.title && currentSectionMeta.label && currentApp.title !== currentSectionMeta.label">
                <span class="console-shell__breadcrumb-sep">/</span>
                <span class="console-shell__breadcrumb-page">{{ currentSectionMeta.label }}</span>
              </template>
            </div>
            <h2 class="console-shell__page-header-title">{{ currentSectionMeta.label }}</h2>
          </div>

          <router-view />
        </main>
      </el-watermark>
    </div>


    <!-- 功能索引抽屉 -->
    <el-drawer v-model="directoryVisible" title="功能索引" size="480px">
      <NavigationAtlas :apps="apps" :active-code="currentApp?.code" compact @select="handleAtlasSelect" />
    </el-drawer>

    <!-- 偏好与系统布局配置抽屉 -->
    <el-drawer
      v-model="settingsDrawerVisible"
      title="系统偏好与布局设置"
      size="420px"
      popper-class="console-shell__settings-drawer"
    >
      <div class="layout-drawer">
        <!-- 模式选择区 -->
        <div class="layout-drawer__section">
          <div class="layout-drawer__section-header">
            <h4>导航布局模式</h4>
            <span class="layout-drawer__section-hint">选择最适合您工作流的菜单呈现方式</span>
          </div>
          
          <div class="layout-mode-cards">
            <!-- 混合双导航模式 -->
            <div
              class="layout-mode-card"
              :class="{ 'is-active': layoutMode === 'mix' }"
              @click="setLayoutMode('mix')"
            >
              <div class="layout-mode-card__preview preview--mix">
                <div class="preview__bar-top">
                  <div class="preview__brand-dot" />
                  <div class="preview__nav-pill" />
                  <div class="preview__nav-pill" />
                </div>
                <div class="preview__split">
                  <div class="preview__rail-sub">
                    <div class="preview__line" />
                    <div class="preview__line is-short" />
                  </div>
                  <div class="preview__body-content">
                    <div class="preview__card" />
                  </div>
                </div>
              </div>
              <div class="layout-mode-card__body">
                <div class="layout-mode-card__title-row">
                  <strong>顶层主域置顶模式 (Top-Side Mix)</strong>
                  <span class="card-badge is-recommend">企业推荐</span>
                </div>
                <p>基础治理、运行支撑、工程交付等顶层主域置于顶部，左侧专注二级功能</p>
              </div>
              <el-icon v-if="layoutMode === 'mix'" class="layout-mode-card__check"><Check /></el-icon>
            </div>

            <!-- 左侧多级折叠树模式 -->
            <div
              class="layout-mode-card"
              :class="{ 'is-active': layoutMode === 'side' }"
              @click="setLayoutMode('side')"
            >
              <div class="layout-mode-card__preview preview--side">
                <div class="preview__rail-full">
                  <div class="preview__brand-dot" />
                  <div class="preview__line" />
                  <div class="preview__line" />
                  <div class="preview__line is-short" />
                </div>
                <div class="preview__body-full">
                  <div class="preview__bar-sub" />
                  <div class="preview__body-content">
                    <div class="preview__card" />
                  </div>
                </div>
              </div>
              <div class="layout-mode-card__body">
                <div class="layout-mode-card__title-row">
                  <strong>左侧多级折叠树模式 (Sidebar Tree)</strong>
                  <span class="card-badge">经典树形</span>
                </div>
                <p>基础治理、运行支撑等顶层主域作为左侧一级折叠父菜单，支持整树展开/收起</p>
              </div>
              <el-icon v-if="layoutMode === 'side'" class="layout-mode-card__check"><Check /></el-icon>
            </div>

            <!-- 顶部菜单模式 -->
            <div
              class="layout-mode-card"
              :class="{ 'is-active': layoutMode === 'top' }"
              @click="setLayoutMode('top')"
            >
              <div class="layout-mode-card__preview preview--top">
                <div class="preview__bar-top is-full">
                  <div class="preview__brand-dot" />
                  <div class="preview__nav-pill" />
                  <div class="preview__nav-pill" />
                  <div class="preview__nav-pill" />
                </div>
                <div class="preview__body-full">
                  <div class="preview__body-content">
                    <div class="preview__card" />
                  </div>
                </div>
              </div>
              <div class="layout-mode-card__body">
                <div class="layout-mode-card__title-row">
                  <strong>顶部菜单模式</strong>
                  <span class="card-badge">最大画布</span>
                </div>
                <p>主菜单置顶展开，完全隐藏侧边栏，最大化横向工作区空间</p>
              </div>
              <el-icon v-if="layoutMode === 'top'" class="layout-mode-card__check"><Check /></el-icon>
            </div>
          </div>
        </div>

        <el-divider />

        <!-- 界面与交互选项 -->
        <div class="layout-drawer__section">
          <div class="layout-drawer__section-header">
            <h4>界面显示与交互特性</h4>
          </div>
          
          <div class="layout-drawer__toggles">
            <div class="layout-toggle-row">
              <div class="toggle-meta">
                <span class="toggle-title">显示面包屑导航</span>
                <span class="toggle-desc">在顶栏或工作区顶部展示当前所在功能层级路径</span>
              </div>
              <el-switch :model-value="showBreadcrumbs" @change="toggleBreadcrumbs" />
            </div>

            <div class="layout-toggle-row">
              <div class="toggle-meta">
                <span class="toggle-title">侧栏手风琴模式</span>
                <span class="toggle-desc">开启时保持单菜单聚焦，展开新菜单自动收起其他；默认关闭，支持多菜单同时展开与自由折叠</span>
              </div>
              <el-switch :model-value="sidebarAccordion" @change="toggleSidebarAccordion" />
            </div>

            <div class="layout-toggle-row">
              <div class="toggle-meta">
                <span class="toggle-title">全局防截屏安全水印</span>
                <span class="toggle-desc">在系统全屏工作区浮现操作人与租户名称的半透明防泄密水印</span>
              </div>
              <el-switch
                :model-value="enableWatermark"
                @change="toggleWatermark"
              />

            </div>
          </div>
        </div>

        <el-divider />

        <!-- 快捷功能 -->
        <div class="layout-drawer__actions">
          <el-button :icon="RefreshRight" plain @click="resetLayoutSettings">恢复平台默认设置</el-button>
          <el-button type="primary" @click="settingsDrawerVisible = false">完成</el-button>
        </div>
      </div>
    </el-drawer>

    <!-- AI 大模型智能助手抽屉 -->
    <AiAssistantDrawer ref="aiAssistantRef" />

    <!-- 全局 ⌘K 盲打搜索与指令中心 -->
    <CommandPaletteDialog v-model:visible="commandPaletteVisible" />
  </div>
</template>


<style scoped lang="scss">
.console-shell {
  height: 100vh;
  display: grid;
  grid-template-columns: 236px minmax(0, 1fr);
  overflow: hidden;
  background: var(--sb-shell-main);
  transition: grid-template-columns 160ms cubic-bezier(0.4, 0, 0.2, 1);

  &.layout--top {
    grid-template-columns: minmax(0, 1fr);
  }
}

.console-shell--collapsed {
  grid-template-columns: 58px minmax(0, 1fr);

  .console-shell__brand {
    justify-content: center;
    padding: 0;
  }

  .console-shell__brand-text,
  .console-shell__rail-label,
  .console-shell__rail-title,
  .console-shell__rail-badge,
  .console-shell__rail-arrow,
  .console-shell__submenu,
  .console-shell__directory-trigger span,
  .console-shell__footer-layout-btn,
  .console-shell__nav-state span,
  .console-shell__cluster-title,
  .cluster-parent-title,
  .cluster-parent-arrow,
  .console-shell__cluster-children {
    display: none;
  }

  .console-shell__navigation {
    padding: 10px 6px;
  }

  .console-shell__rail-app,
  .console-shell__cluster-parent {
    justify-content: center;
    padding: 6px 0;
    border-radius: 6px;
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
  border-right: 1px solid var(--sb-shell-rail-border);
  background: var(--sb-shell-rail);
  color: var(--sb-shell-rail-text);
  box-shadow: 1px 0 3px rgb(0 0 0 / 0.02);
}

.console-shell__brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 52px;
  padding: 0 14px;
  border-bottom: 1px solid var(--sb-shell-rail-border);
  background: #fff;

  &--top {
    height: 100%;
    padding: 0 18px 0 0;
    margin-right: 12px;
    border-bottom: 0;
    border-right: 1px solid var(--sb-border-color);
    background: transparent;
    flex: 0 0 auto;
  }
}

.console-shell__brand-mark {
  width: 28px;
  height: 28px;
  display: grid;
  flex: 0 0 28px;
  place-items: center;
  border-radius: 7px;
  background: linear-gradient(135deg, #2563eb 0%, #4f46e5 100%);
  color: #fff;
  box-shadow: 0 2px 6px rgb(37 99 235 / 0.25);
  border: 1px solid rgb(255 255 255 / 0.2);

  svg {
    width: 15px;
    height: 15px;
  }
}

.console-shell__brand-text {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 6px;

  strong {
    overflow: hidden;
    font-size: 14px;
    font-weight: 700;
    color: #0f172a;
    letter-spacing: -0.015em;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.console-shell__brand-badge {
  padding: 1px 6px;
  border-radius: 9999px;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  color: #64748b;
  font-family: var(--sb-font-mono);
  font-size: 10px;
  font-weight: 600;
  line-height: 1;
}

.console-shell__navigation {
  flex: 1;
  min-height: 0;
  padding: 8px 8px 16px;
  overflow-y: auto;

  &::-webkit-scrollbar {
    width: 4px;
  }

  &::-webkit-scrollbar-thumb {
    background: #e2e8f0;
    border-radius: 4px;
  }
}

/* 顶层主域名称 (Top-Side Mix 模式侧栏模块列表标题) */
.console-shell__cluster-title {
  padding: 4px 8px 8px;
  margin-bottom: 4px;
  border-bottom: 1px solid var(--sb-border-subtle, #f1f5f9);
  font-size: 11px;
  font-weight: 700;
  color: #64748b;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

/* Cluster Group & Parent Menu in Sidebar Tree Mode */
.console-shell__cluster-section {
  display: flex;
  flex-direction: column;
  gap: 1px;
  margin-bottom: 2px;
}

.console-shell__cluster-parent-wrap {
  margin-bottom: 1px;
}

.console-shell__cluster-group {
  margin-bottom: 2px;
}

.console-shell__cluster-parent {
  position: relative;
  width: 100%;
  height: 34px;
  min-height: 34px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 8px;
  border-radius: 6px;
  border: none;
  background: transparent;
  color: #334155;
  font-family: inherit;
  font-size: 13px;
  font-weight: 600;
  text-align: left;
  cursor: pointer;
  transition: all 120ms ease;

  &:hover {
    background: #f1f5f9;
    color: #090d16;

    .cluster-parent-icon {
      color: #090d16;
    }

    .cluster-parent-arrow {
      color: #334155;
    }
  }

  &.is-active {
    color: #2563eb;

    .cluster-parent-icon {
      color: #2563eb;
    }

    .cluster-parent-title {
      color: #2563eb;
      font-weight: 650;
    }
  }

  &--single {
    font-weight: 500;

    &.is-active {
      background: #eff6ff;
      color: #2563eb;
      font-weight: 600;

      .cluster-parent-icon {
        color: #2563eb;
      }
    }
  }
}

.cluster-parent-icon {
  width: 18px;
  height: 18px;
  flex: 0 0 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  box-shadow: none;
  color: #64748b;
  font-size: 16px;
  transition: color 120ms ease;

  svg {
    width: 16px;
    height: 16px;
  }
}

.cluster-parent-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: -0.01em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cluster-parent-arrow {
  width: 14px;
  height: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
  font-size: 11px;
  margin-left: auto;
  transition: transform 160ms cubic-bezier(0.4, 0, 0.2, 1), color 120ms ease;

  &.is-open {
    transform: rotate(90deg);
    color: #475569;
  }

  svg {
    width: 11px;
    height: 11px;
  }
}

/* Cluster Children (二级应用容器) */
.console-shell__cluster-children {
  display: grid;
  gap: 1px;
  margin: 1px 0 3px 15px;
  padding-left: 8px;
  border-left: 1px solid var(--sb-border-subtle, #e2e8f0);
}

.cluster-slide-enter-active,
.cluster-slide-leave-active {
  transition: all 160ms cubic-bezier(0.4, 0, 0.2, 1);
}

.cluster-slide-enter-from,
.cluster-slide-leave-to {
  opacity: 0;
  transform: translateY(-3px);
}

.console-shell__rail-group {
  display: grid;
  gap: 1px;
}

.console-shell__app-group {
  display: grid;
  gap: 1px;
}

.console-shell__rail-app,
.console-shell__submenu-item,
.console-shell__directory-trigger,
.console-shell__user-trigger,
.console-shell__icon-btn {
  border: none;
  font-family: inherit;
  cursor: pointer;
  transition: all 120ms cubic-bezier(0.4, 0, 0.2, 1);
}

.console-shell__rail-app {
  position: relative;
  width: 100%;
  height: 32px;
  min-height: 32px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 8px;
  border-radius: 6px;
  background: transparent;
  color: #475569;
  font-size: 13px;
  font-weight: 500;
  text-align: left;

  &:hover {
    background: #f1f5f9;
    color: #090d16;

    .console-shell__rail-icon {
      color: #090d16;
    }

    .console-shell__rail-arrow {
      color: #334155;
    }
  }

  &.is-active {
    background: #eff6ff;
    color: #2563eb;
    font-weight: 600;

    .console-shell__rail-icon {
      color: #2563eb;
      background: transparent;
      border: none;
      box-shadow: none;
    }

    .console-shell__rail-title {
      font-weight: 600;
      color: #2563eb;
    }

    .console-shell__rail-arrow {
      color: #2563eb;
    }
  }
}

.console-shell__rail-icon {
  width: 18px;
  height: 18px;
  flex: 0 0 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  box-shadow: none;
  color: #64748b;
  font-size: 15px;
  transition: color 120ms ease;

  svg {
    width: 15px;
    height: 15px;
  }
}

.console-shell__rail-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  font-size: 13px;
  font-weight: inherit;
  letter-spacing: -0.01em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.console-shell__rail-arrow {
  width: 14px;
  height: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
  font-size: 11px;
  margin-left: auto;
  transition: transform 160ms cubic-bezier(0.4, 0, 0.2, 1), color 120ms ease;

  &.is-open {
    transform: rotate(90deg);
    color: #475569;
  }

  svg {
    width: 11px;
    height: 11px;
  }
}

.console-shell__submenu {
  display: grid;
  gap: 1px;
  margin: 1px 0 3px 15px;
  padding-left: 8px;
  border-left: 1px solid var(--sb-border-subtle, #e2e8f0);
}

.submenu-slide-enter-active,
.submenu-slide-leave-active {
  transition: all 160ms cubic-bezier(0.4, 0, 0.2, 1);
}

.submenu-slide-enter-from,
.submenu-slide-leave-to {
  opacity: 0;
  transform: translateY(-3px);
}

.console-shell__submenu-item {
  width: 100%;
  height: 28px;
  min-height: 28px;
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 0 8px;
  border-radius: 5px;
  background: transparent;
  color: #475569;
  font-size: 12.5px;
  font-weight: 400;
  text-align: left;
  border: none;
  cursor: pointer;
  transition: all 120ms ease;

  &:hover {
    background: #f8fafc;
    color: #090d16;

    .console-shell__submenu-dot {
      background: #475569;
    }
  }

  &.is-active {
    background: #eff6ff;
    color: #2563eb;
    font-weight: 600;

    .console-shell__submenu-dot {
      background: #2563eb;
      box-shadow: 0 0 0 2px rgb(37 99 235 / 0.2);
    }

    .console-shell__submenu-icon {
      color: #2563eb;
    }
  }
}

.console-shell__submenu-dot {
  width: 4px;
  height: 4px;
  flex: 0 0 4px;
  border-radius: 50%;
  background: #94a3b8;
  transition: all 120ms ease;
}

.console-shell__submenu-icon {
  font-size: 13px;
  width: 13px;
  height: 13px;
  flex: 0 0 13px;
  color: #64748b;
  transition: color 120ms ease;
}

.console-shell__submenu-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.console-shell__nav-state {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 12px;
  border-top: 1px solid var(--sb-border-color);
  color: var(--sb-warning-color);
  font-size: 12px;
}

.console-shell__rail-footer {
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px solid var(--sb-border-color);
  background: #fff;
  padding: 0 6px;
  gap: 4px;
}

.console-shell__directory-trigger {
  min-width: 0;
  height: 32px;
  flex: 1;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 8px;
  border-radius: 6px;
  background: transparent;
  color: #475569;
  font-size: 12.5px;
  font-weight: 500;
  text-align: left;
  border: none;
  cursor: pointer;

  &:hover {
    background: #f1f5f9;
    color: #090d16;
  }
}

.console-shell__footer-layout-btn {
  height: 30px;
  padding: 0 7px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border-radius: 6px;
  background: transparent;
  color: #475569;
  font-size: 11.5px;
  font-weight: 500;
  border: 1px solid var(--sb-border-subtle, #e2e8f0);
  cursor: pointer;
  white-space: nowrap;
  transition: all 120ms ease;

  &:hover {
    background: #eff6ff;
    border-color: #bfdbfe;
    color: #2563eb;
  }

  .footer-layout-icon {
    font-size: 12px;
  }

  .footer-layout-text {
    font-size: 11px;
  }
}

.console-shell__collapse-trigger {
  width: 32px;
  height: 32px;
  display: grid;
  flex: 0 0 32px;
  place-items: center;
  border-radius: 6px;
  border: none;
  background: transparent;
  color: #475569;
  cursor: pointer;
  transition: all 120ms ease;

  &:hover {
    background: #f1f5f9;
    color: #090d16;
  }

  svg {
    width: 15px;
    height: 15px;
  }
}

.console-shell__directory-icon {
  width: 15px;
}

.console-shell__main {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.console-shell__topbar {
  height: 52px;
  flex: 0 0 52px;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 20px;
  border-bottom: 1px solid var(--sb-border-color);
  background: rgb(255 255 255 / 0.96);
  backdrop-filter: blur(8px);
}

.console-shell__watermark-wrap {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.console-shell__title-block {
  min-width: 140px;

  h1 {
    margin: 2px 0 0;
    overflow: hidden;
    color: var(--sb-text-primary);
    font-size: 17px;
    font-weight: 650;
    letter-spacing: -0.015em;
    line-height: 1.25;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &--minimal {
    h1 {
      margin: 0;
    }
  }
}

.console-shell__breadcrumb-bar {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  white-space: nowrap;

  .console-shell__breadcrumb-app {
    color: #475569;
    font-weight: 500;
  }

  .console-shell__breadcrumb-sep {
    color: #94a3b8;
    font-size: 13px;
  }

  .console-shell__breadcrumb-page {
    color: #090d16;
    font-weight: 700;
    font-size: 14.5px;
  }
}

.console-shell__breadcrumbs {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  font-weight: 500;
}

.console-shell__breadcrumb-app {
  color: var(--sb-text-tertiary);
}

.console-shell__breadcrumb-sep {
  color: var(--sb-border-strong);
  font-size: 11px;
}

.console-shell__breadcrumb-page {
  color: var(--sb-primary-color);
  font-weight: 600;
}

.console-shell__top-domains {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0 16px;
  overflow-x: auto;
  min-width: 0;

  &::-webkit-scrollbar {
    display: none;
  }
}

.console-shell__domain-tab {
  position: relative;
  display: flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 11px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: #334155;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.14s ease;

  &:hover {
    background: #f1f5f9;
    color: #090d16;
  }

  &.is-active {
    background: #eff6ff;
    color: #1d4ed8;
    font-weight: 600;

    .console-shell__domain-icon {
      color: #1d4ed8;
    }

    .console-shell__domain-badge {
      background: #2563eb;
      color: #fff;
    }
  }
}

.console-shell__domain-icon {
  font-size: 15px;
  color: #94a3b8;
  transition: color 0.18s;
}

.console-shell__domain-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9999px;
  background: #f1f5f9;
  color: #64748b;
  font-size: 10.5px;
  font-weight: 600;
  line-height: 1;
  transition: all 0.18s;
}

.console-shell__domain-divider {
  width: 1px;
  height: 18px;
  background: var(--sb-border-color, #e2e8f0);
  margin: 0 4px;
  flex-shrink: 0;
}

.console-shell__header-pinned-wrap {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-left: 18px;
}

.console-shell__header-vdivider {
  width: 1px;
  height: 22px;
  background: var(--sb-border-color, #e2e8f0);
  flex-shrink: 0;
}

.console-shell__pinned-pill-group {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  background: #f1f5f9;
  padding: 3px 4px;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.console-shell__pinned-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  padding: 0 10px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: #64748b;
  font-size: 12.5px;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s ease;

  &:hover {
    color: #0f172a;
    background: rgb(255 255 255 / 0.7);
  }

  &.is-active {
    background: #fff;
    color: var(--el-color-primary, #0284c7);
    font-weight: 600;
    box-shadow: 0 1px 2px rgb(0 0 0 / 0.06);
  }

  .pinned-pill-icon {
    font-size: 13px;
  }

  .pinned-pill-arrow {
    font-size: 11px;
    margin-left: 2px;
  }
}

.console-shell__top-menu-bar {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: 1;
  min-width: 0;
  overflow-x: auto;
  padding: 0 8px;

  &--side-pinned {
    margin-left: 18px;
    gap: 6px;
  }

  &::-webkit-scrollbar {
    display: none;
  }
}

.console-shell__top-nav-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 7px;
  height: 38px;
  padding: 0 12px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: #475569;
  font-size: 13.5px;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.16s ease;

  &:hover {
    background: #f1f5f9;
    color: #0f172a;

    .console-shell__top-nav-icon {
      color: #0f172a;
    }
  }

  &.is-active {
    background: #eff6ff;
    border-color: #bfdbfe;
    color: #2563eb;
    font-weight: 600;

    .console-shell__top-nav-icon {
      color: #2563eb;
    }

    &::after {
      content: "";
      position: absolute;
      bottom: -11px;
      left: 15%;
      right: 15%;
      height: 2.5px;
      border-radius: 2px;
      background: #2563eb;
    }
  }
}

.console-shell__top-nav-icon {
  display: grid;
  place-items: center;
  font-size: 15px;
  color: #64748b;
  transition: color 0.16s ease;

  svg {
    width: 15px;
    height: 15px;
  }
}

.console-shell__top-nav-title {
  font-size: 13.5px;
  line-height: 1;
}

.console-shell__top-nav-arrow {
  margin-left: 2px;
  font-size: 11px;
  color: #94a3b8;
  transition: transform 0.16s ease;
}

.console-shell__topbar-actions {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 12px;
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
  border-radius: 8px;
  background: var(--sb-surface-contrast);
  box-shadow: 0 0 0 1px var(--sb-border-color) inset;
  transition: all 140ms ease;

  &:hover {
    box-shadow: 0 0 0 1px var(--sb-border-strong) inset;
  }

  &.is-focus {
    background: #fff;
    box-shadow: 0 0 0 2px var(--sb-primary-color) inset !important;
  }
}

.console-shell__quick-kbd {
  display: inline-block;
  padding: 2px 5px;
  border: 1px solid var(--sb-border-strong);
  border-radius: 4px;
  background: #fff;
  color: var(--sb-text-secondary);
  font-family: var(--sb-font-mono);
  font-size: 10.5px;
  font-weight: 600;
  line-height: 1.2;
}

.console-shell__quick-option {
  display: block;
  overflow: hidden;
  font-size: 13.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.console-shell__icon-btn {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border: 1px solid var(--sb-border-color);
  border-radius: 8px;
  background: #fff;
  color: var(--sb-text-secondary);
  transition: all 140ms ease;

  &:hover {
    background: var(--sb-surface-contrast);
    border-color: var(--sb-border-strong);
    color: var(--sb-text-primary);
  }
}

.console-shell__icon-svg {
  width: 16px;
  height: 16px;
}

.console-shell__notif-badge :deep(.el-badge__content) {
  top: 4px;
  right: 6px;
  height: 16px;
  padding: 0 4px;
  font-size: 10px;
  line-height: 16px;
}

.console-shell__notif-panel {
  display: grid;
  gap: 10px;
}

.console-shell__notif-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--sb-border-color);

  strong {
    font-size: 13px;
    font-weight: 600;
  }
}

.console-shell__notif-body {
  display: grid;
  gap: 8px;
}

.console-shell__notif-card {
  display: grid;
  grid-template-columns: 8px minmax(0, 1fr);
  align-items: flex-start;
  gap: 10px;
  padding: 8px;
  border-radius: var(--sb-radius-sm);
  background: var(--sb-surface-soft);

  p {
    margin: 0;
    color: var(--sb-text-primary);
    font-size: 12px;
    line-height: 1.4;
  }

  time {
    display: block;
    margin-top: 4px;
    color: var(--sb-text-tertiary);
    font-size: 11px;
  }
}

.console-shell__notif-indicator {
  width: 8px;
  height: 8px;
  margin-top: 4px;
  border-radius: 50%;

  &.is-info {
    background: var(--sb-primary-color);
  }

  &.is-success {
    background: var(--sb-success-color);
  }

  &.is-warning {
    background: var(--sb-warning-color);
  }
}

.console-shell__locale {
  width: 84px;
}

.console-shell__ai-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 12px;
  border-radius: 8px;
  border: 1px solid rgb(99 102 241 / 0.3);
  background: linear-gradient(135deg, rgb(99 102 241 / 0.08) 0%, rgb(6 182 212 / 0.08) 100%);
  color: #4f46e5;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 160ms cubic-bezier(0.4, 0, 0.2, 1);

  .ai-sparkle-icon {
    font-size: 15px;
    color: #6366f1;
    transition: transform 0.25s ease;
  }

  &:hover {
    background: linear-gradient(135deg, #4f46e5 0%, #06b6d4 100%);
    color: #fff;
    border-color: transparent;
    box-shadow: 0 4px 12px rgb(79 70 229 / 0.25);

    .ai-sparkle-icon {
      color: #fff;
      transform: scale(1.15) rotate(15deg);
    }
  }
}

.console-shell__user-trigger {
  min-height: 36px;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 3px 10px 3px 5px;
  border: 1px solid var(--sb-border-color);
  border-radius: 8px;
  background: #fff;
  color: var(--sb-text-primary);
  text-align: left;
  transition: all 140ms ease;

  &:hover {
    background: #f8fafc;
    border-color: var(--sb-border-strong);
  }
}

.console-shell__user-avatar {
  width: 28px;
  height: 28px;
  display: grid;
  flex: 0 0 28px;
  place-items: center;
  border-radius: 50%;
  background: linear-gradient(135deg, #3b82f6 0%, #6366f1 100%);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  box-shadow: 0 1px 3px rgb(37 99 235 / 0.3);
}

.console-shell__user-details {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.console-shell__user-name {
  max-width: 100px;
  overflow: hidden;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.console-shell__user-tag {
  color: var(--sb-primary-color);
  font-size: 10.5px;
  font-weight: 600;
  line-height: 1.2;
}

.console-shell__workspace {
  flex: 1;
  min-width: 0;
  min-height: 0;

  --sb-workspace-x: 24px;

  padding: 20px var(--sb-workspace-x) 32px;
  overflow: auto;
}

.console-shell__page-header-bar {
  margin-bottom: 20px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--sb-border-color);

  .console-shell__page-header-title {
    margin: 6px 0 0;
    font-size: 19px;
    font-weight: 700;
    color: var(--sb-text-primary);
    letter-spacing: -0.015em;
  }
}

.layout-drawer {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.layout-drawer__section-header {
  margin-bottom: 12px;

  h4 {
    margin: 0;
    font-size: 14.5px;
    font-weight: 700;
    color: #0f172a;
  }

  .layout-drawer__section-hint {
    display: block;
    margin-top: 4px;
    font-size: 12px;
    color: #64748b;
  }
}

.layout-mode-cards {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.layout-mode-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 14px;
  border-radius: 10px;
  border: 2px solid #e2e8f0;
  background: #fff;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);

  &:hover {
    border-color: #cbd5e1;
    background: #f8fafc;
    transform: translateY(-1px);
    box-shadow: 0 4px 12px rgb(0 0 0 / 0.05);
  }

  &.is-active {
    border-color: #2563eb;
    background: #eff6ff;
    box-shadow: 0 4px 14px rgb(37 99 235 / 0.12);

    .layout-mode-card__title-row strong {
      color: #1d4ed8;
    }
  }
}

.layout-mode-card__preview {
  width: 72px;
  height: 52px;
  flex: 0 0 72px;
  border-radius: 6px;
  background: #f1f5f9;
  border: 1px solid #cbd5e1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

/* Side Mode Mini Preview */
.preview--side {
  flex-direction: row;

  .preview__rail-full {
    width: 20px;
    background: #334155;
    padding: 3px 2px;
    display: flex;
    flex-direction: column;
    gap: 3px;

    .preview__brand-dot {
      width: 6px;
      height: 6px;
      border-radius: 2px;
      background: #38bdf8;
      margin-bottom: 2px;
    }

    .preview__line {
      height: 2px;
      border-radius: 1px;
      background: #94a3b8;

      &.is-short {
        width: 60%;
      }
    }
  }

  .preview__body-full {
    flex: 1;
    display: flex;
    flex-direction: column;

    .preview__bar-sub {
      height: 10px;
      background: #fff;
      border-bottom: 1px solid #e2e8f0;
    }

    .preview__body-content {
      flex: 1;
      padding: 4px;

      .preview__card {
        height: 100%;
        border-radius: 3px;
        background: #fff;
        border: 1px solid #e2e8f0;
      }
    }
  }
}

/* Top Mode Mini Preview */
.preview--top {
  .preview__bar-top {
    height: 12px;
    background: #1e293b;
    display: flex;
    align-items: center;
    gap: 3px;
    padding: 0 4px;

    .preview__brand-dot {
      width: 5px;
      height: 5px;
      border-radius: 2px;
      background: #38bdf8;
      margin-right: 2px;
    }

    .preview__nav-pill {
      width: 10px;
      height: 3px;
      border-radius: 1px;
      background: #94a3b8;
    }
  }

  .preview__body-full {
    flex: 1;
    padding: 4px;

    .preview__card {
      height: 100%;
      border-radius: 3px;
      background: #fff;
      border: 1px solid #e2e8f0;
    }
  }
}

/* Mix Mode Mini Preview */
.preview--mix {
  .preview__bar-top {
    height: 12px;
    background: #1e293b;
    display: flex;
    align-items: center;
    gap: 3px;
    padding: 0 4px;

    .preview__brand-dot {
      width: 5px;
      height: 5px;
      border-radius: 2px;
      background: #38bdf8;
    }

    .preview__nav-pill {
      width: 9px;
      height: 3px;
      border-radius: 1px;
      background: #38bdf8;
    }
  }

  .preview__split {
    flex: 1;
    display: flex;

    .preview__rail-sub {
      width: 16px;
      background: #f8fafc;
      border-right: 1px solid #e2e8f0;
      padding: 3px 2px;
      display: flex;
      flex-direction: column;
      gap: 3px;

      .preview__line {
        height: 2px;
        border-radius: 1px;
        background: #cbd5e1;

        &.is-short {
          width: 50%;
        }
      }
    }

    .preview__body-content {
      flex: 1;
      padding: 4px;

      .preview__card {
        height: 100%;
        border-radius: 3px;
        background: #fff;
        border: 1px solid #e2e8f0;
      }
    }
  }
}

.layout-mode-card__body {
  flex: 1;
  min-width: 0;

  .layout-mode-card__title-row {
    display: flex;
    align-items: center;
    gap: 8px;

    strong {
      font-size: 13.5px;
      font-weight: 600;
      color: #1e293b;
    }

    .card-badge {
      padding: 1px 6px;
      border-radius: 4px;
      font-size: 10.5px;
      font-weight: 600;
      background: #f1f5f9;
      color: #64748b;

      &.is-recommend {
        background: #eff6ff;
        color: #2563eb;
        border: 1px solid #bfdbfe;
      }
    }
  }

  p {
    margin: 4px 0 0;
    font-size: 12px;
    color: #64748b;
    line-height: 1.4;
  }
}

.layout-mode-card__check {
  font-size: 16px;
  color: #2563eb;
  font-weight: 700;
}

.layout-drawer__toggles {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.layout-toggle-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;

  .toggle-meta {
    display: flex;
    flex-direction: column;
    gap: 2px;

    .toggle-title {
      font-size: 13px;
      font-weight: 600;
      color: #1e293b;
    }

    .toggle-desc {
      font-size: 12px;
      color: #64748b;
    }
  }
}

.layout-drawer__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

:global(.console-shell__top-flyout) {
  min-width: 200px;
  padding: 6px;
  border-radius: 10px;
  box-shadow: 0 10px 25px -5px rgb(0 0 0 / 0.1), 0 8px 10px -6px rgb(0 0 0 / 0.08);
}

:global(.console-shell__top-flyout-header) {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px 8px;
  margin-bottom: 4px;
  border-bottom: 1px solid #f1f5f9;
  font-size: 12px;
  color: #64748b;

  strong {
    color: #0f172a;
    font-size: 13px;
  }
}

:global(.console-shell__top-flyout-icon) {
  width: 20px;
  height: 20px;
  display: grid;
  place-items: center;
  border-radius: 4px;
  background: #eff6ff;
  color: #2563eb;
  font-size: 12px;
}

:global(.console-shell__top-flyout-count) {
  margin-left: auto;
  font-size: 11px;
  color: #94a3b8;
}

:global(.console-shell__flyout-item) {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 2px 0;
}

:global(.console-shell__flyout-item .flyout-bullet) {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #cbd5e1;
}

:global(.el-dropdown-menu__item.is-sub-active) {
  background: #eff6ff !important;
  color: #2563eb !important;
  font-weight: 600;

  .flyout-bullet {
    background: #2563eb !important;
  }
}

:global(.console-shell__flyout-item .flyout-badge) {
  margin-left: auto;
  padding: 1px 6px;
  border-radius: 9999px;
  background: #eff6ff;
  color: #2563eb;
  border: 1px solid #bfdbfe;
  font-size: 10px;
  font-weight: 600;
}

:global(.console-shell__layout-quick-menu) {
  min-width: 240px;
  padding: 6px;

  .layout-quick-menu__title {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 6px 10px;
    font-size: 11.5px;
    font-weight: 700;
    color: #64748b;
    text-transform: uppercase;
    letter-spacing: 0.05em;
  }

  .quick-layout-item {
    display: flex;
    align-items: center;
    gap: 8px;
    width: 100%;

    .quick-layout-label {
      flex: 1;
      font-size: 13px;
    }

    .check-icon {
      margin-left: auto;
      color: #2563eb;
    }
  }

  .el-dropdown-menu__item.is-selected {
    color: #2563eb;
    background: #eff6ff;
    font-weight: 600;
  }
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

    .console-shell__brand-text {
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
    padding: 6px;
  }

  .console-shell__directory-trigger {
    justify-content: center;
    padding: 0;
  }

  .console-shell__quick-jump {
    flex-basis: 180px;
    width: min(180px, 22vw);
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
      font-size: 14px;
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

  .console-shell__user-details {
    display: none;
  }
}
</style>
