<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import {
  ArrowDown,
  ArrowRight,
  Bell,
  Check,
  Close,
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
  Switch as SwitchIcon,
  Sunny,
  Moon
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
import TianshuLogo from "@/components/brand/TianshuLogo.vue";
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
const mobileDrawerVisible = ref(false);
const railCollapsed = ref(false);

// Dual-Nav (Top-Side Mix) vs Classic Full Sidebar vs Top Menu Layout (默认采用顶级横向晶透浮动模式，与 iOS 27 视觉完全统一)
const layoutMode = ref<LayoutMode>(
  (localStorage.getItem("sb_layout_mode") as LayoutMode) || "top"
);
const showBreadcrumbs = ref<boolean>(
  localStorage.getItem("sb_show_breadcrumbs") !== "false"
);
const sidebarAccordion = ref<boolean>(
  localStorage.getItem("sb_sidebar_accordion") === "true"
);
const settingsDrawerVisible = ref(false);
const commandPaletteVisible = ref(false);
const enableWatermark = ref(localStorage.getItem("sb_enable_watermark") === "true");
const railTheme = ref<"dark" | "light">(
  (localStorage.getItem("sb_rail_theme") as "dark" | "light") || "light"
);

function toggleRailTheme() {
  railTheme.value = railTheme.value === "dark" ? "light" : "dark";
  localStorage.setItem("sb_rail_theme", railTheme.value);
  ElMessage.success(`已切换侧栏风格为「${railTheme.value === "dark" ? "深钛金灰" : "极简雅白"}」`);
}

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

function handleAtlasSelect(item: any) {
  directoryVisible.value = false;
  mobileDrawerVisible.value = false;
  const target = item.path ? item : (item.children?.find((c: any) => c.path) ?? item);
  if (target?.path) {
    router.push({ path: target.path, query: target.query });
  }
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
    <aside v-if="layoutMode !== 'top'" class="console-shell__rail" :class="`rail-theme--${railTheme}`">
      <div
        class="console-shell__brand"
        :class="{ 'is-clickable': railCollapsed }"
        :title="railCollapsed ? '点击展开左侧主导航' : ''"
        @click="railCollapsed ? (railCollapsed = false) : null"
      >
        <TianshuLogo
          :size="26"
          :show-text="!railCollapsed"
          :theme="railTheme"
        />
      </div>

      <nav class="console-shell__navigation" aria-label="主导航">
        <!-- 模式 A：顶层父级置顶模式 (Top-Side Mix) 侧栏展示当前主域下的二级应用 -->
        <template v-if="layoutMode === 'mix'">
          <div v-if="!railCollapsed" class="console-shell__cluster-title">
            {{ clusterMetaMap[activeCluster]?.label || '模块导航' }}
          </div>
          <section v-for="group in displayedGroups" :key="group.key" class="console-shell__rail-group">
            <div v-for="app in group.items" :key="app.id" class="console-shell__app-group">
              <!-- 折叠状态下：浮层菜单 -->
              <el-popover
                v-if="railCollapsed && app.children && app.children.length"
                placement="right-start"
                trigger="hover"
                :offset="12"
                :show-after="100"
                :popper-class="railTheme === 'dark' ? 'rail-collapsed-popover rail-collapsed-popover--dark' : 'rail-collapsed-popover'"
                :width="210"
              >
                <template #reference>
                  <button
                    class="console-shell__rail-app"
                    :class="{
                      'is-active': isSectionActive(app)
                    }"
                    @click="handleAppClick(app)"
                  >
                    <el-icon class="console-shell__rail-icon">
                      <component :is="resolveAppIcon(app.code, app.icon)" />
                    </el-icon>
                  </button>
                </template>
                <div class="rail-popover-menu">
                  <div class="rail-popover-header">
                    <span class="rail-popover-title">{{ app.title }}</span>
                  </div>
                  <div class="rail-popover-sub-list">
                    <div
                      v-for="item in app.children.filter((child) => child.path)"
                      :key="item.id"
                      class="rail-popover-sub-item"
                      :class="{ 'is-active': isExactActive(item) }"
                      @click="go(item)"
                    >
                      <span class="rail-popover-sub-dot" />
                      <span class="rail-popover-sub-text">{{ item.title }}</span>
                    </div>
                  </div>
                </div>
              </el-popover>
              <el-tooltip
                v-else-if="railCollapsed"
                :content="app.title"
                placement="right"
                :offset="14"
              >
                <button
                  class="console-shell__rail-app"
                  :class="{ 'is-active': isSectionActive(app) }"
                  @click="handleAppClick(app)"
                >
                  <el-icon class="console-shell__rail-icon">
                    <component :is="resolveAppIcon(app.code, app.icon)" />
                  </el-icon>
                </button>
              </el-tooltip>

              <!-- 展开状态下：原装完整折叠项 -->
              <template v-else>
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
              </template>
            </div>
          </section>
        </template>

        <!-- 模式 B：顶层父级在左侧模式 (Sidebar Tree) 顶级主域作为一级可折叠父菜单 -->
        <template v-else-if="layoutMode === 'side'">
          <section v-for="group in displayedGroups" :key="group.key" class="console-shell__cluster-section">
            <!-- 单一无子模块独立大类 (如平台总览) -->
            <div v-if="group.items.length === 1 && (!group.items[0].children || group.items[0].children.length === 0)" class="console-shell__cluster-parent-wrap">
              <el-tooltip
                :disabled="!railCollapsed"
                :content="group.label"
                placement="right"
                :offset="14"
              >
                <button
                  class="console-shell__cluster-parent console-shell__cluster-parent--single"
                  :class="{ 'is-active': isSectionActive(group.items[0]) }"
                  :title="railCollapsed ? '' : group.label"
                  @click="handleAppClick(group.items[0])"
                >
                  <el-icon class="cluster-parent-icon">
                    <component :is="clusterMetaMap[group.key]?.icon || 'DataBoard'" />
                  </el-icon>
                  <span class="cluster-parent-title">{{ group.label }}</span>
                </button>
              </el-tooltip>
            </div>

            <!-- 包含多个二级模块的顶层父级 (如基础治理、运行支撑、工程交付、审计追踪) -->
            <div v-else class="console-shell__cluster-group">
              <!-- 折叠状态下：悬浮气泡二级/三级菜单 (完美解决收起后二级菜单不明显/出不来的问题) -->
              <el-popover
                v-if="railCollapsed"
                placement="right-start"
                trigger="hover"
                :offset="12"
                :show-after="100"
                :popper-class="railTheme === 'dark' ? 'rail-collapsed-popover rail-collapsed-popover--dark' : 'rail-collapsed-popover'"
                :width="230"
              >
                <template #reference>
                  <button
                    class="console-shell__cluster-parent"
                    :class="{
                      'is-active': isClusterActive(group)
                    }"
                    @click="switchCluster(group.key)"
                  >
                    <el-icon class="cluster-parent-icon">
                      <component :is="clusterMetaMap[group.key]?.icon || 'Grid'" />
                    </el-icon>
                    <span class="cluster-parent-title">{{ group.label }}</span>
                  </button>
                </template>

                <!-- 浮层内容：该模块大类下的完整菜单树 -->
                <div class="rail-popover-menu">
                  <div class="rail-popover-header">
                    <el-icon class="rail-popover-icon">
                      <component :is="clusterMetaMap[group.key]?.icon || 'Grid'" />
                    </el-icon>
                    <span class="rail-popover-title">{{ group.label }}</span>
                  </div>
                  <div class="rail-popover-body">
                    <div v-for="app in group.items" :key="app.id" class="rail-popover-item-wrap">
                      <div
                        class="rail-popover-app"
                        :class="{ 'is-active': isSectionActive(app) }"
                        @click="handleAppClick(app)"
                      >
                        <el-icon class="rail-popover-app-icon">
                          <component :is="resolveAppIcon(app.code, app.icon)" />
                        </el-icon>
                        <span class="rail-popover-app-title">{{ app.title }}</span>
                      </div>
                      <div v-if="app.children && app.children.length" class="rail-popover-sub-list">
                        <div
                          v-for="item in app.children.filter((child) => child.path)"
                          :key="item.id"
                          class="rail-popover-sub-item"
                          :class="{ 'is-active': isExactActive(item) }"
                          @click="go(item)"
                        >
                          <span class="rail-popover-sub-dot" />
                          <span class="rail-popover-sub-text">{{ item.title }}</span>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </el-popover>

              <!-- 正常展开状态下：内嵌完整树展开 -->
              <template v-else>
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
              </template>
            </div>
          </section>
        </template>
      </nav>

      <div v-if="menuStore.loading || menuStore.lastError" class="console-shell__nav-state">
        <span>{{ menuStore.loading ? "导航加载中" : "导航加载失败" }}</span>
        <el-button link :loading="menuStore.loading" @click="handleRefreshNavigation">重试</el-button>
      </div>

      <footer class="console-shell__rail-footer" :class="{ 'is-collapsed': railCollapsed }">
        <template v-if="railCollapsed">
          <button
            class="console-shell__collapsed-expand-bar"
            title="点击展开左侧主导航"
            aria-label="展开导航"
            @click="railCollapsed = false"
          >
            <el-icon class="expand-icon"><Expand /></el-icon>
            <span class="expand-label">展开</span>
          </button>
        </template>
        <template v-else>
          <button class="console-shell__directory-trigger" title="功能索引 (快捷导航地图)" @click="directoryVisible = true">
            <Grid class="console-shell__directory-icon" />
            <span>{{ localeStore.t("shell.directory") }}</span>
          </button>

          <div class="console-shell__footer-actions">
            <!-- 顶层父级位置便捷切换按钮 (在顶部 vs 在左侧) -->
            <el-tooltip :content="layoutMode === 'side' ? '点击切换为「顶层主域置顶模式」' : '点击切换为「左侧多级折叠树模式」'" placement="top">
              <button
                class="console-shell__footer-icon-btn"
                :aria-label="layoutMode === 'side' ? '切换为顶层主域置顶模式' : '切换为左侧折叠树模式'"
                @click="setCategoryPosition(layoutMode === 'side' ? 'top' : 'side')"
              >
                <el-icon><SwitchIcon /></el-icon>
              </button>
            </el-tooltip>

            <!-- 侧栏风格切换 (深钛金灰 vs 极简雅白) -->
            <el-tooltip :content="railTheme === 'dark' ? '点击切换为「极简雅白」侧栏' : '点击切换为「深钛金灰」侧栏'" placement="top">
              <button
                class="console-shell__footer-icon-btn"
                :aria-label="railTheme === 'dark' ? '切换为极简雅白侧栏' : '切换为深钛金灰侧栏'"
                @click="toggleRailTheme"
              >
                <el-icon><component :is="railTheme === 'dark' ? Sunny : Moon" /></el-icon>
              </button>
            </el-tooltip>

            <button
              class="console-shell__collapse-trigger"
              title="收起侧栏主导航"
              aria-label="收起导航"
              @click="railCollapsed = true"
            >
              <Fold />
            </button>
          </div>
        </template>
      </footer>
    </aside>

    <div class="console-shell__main">
      <header class="console-shell__topbar">
        <!-- 移动端汉堡导航菜单按钮 (<760px 显示) -->
        <button
          class="console-shell__mobile-menu-btn"
          aria-label="打开移动端导航抽屉"
          title="打开导航"
          @click="mobileDrawerVisible = true"
        >
          <el-icon class="mobile-menu-icon"><Fold /></el-icon>
        </button>

        <!-- 侧栏折叠/展开常驻快捷按钮 (醒目快捷键：直接点击切换展开/收起) -->
        <button
          v-if="layoutMode !== 'top'"
          class="console-shell__rail-toggle-pill"
          :class="{ 'is-collapsed': railCollapsed }"
          :title="railCollapsed ? '展开左侧菜单' : '收起左侧菜单'"
          :aria-label="railCollapsed ? '展开左侧菜单' : '收起左侧菜单'"
          @click="railCollapsed = !railCollapsed"
        >
          <el-icon class="toggle-icon"><component :is="railCollapsed ? Expand : Fold" /></el-icon>
          <span class="toggle-text">{{ railCollapsed ? '展开菜单' : '收起' }}</span>
        </button>

        <!-- 顶部菜单模式下的品牌展示 -->
        <div v-if="layoutMode === 'top'" class="console-shell__brand console-shell__brand--top">
          <TianshuLogo
            :size="26"
            show-text
            :theme="railTheme"
          />
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
                <kbd class="console-shell__quick-kbd">搜索</kbd>
              </template>
              <template #default="{ item }">
                <span class="console-shell__quick-option">{{ item.value }}</span>
              </template>
            </el-autocomplete>
          </div>

          <!-- 系统运行状态指示器 -->
          <div class="console-shell__sla-pill" title="系统各服务模块运行正常，数据传输稳定">
            <span class="sla-dot-pulse" />
            <span class="sla-text">运行正常</span>
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
                  {{
                    authStore.isSuperAdmin
                      ? '超级管理员'
                      : authStore.currentUser?.userType === 'TENANT_ADMIN'
                      ? '租户管理员'
                      : authStore.currentUser?.userType === 'TENANT_USER'
                      ? '企业普通员工'
                      : '普通用户'
                  }}
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

      <!-- 多标签页 TagsView 导航条 (在概览控制台首页沉浸模式下隐藏，确保毛玻璃纯粹无干扰) -->
      <TagsView v-if="route.path !== '/dashboard'" />

      <el-watermark class="console-shell__watermark-wrap" :content="watermarkContent" :font="{ color: 'rgba(0, 0, 0, 0.015)', fontSize: 13 }">
        <main class="console-shell__workspace">
          <!-- 顶部模式下的页头面包屑导航条 (在概览控制台首页隐藏，避免双重标题) -->
          <div v-if="layoutMode === 'top' && showBreadcrumbs && route.path !== '/dashboard'" class="console-shell__page-header-bar">
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

    <!-- 移动端侧滑导航抽屉 -->
    <el-drawer
      v-model="mobileDrawerVisible"
      direction="ltr"
      size="300px"
      :with-header="false"
      class="console-shell__mobile-drawer"
    >
      <div class="mobile-drawer__content">
        <div class="mobile-drawer__header">
          <TianshuLogo :size="24" show-text />
          <button
            class="mobile-drawer__close-btn"
            aria-label="关闭导航"
            @click="mobileDrawerVisible = false"
          >
            <el-icon><Close /></el-icon>
          </button>
        </div>

        <div class="mobile-drawer__user-card">
          <span class="mobile-drawer__user-avatar">{{ userInitial }}</span>
          <div class="mobile-drawer__user-info">
            <span class="mobile-drawer__user-name">
              {{ authStore.currentUser?.nickname || authStore.currentUser?.username || "用户" }}
            </span>
            <span class="mobile-drawer__tenant-badge">
              {{ tenantStore.currentTenant?.name || "当前空间" }}
            </span>
          </div>
        </div>

        <div class="mobile-drawer__body">
          <NavigationAtlas
            :apps="apps"
            :active-code="currentApp?.code"
            compact
            @select="handleAtlasSelect"
          />
        </div>

        <div class="mobile-drawer__footer">
          <button class="mobile-drawer__footer-btn" @click="mobileDrawerVisible = false; router.push('/profile')">
            <el-icon><User /></el-icon>
            <span>个人中心</span>
          </button>
          <button class="mobile-drawer__footer-btn" @click="mobileDrawerVisible = false; settingsDrawerVisible = true">
            <el-icon><Setting /></el-icon>
            <span>偏好设置</span>
          </button>
          <button class="mobile-drawer__footer-btn is-logout" @click="mobileDrawerVisible = false; handleLogout()">
            <el-icon><SwitchButton /></el-icon>
            <span>退出</span>
          </button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>


