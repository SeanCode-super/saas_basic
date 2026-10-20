import type { RouteRecordRaw } from "vue-router";
import type { IamMenuRow } from "@/api/modules/iam";

export interface MenuTarget {
  path: string;
  query: Record<string, string>;
}

// 自动扫描工程中所有 views 页面组件，彻底解除硬编码
const viewModules = import.meta.glob("@/views/**/*.vue");

export function resolveMenuComponent(componentPath?: string | null): (() => Promise<unknown>) | null {
  if (!componentPath) {
    return null;
  }
  const cleanPath = componentPath.trim();
  // 精确匹配
  if (viewModules[cleanPath]) {
    return viewModules[cleanPath];
  }
  // 灵活匹配相对或绝对视图路径
  const normalizedKey = Object.keys(viewModules).find(
    (key) => key === cleanPath || key.endsWith(cleanPath.replace(/^@\/views\//, ""))
  );
  return normalizedKey ? viewModules[normalizedKey] : null;
}

export function parseMenuTarget(routePath?: string | null): MenuTarget {
  if (!routePath) {
    return { path: "", query: {} };
  }
  const url = new URL(routePath, "https://saas-basics.local");
  const query: Record<string, string> = {};
  url.searchParams.forEach((value, key) => {
    query[key] = value;
  });
  return {
    path: url.pathname,
    query
  };
}

export function buildMenuRoutes(menus: IamMenuRow[]): RouteRecordRaw[] {
  const routes: RouteRecordRaw[] = [];
  const seenPaths = new Set<string>();
  const sortedMenus = [...menus].sort((left, right) => {
    const sortDiff = left.sortNo - right.sortNo;
    return sortDiff !== 0 ? sortDiff : left.id - right.id;
  });

  for (const menu of sortedMenus) {
    if (menu.status !== "ENABLED" || !menu.routePath || !menu.componentPath) {
      continue;
    }
    const component = resolveMenuComponent(menu.componentPath);
    if (!component) {
      continue;
    }
    const target = parseMenuTarget(menu.routePath);
    if (!target.path || seenPaths.has(target.path)) {
      continue;
    }
    const cleanPath = target.path.replace(/^\/+/, "");
    routes.push({
      path: cleanPath,
      name: menu.componentName || `menu-${menu.id}`,
      component,
      meta: {
        title: menu.menuName,
        icon: menu.icon,
        permissionCode: menu.permissionCode,
        keepAlive: menu.keepAlive,
        hidden: !menu.visible,
        menuId: menu.id,
        activeMenu: menu.activeMenu,
        isFrame: menu.isFrame,
        frameSrc: menu.frameSrc,
        affix: menu.isAffix
      }
    });

    if (cleanPath && !cleanPath.includes(":") && !cleanPath.includes("*")) {
      const sectionPath = `${cleanPath}/:section(.*)*`;
      if (!seenPaths.has(sectionPath)) {
        seenPaths.add(sectionPath);
        routes.push({
          path: sectionPath,
          name: `${menu.componentName || `menu-${menu.id}`}-section`,
          component,
          meta: {
            title: menu.menuName,
            icon: menu.icon,
            permissionCode: menu.permissionCode,
            keepAlive: menu.keepAlive,
            hidden: true,
            menuId: menu.id,
            activeMenu: menu.activeMenu,
            isFrame: menu.isFrame,
            frameSrc: menu.frameSrc,
            affix: menu.isAffix
          }
        });
      }
    }
  }

  return routes;
}
