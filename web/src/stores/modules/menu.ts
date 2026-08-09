import { computed, ref } from "vue";
import { defineStore } from "pinia";
import { fetchCurrentIamMenus } from "@/api/modules/iam";
import type { IamMenuRow } from "@/api/modules/iam";
import { parseMenuTarget } from "@/router/menu-registry";
import { useAuthStore } from "./auth";

const MENU_FETCH_TIMEOUT = 4000;

type MenuBootstrapSource = "remote" | "empty";

export interface MenuNavItem {
  id: number;
  code: string;
  parentId: number;
  title: string;
  icon?: string;
  path: string;
  query: Record<string, string>;
  permissionCode?: string;
  menuType: string;
  sortNo: number;
  visible: boolean;
  children: MenuNavItem[];
}

export const useMenuStore = defineStore("menu", () => {
  const rawMenus = ref<IamMenuRow[]>([]);
  const bootstrapped = ref(false);
  const routesReady = ref(false);
  const loading = ref(false);
  const bootstrapSource = ref<MenuBootstrapSource>("empty");
  const lastError = ref("");
  const authStore = useAuthStore();
  let pendingBootstrap: Promise<void> | null = null;

  const enabledMenus = computed(() =>
    rawMenus.value
      .filter((item) => item.status === "ENABLED")
      .filter((item) => authStore.hasPermission(item.permissionCode ?? undefined))
  );

  const navigation = computed(() => buildNavigationTree(enabledMenus.value));

  const firstEntry = computed(() => findFirstEntry(navigation.value));

  async function bootstrap(force = false) {
    if (pendingBootstrap && !force) {
      return pendingBootstrap;
    }

    if (bootstrapped.value && !force) {
      return;
    }

    pendingBootstrap = refreshFromRemote();
    await pendingBootstrap;
  }

  async function refresh() {
    await bootstrap(true);
  }

  function clear() {
    rawMenus.value = [];
    bootstrapped.value = false;
    routesReady.value = false;
    loading.value = false;
    bootstrapSource.value = "empty";
    lastError.value = "";
  }

  function markRoutesReady() {
    routesReady.value = true;
  }

  return {
    rawMenus,
    bootstrapped,
    routesReady,
    loading,
    bootstrapSource,
    lastError,
    enabledMenus,
    navigation,
    firstEntry,
    bootstrap,
    refresh,
    clear,
    markRoutesReady
  };

  function resetToEmpty() {
    rawMenus.value = [];
    bootstrapped.value = false;
    bootstrapSource.value = "empty";
  }

  async function refreshFromRemote() {
    loading.value = true;
    lastError.value = "";

    try {
      const menus = await fetchCurrentIamMenus({ timeout: MENU_FETCH_TIMEOUT });
      rawMenus.value = menus;
      bootstrapped.value = menus.length > 0;
      bootstrapSource.value = "remote";
      routesReady.value = false;
    } catch (error: any) {
      lastError.value = error?.response?.data?.message ?? error?.message ?? "菜单加载失败";
      resetToEmpty();
    } finally {
      loading.value = false;
      pendingBootstrap = null;
    }
  }
});

function buildNavigationTree(menus: IamMenuRow[]): MenuNavItem[] {
  const visibleMenus = menus
    .filter((item) => item.visible)
    .sort((left, right) => {
      const sortDiff = left.sortNo - right.sortNo;
      return sortDiff !== 0 ? sortDiff : left.id - right.id;
    });

  const nodeMap = new Map<number, MenuNavItem>();
  const roots: MenuNavItem[] = [];

  for (const menu of visibleMenus) {
    const target = parseMenuTarget(menu.routePath);
    nodeMap.set(menu.id, {
      id: menu.id,
      code: menu.menuCode,
      parentId: menu.parentId,
      title: menu.menuName,
      icon: menu.icon ?? undefined,
      path: target.path,
      query: target.query,
      permissionCode: menu.permissionCode ?? undefined,
      menuType: menu.menuType,
      sortNo: menu.sortNo,
      visible: menu.visible,
      children: []
    });
  }

  for (const node of nodeMap.values()) {
    const parent = nodeMap.get(node.parentId);
    if (parent) {
      parent.children.push(node);
    } else {
      roots.push(node);
    }
  }

  return sortTree(roots);
}

function sortTree(nodes: MenuNavItem[]): MenuNavItem[] {
  return nodes
    .sort((left, right) => {
      const sortDiff = left.sortNo - right.sortNo;
      return sortDiff !== 0 ? sortDiff : left.id - right.id;
    })
    .map((node) => ({
      ...node,
      children: sortTree(node.children)
    }));
}

function findFirstEntry(nodes: MenuNavItem[]): MenuNavItem | null {
  for (const node of nodes) {
    if (node.path) {
      return node;
    }
    const child = findFirstEntry(node.children);
    if (child) {
      return child;
    }
  }
  return null;
}