<style scoped lang="scss">
.console-shell {
  height: 100vh;
  display: grid;
  grid-template-columns: 242px minmax(0, 1fr);
  overflow: hidden;
  background: var(--sb-shell-main);
  transition: grid-template-columns 160ms cubic-bezier(0.4, 0, 0.2, 1);
  padding: 12px 14px 12px 14px;
  gap: 14px;
  box-sizing: border-box;

  &.layout--top {
    grid-template-columns: minmax(0, 1fr);
    padding: 12px 16px;
  }
}

.console-shell--collapsed {
  grid-template-columns: 64px minmax(0, 1fr);

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
  .console-shell__footer-icon-btn,
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
  height: 100%;
  border-radius: 20px;
  overflow: hidden;
  backdrop-filter: var(--sb-glass-blur);
  -webkit-backdrop-filter: var(--sb-glass-blur);

  /* 主题 A：深钛金黑曜石毛玻璃 (Classic Dark Slate Obsidian Glass) */
  --rail-bg: rgba(15, 23, 42, 0.75);
  --rail-border: rgba(255, 255, 255, 0.10);
  --rail-brand-bg: rgba(11, 17, 32, 0.45);
  --rail-brand-border: rgba(255, 255, 255, 0.08);
  --rail-text-cluster: #94a3b8;
  --rail-text-cluster-hover: #ffffff;
  --rail-text-cluster-active: #38bdf8;
  --rail-text-app: #cbd5e1;
  --rail-text-app-hover: #ffffff;
  --rail-text-app-active: #ffffff;
  --rail-app-bg-hover: rgba(255, 255, 255, 0.08);
  --rail-app-bg-active: rgba(37, 99, 235, 0.32);
  --rail-icon-color: #64748b;
  --rail-icon-active: #38bdf8;
  --rail-indicator: #38bdf8;
  --rail-indicator-glow: 0 0 12px rgba(56, 189, 248, 0.7);
  --rail-arrow-color: #64748b;
  --rail-tree-line: rgba(255, 255, 255, 0.1);
  --rail-footer-bg: rgba(11, 17, 32, 0.45);
  --rail-footer-border: rgba(255, 255, 255, 0.08);
  --rail-btn-text: #94a3b8;
  --rail-btn-border: rgba(255, 255, 255, 0.1);

  background: var(--rail-bg);
  border: 1px solid var(--rail-border);
  color: var(--rail-text-app);
  box-shadow: inset 0 1px 1.5px 0 rgba(255, 255, 255, 0.25), 0 20px 40px -10px rgba(0, 0, 0, 0.35);

  /* 主题 B：iOS 27 晶透雅白 (Unified Liquid Glass - 超高透光，光学倒角高光，深度清晰文字) */
  &.rail-theme--light {
    --rail-bg: rgba(255, 255, 255, 0.78);
    --rail-border: rgba(255, 255, 255, 0.95);
    --rail-brand-bg: rgba(255, 255, 255, 0.55);
    --rail-brand-border: rgba(226, 232, 240, 0.65);
    --rail-text-cluster: #475569;       /* 顶层主域大字：权威结构 Slate-600 */
    --rail-text-cluster-hover: #0f172a;
    --rail-text-cluster-active: #0f172a;/* 激活顶级保持稳重大字，杜绝杂乱发虚 */
    --rail-text-app: #1e293b;           /* 二级功能文字：高对比度深色 Slate-800，绝对清晰锐利 */
    --rail-text-app-hover: #0f172a;
    --rail-text-app-active: #1d4ed8;    /* 激活项纯正钴蓝 */
    --rail-app-bg-hover: rgba(255, 255, 255, 0.88);       /* 晶白悬浮背景 */
    --rail-app-bg-active: rgba(37, 99, 235, 0.10);      /* 清爽微蓝激活底色 */
    --rail-icon-color: #2563eb;         /* 图标清晰易辨科技钴蓝 */
    --rail-icon-active: #2563eb;
    --rail-indicator: #2563eb;
    --rail-indicator-glow: 0 0 10px rgba(37, 99, 235, 0.4);
    --rail-arrow-color: #94a3b8;
    --rail-tree-line: rgba(203, 213, 225, 0.85);         /* 细腻雅灰引导发丝线 */
    --rail-footer-bg: rgba(255, 255, 255, 0.60);
    --rail-footer-border: rgba(226, 232, 240, 0.7);
    --rail-btn-text: #1e293b;
    --rail-btn-border: rgba(203, 213, 225, 0.8);

    backdrop-filter: blur(32px) saturate(210%) contrast(106%);
    -webkit-backdrop-filter: blur(32px) saturate(210%) contrast(106%);
    border: 1px solid rgba(255, 255, 255, 0.98);
    box-shadow:
      inset 0 1.5px 2px 0 rgba(255, 255, 255, 1),
      0 20px 40px -10px rgba(15, 23, 42, 0.08);
  }
}

.console-shell__brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 52px;
  padding: 0 14px;
  border-bottom: 1px solid var(--rail-brand-border);
  background: var(--rail-brand-bg);
  transition: background 150ms ease, border-color 150ms ease;

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
  padding: 8px 8px 6px;
  margin-bottom: 4px;
  border-bottom: 1px solid var(--rail-tree-line);
  font-size: 11px;
  font-weight: 700;
  color: var(--rail-text-cluster);
  letter-spacing: 0.05em;
  font-family: var(--sb-font-mono, monospace);
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
  color: var(--rail-text-cluster);
  font-family: inherit;
  font-size: 13px;
  font-weight: 600;
  text-align: left;
  cursor: pointer;
  transition: all 120ms ease;

  .cluster-parent-icon {
    color: var(--rail-icon-color);
  }

  .cluster-parent-title {
    color: inherit;
    font-size: 13px;
    font-weight: 650;
    letter-spacing: -0.01em;
  }

  .cluster-parent-arrow {
    color: var(--rail-arrow-color);
  }

  &:hover {
    background: var(--rail-app-bg-hover);
    color: var(--rail-text-cluster-hover);

    .cluster-parent-icon {
      color: var(--rail-icon-active);
    }

    .cluster-parent-arrow {
      color: var(--rail-text-cluster-hover);
    }
  }

  &.is-active {
    color: var(--rail-text-cluster-active);

    .cluster-parent-icon {
      color: var(--rail-icon-active);
    }

    .cluster-parent-title {
      color: var(--rail-text-cluster-active);
      font-weight: 700;
    }
  }

  &--single {
    font-weight: 500;

    &.is-active {
      background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%) !important;
      color: #ffffff !important;
      font-weight: 600 !important;
      box-shadow: 0 3px 10px rgba(37, 99, 235, 0.35);

      &::before {
        display: none;
      }

      .cluster-parent-icon {
        color: #ffffff !important;
      }

      .cluster-parent-title {
        color: #ffffff !important;
        font-weight: 600 !important;
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
  color: var(--rail-icon-color);
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
  color: var(--rail-arrow-color);
  font-size: 11px;
  margin-left: auto;
  transition: transform 160ms cubic-bezier(0.4, 0, 0.2, 1), color 120ms ease;

  &.is-open {
    transform: rotate(90deg);
    color: var(--rail-icon-active);
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
  border-left: 1px solid var(--rail-tree-line);
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
  height: 34px;
  min-height: 34px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 10px;
  border-radius: 7px;
  background: transparent;
  color: var(--rail-text-app);
  font-size: 13px;
  font-weight: 500;
  text-align: left;
  -webkit-font-smoothing: antialiased;

  .console-shell__rail-icon {
    color: var(--rail-icon-color);
  }

  .console-shell__rail-title {
    color: inherit;
    font-size: 13px;
    font-weight: inherit;
    letter-spacing: -0.01em;
  }

  .console-shell__rail-arrow {
    color: var(--rail-arrow-color);
  }

  &:hover {
    background: var(--rail-app-bg-hover);
    color: var(--rail-text-app-hover);

    .console-shell__rail-icon {
      color: var(--rail-icon-active);
    }

    .console-shell__rail-arrow {
      color: var(--rail-text-app-hover);
    }
  }

  /* 具有下级菜单项的父节点：展开或其子路由激活时保持素雅权威大字，不出现与子菜单冲突的混杂蓝底 */
  &.has-children {
    font-weight: 550;

    &.is-expanded,
    &.is-active {
      background: rgba(241, 245, 249, 0.70);
      color: #0f172a;
      font-weight: 650;

      .console-shell__rail-icon {
        color: #2563eb;
      }

      .console-shell__rail-title {
        color: #0f172a;
        font-weight: 650;
      }

      .console-shell__rail-arrow {
        color: #2563eb;
      }

      &::before {
        display: none;
      }
    }
  }

  /* 单一叶子节点激活态 (无子级的菜单，如平台总览) */
  &:not(.has-children).is-active {
    background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%) !important;
    color: #ffffff !important;
    font-weight: 600 !important;
    box-shadow: 0 3px 10px rgba(37, 99, 235, 0.35);

    .console-shell__rail-icon {
      color: #ffffff !important;
    }

    .console-shell__rail-title {
      font-weight: 600 !important;
      color: #ffffff !important;
    }

    &::before {
      display: none;
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
  color: var(--rail-icon-color);
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
  color: var(--rail-arrow-color);
  font-size: 11px;
  margin-left: auto;
  transition: transform 160ms cubic-bezier(0.4, 0, 0.2, 1), color 120ms ease;

  &.is-open {
    transform: rotate(90deg);
    color: var(--rail-icon-active);
  }

  svg {
    width: 11px;
    height: 11px;
  }
}

.console-shell__submenu {
  display: grid;
  gap: 2px;
  margin: 2px 0 4px 14px;
  padding-left: 8px;
  border-left: 1.5px solid var(--rail-tree-line);
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
  height: 32px;
  min-height: 32px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 10px;
  border-radius: 6px;
  background: transparent;
  color: var(--rail-text-app);
  font-size: 13px;
  font-weight: 500;
  text-align: left;
  border: none;
  cursor: pointer;
  -webkit-font-smoothing: antialiased;
  letter-spacing: -0.01em;
  transition: all 120ms cubic-bezier(0.4, 0, 0.2, 1);

  .console-shell__submenu-dot {
    width: 4.5px;
    height: 4.5px;
    flex: 0 0 4.5px;
    border-radius: 50%;
    background: #94a3b8;
    transition: all 120ms ease;
  }

  .console-shell__submenu-icon {
    font-size: 14px;
    width: 14px;
    height: 14px;
    flex: 0 0 14px;
    color: var(--rail-icon-color);
    transition: color 120ms ease;
  }

  .console-shell__submenu-text {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    color: inherit;
  }

  &:hover {
    background: var(--rail-app-bg-hover);
    color: var(--rail-text-app-hover);

    .console-shell__submenu-dot {
      background: var(--rail-icon-active);
      transform: scale(1.2);
    }

    .console-shell__submenu-icon {
      color: var(--rail-icon-active);
    }
  }

  /* Apple Keynote 顶级聚焦明澈胶囊 */
  &.is-active {
    background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%) !important;
    color: #ffffff !important;
    font-weight: 600 !important;
    box-shadow:
      0 3px 10px rgba(37, 99, 235, 0.35),
      inset 0 1px 0 rgba(255, 255, 255, 0.25) !important;

    .console-shell__submenu-dot {
      background: #ffffff !important;
      box-shadow: 0 0 6px rgba(255, 255, 255, 0.85);
      transform: scale(1.2);
    }

    .console-shell__submenu-icon {
      color: #ffffff !important;
    }

    .console-shell__submenu-text {
      color: #ffffff !important;
      font-weight: 600 !important;
    }
  }
}

.console-shell__nav-state {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 12px;
  border-top: 1px solid var(--rail-footer-border);
  color: var(--sb-warning-color);
  font-size: 12px;
}

.console-shell__rail-footer {
  height: 46px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px solid var(--rail-footer-border);
  background: var(--rail-footer-bg);
  padding: 0 8px;
  gap: 6px;
  box-sizing: border-box;

  &.is-collapsed {
    padding: 6px;
    justify-content: center;
  }
}

.console-shell__collapsed-expand-bar {
  width: 100%;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 0 4px;
  white-space: nowrap;
  border-radius: 8px;
  background: rgba(37, 99, 235, 0.12);
  border: 1px solid rgba(37, 99, 235, 0.35);
  color: #2563eb;
  font-size: 11.5px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 2px 6px rgba(37, 99, 235, 0.12);
  transition: all 160ms cubic-bezier(0.4, 0, 0.2, 1);

  .expand-icon {
    font-size: 13px;
    flex-shrink: 0;
    transition: transform 160ms ease;
  }

  .expand-label {
    letter-spacing: 0.02em;
    font-weight: 600;
    white-space: nowrap;
    flex-shrink: 0;
  }

  &:hover {
    background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
    border-color: #1d4ed8;
    color: #ffffff;
    box-shadow: 0 4px 14px rgba(37, 99, 235, 0.4);

    .expand-icon {
      transform: translateX(2px);
    }
  }
}

.rail-theme--dark {
  .console-shell__collapsed-expand-bar {
    background: rgba(56, 189, 248, 0.14);
    border-color: rgba(56, 189, 248, 0.4);
    color: #38bdf8;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);

    &:hover {
      background: linear-gradient(135deg, #0284c7 0%, #2563eb 100%);
      border-color: #38bdf8;
      color: #ffffff;
      box-shadow: 0 4px 16px rgba(56, 189, 248, 0.45);
    }
  }
}

.console-shell__directory-trigger {
  min-width: 0;
  height: 30px;
  flex: 1;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 8px;
  border-radius: 6px;
  background: transparent;
  color: var(--rail-btn-text);
  font-size: 12px;
  font-weight: 500;
  text-align: left;
  border: 1px solid var(--rail-btn-border);
  cursor: pointer;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: all 120ms ease;

  span {
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  &:hover {
    background: var(--rail-app-bg-hover);
    color: var(--rail-text-app-hover);
    border-color: var(--rail-icon-active);
  }
}

.console-shell__footer-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.console-shell__footer-icon-btn,
.console-shell__collapse-trigger {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  border: 1px solid var(--rail-btn-border);
  background: transparent;
  color: var(--rail-btn-text);
  font-size: 13px;
  cursor: pointer;
  flex-shrink: 0;
  transition: all 120ms ease;

  &:hover {
    background: var(--rail-app-bg-hover);
    border-color: var(--rail-icon-active);
    color: var(--rail-text-app-hover);
  }
}

.console-shell__directory-icon {
  width: 15px;
  height: 15px;
  flex-shrink: 0;
}

.console-shell__main {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  gap: 10px;
}

.console-shell__topbar {
  height: 54px;
  flex: 0 0 54px;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 20px;
  border-radius: 20px;
  border: 1px solid rgba(255, 255, 255, 0.85);
  background: rgba(255, 255, 255, 0.58);
  backdrop-filter: var(--sb-glass-blur);
  -webkit-backdrop-filter: var(--sb-glass-blur);
  box-shadow:
    inset 0 1.5px 2px 0 rgba(255, 255, 255, 0.98),
    0 10px 25px -5px rgba(15, 23, 42, 0.04);
}

.console-shell__rail-toggle-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 12px;
  border-radius: 9999px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(226, 232, 240, 0.95);
  color: #334155;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  flex-shrink: 0;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
  transition: all 160ms cubic-bezier(0.4, 0, 0.2, 1);

  .toggle-icon {
    font-size: 14px;
    color: #2563eb;
    transition: transform 160ms ease;
  }

  .toggle-text {
    letter-spacing: -0.01em;
  }

  &:hover {
    background: #ffffff;
    border-color: #93c5fd;
    color: #1d4ed8;
    box-shadow: 0 3px 10px rgba(37, 99, 235, 0.15);

    .toggle-icon {
      transform: scale(1.15);
    }
  }

  &.is-collapsed {
    background: linear-gradient(135deg, rgba(37, 99, 235, 0.10) 0%, rgba(59, 130, 246, 0.15) 100%);
    border-color: rgba(37, 99, 235, 0.4);
    color: #1d4ed8;
    box-shadow: 0 2px 8px rgba(37, 99, 235, 0.2);

    .toggle-icon {
      color: #2563eb;
    }
  }
}

.console-shell__watermark-wrap {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: transparent;
  border: none;
  box-shadow: none;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
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

.console-shell__sla-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  padding: 0 9px;
  border-radius: var(--sb-radius-xs, 4px);
  background: rgba(5, 150, 105, 0.08);
  border: 1px solid rgba(5, 150, 105, 0.25);
  color: #047857;
  font-size: 11px;
  font-weight: 600;
  font-family: var(--sb-font-mono, monospace);
  user-select: none;
  white-space: nowrap;

  .sla-dot-pulse {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: #10b981;
    box-shadow: 0 0 0 2px rgba(16, 185, 129, 0.25);
    animation: slaPulse 2.4s infinite ease-in-out;
  }
}

@keyframes slaPulse {
  0%, 100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.55;
    transform: scale(0.85);
  }
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
  padding: 3px 12px 3px 6px;
  border: 1px solid rgba(226, 232, 240, 0.85);
  border-radius: 9999px;
  background: rgba(255, 255, 255, 0.65);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  box-shadow: inset 0 1px 1px 0 rgba(255, 255, 255, 0.95), 0 2px 8px rgba(15, 23, 42, 0.03);
  color: var(--sb-text-primary);
  text-align: left;
  transition: all 160ms cubic-bezier(0.16, 1, 0.3, 1);

  &:hover {
    background: rgba(255, 255, 255, 0.95);
    border-color: rgba(203, 213, 225, 0.95);
    box-shadow: inset 0 1px 1.5px 0 rgba(255, 255, 255, 1), 0 4px 12px rgba(15, 23, 42, 0.06);
    transform: translateY(-0.5px);
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

.console-shell__mobile-menu-btn {
  display: none;
}

/* 移动端侧滑导航抽屉 */
.console-shell__mobile-drawer {
  :deep(.el-drawer__body) {
    padding: 0 !important;
    display: flex;
    flex-direction: column;
    height: 100%;
    background: #ffffff;
    overflow: hidden;
  }
}

.mobile-drawer__content {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.mobile-drawer__header {
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  border-bottom: 1px solid #f1f5f9;
  flex-shrink: 0;
}

.mobile-drawer__close-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  color: #64748b;
  border-radius: 6px;
  cursor: pointer;

  &:hover,
  &:active {
    background: #f1f5f9;
    color: #0f172a;
  }
}

.mobile-drawer__user-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: #f8fafc;
  border-bottom: 1px solid #f1f5f9;
  flex-shrink: 0;
}

.mobile-drawer__user-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: linear-gradient(135deg, #2563eb, #38bdf8);
  color: #ffffff;
  font-weight: 700;
  font-size: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 4px rgba(37, 99, 235, 0.2);
}

.mobile-drawer__user-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.mobile-drawer__user-name {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mobile-drawer__tenant-badge {
  font-size: 11px;
  color: #2563eb;
  font-weight: 500;
  background: #eff6ff;
  padding: 1px 6px;
  border-radius: 4px;
  display: inline-block;
  width: fit-content;
}

.mobile-drawer__body {
  flex: 1;
  overflow-y: auto;
  padding: 12px 16px;
  -webkit-overflow-scrolling: touch;
}

.mobile-drawer__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  border-top: 1px solid #f1f5f9;
  background: #fafafa;
  flex-shrink: 0;
}

.mobile-drawer__footer-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: none;
  background: transparent;
  padding: 6px 10px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  color: #475569;
  cursor: pointer;

  &:hover,
  &:active {
    background: #f1f5f9;
    color: #0f172a;
  }

  &.is-logout {
    color: #ef4444;

    &:hover,
    &:active {
      background: #fef2f2;
      color: #dc2626;
    }
  }
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
    gap: 8px;
    padding: 0 10px;
    height: 48px;
    flex: 0 0 48px;
  }

  .console-shell__mobile-menu-btn {
    display: inline-flex !important;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 32px;
    padding: 0;
    border-radius: 6px;
    border: 1px solid var(--sb-border-subtle, #e2e8f0);
    background: #ffffff;
    color: var(--sb-text-primary, #0f172a);
    cursor: pointer;
    flex-shrink: 0;

    &:active {
      background: #f1f5f9;
    }

    .mobile-menu-icon {
      font-size: 16px;
    }
  }

  .console-shell__breadcrumb-bar {
    font-size: 13px;
    max-width: 90px;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .console-shell__title-block {
    min-width: 0;
    flex: 1;

    h1 {
      font-size: 13.5px;
    }
  }

  .console-shell__top-domains,
  .console-shell__top-menu-bar {
    display: none !important;
  }

  .console-shell__topbar-actions {
    flex: 0 0 auto;
    gap: 6px;
  }

  .console-shell__quick-jump,
  .console-shell__locale,
  .console-shell__sla-pill,
  .console-shell__icon-btn,
  .console-shell__ai-btn {
    display: none !important;
  }

  .console-shell__context-actions {
    order: 1;
  }

  :deep(.tags-view-container) {
    display: none !important;
  }

  .console-shell__workspace {
    --sb-workspace-x: 10px;
    padding: 10px;
  }

  .console-shell__user-details {
    display: none;
  }

  .console-shell__user-avatar {
    width: 28px;
    height: 28px;
    font-size: 12px;
  }
}
</style>

<style lang="scss">
/* ============================================================
   全局悬浮菜单浮层 (左侧栏收起状态下的子菜单 Popover)
   ============================================================ */
.rail-collapsed-popover.el-popper {
  padding: 0 !important;
  border-radius: 14px !important;
  border: 1px solid rgba(255, 255, 255, 0.95) !important;
  background: rgba(255, 255, 255, 0.92) !important;
  backdrop-filter: blur(28px) saturate(190%) !important;
  -webkit-backdrop-filter: blur(28px) saturate(190%) !important;
  box-shadow:
    0 16px 36px -4px rgba(15, 23, 42, 0.16),
    0 0 0 1px rgba(255, 255, 255, 0.95) inset !important;
  overflow: hidden;

  .el-popper__arrow {
    display: none !important;
  }
}

.rail-collapsed-popover--dark.el-popper {
  border: 1px solid rgba(255, 255, 255, 0.14) !important;
  background: rgba(15, 23, 42, 0.92) !important;
  box-shadow:
    0 20px 40px -4px rgba(0, 0, 0, 0.6),
    0 0 0 1px rgba(255, 255, 255, 0.08) inset !important;

  .rail-popover-menu {
    .rail-popover-header {
      border-bottom: 1px solid rgba(255, 255, 255, 0.1);
      color: #f1f5f9;

      .rail-popover-icon {
        color: #38bdf8;
      }
      .rail-popover-title {
        color: #f1f5f9;
      }
    }

    .rail-popover-app {
      color: #cbd5e1;
      .rail-popover-app-icon {
        color: #38bdf8;
      }
      &:hover {
        background: rgba(255, 255, 255, 0.08);
        color: #ffffff;
      }
      &.is-active {
        background: rgba(56, 189, 248, 0.15);
        color: #38bdf8;
      }
    }

    .rail-popover-sub-list {
      border-left: 1.5px solid rgba(255, 255, 255, 0.12);
    }

    .rail-popover-sub-item {
      color: #94a3b8;
      .rail-popover-sub-dot {
        background: #64748b;
      }
      &:hover {
        background: rgba(255, 255, 255, 0.08);
        color: #ffffff;
        .rail-popover-sub-dot {
          background: #38bdf8;
          transform: scale(1.3);
        }
      }
      &.is-active {
        background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%) !important;
        color: #ffffff !important;
        .rail-popover-sub-dot {
          background: #ffffff !important;
        }
        .rail-popover-sub-text {
          color: #ffffff !important;
        }
      }
    }
  }
}

.rail-popover-menu {
  display: flex;
  flex-direction: column;
  padding: 8px 6px;
  min-width: 190px;
  max-width: 260px;
  box-sizing: border-box;

  .rail-popover-header {
    display: flex;
    align-items: center;
    gap: 7px;
    padding: 6px 10px 8px;
    margin-bottom: 4px;
    border-bottom: 1px solid rgba(226, 232, 240, 0.7);
    color: #0f172a;
    font-size: 13px;
    font-weight: 650;
    letter-spacing: -0.01em;

    .rail-popover-icon {
      font-size: 15px;
      color: #2563eb;
    }

    .rail-popover-title {
      font-size: 13px;
      font-weight: 650;
      color: #0f172a;
    }
  }

  .rail-popover-body {
    display: flex;
    flex-direction: column;
    gap: 6px;
    max-height: 420px;
    overflow-y: auto;
    overscroll-behavior: contain;

    &::-webkit-scrollbar {
      width: 4px;
    }
    &::-webkit-scrollbar-thumb {
      background: rgba(148, 163, 184, 0.35);
      border-radius: 4px;
    }
  }

  .rail-popover-item-wrap {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .rail-popover-app {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 6px 10px;
    border-radius: 7px;
    font-size: 12.5px;
    font-weight: 600;
    color: #334155;
    cursor: pointer;
    transition: all 120ms ease;

    .rail-popover-app-icon {
      font-size: 14px;
      color: #2563eb;
    }

    &:hover {
      background: rgba(241, 245, 249, 0.95);
      color: #0f172a;
    }

    &.is-active {
      background: rgba(37, 99, 235, 0.08);
      color: #2563eb;
    }
  }

  .rail-popover-sub-list {
    display: flex;
    flex-direction: column;
    gap: 2px;
    padding-left: 10px;
    margin-left: 10px;
    border-left: 1.5px solid rgba(226, 232, 240, 0.85);
  }

  .rail-popover-sub-item {
    display: flex;
    align-items: center;
    gap: 7px;
    padding: 5px 8px;
    border-radius: 6px;
    font-size: 12px;
    font-weight: 500;
    color: #475569;
    cursor: pointer;
    transition: all 120ms ease;

    .rail-popover-sub-dot {
      width: 4px;
      height: 4px;
      border-radius: 50%;
      background: #94a3b8;
      transition: all 120ms ease;
    }

    .rail-popover-sub-text {
      flex: 1;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    &:hover {
      background: rgba(241, 245, 249, 0.95);
      color: #0f172a;

      .rail-popover-sub-dot {
        background: #2563eb;
        transform: scale(1.3);
      }
    }

    &.is-active {
      background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%) !important;
      color: #ffffff !important;
      font-weight: 600 !important;
      box-shadow: 0 2px 8px rgba(37, 99, 235, 0.35);

      .rail-popover-sub-dot {
        background: #ffffff !important;
        box-shadow: 0 0 4px rgba(255, 255, 255, 0.85);
      }

      .rail-popover-sub-text {
        color: #ffffff !important;
      }
    }
  }
}
</style>
