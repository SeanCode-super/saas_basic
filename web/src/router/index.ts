import NProgress from "nprogress";
import { createRouter, createWebHistory } from "vue-router";
import { ElMessage } from "element-plus";
import { APP_TITLE } from "@/constants/app";
import ConsoleLayout from "@/layouts/ConsoleLayout.vue";
import { useAuthStore } from "@/stores/modules/auth";
import { useMenuStore } from "@/stores/modules/menu";
import { useTenantStore } from "@/stores/modules/tenant";
import { buildPortalLoginQuery } from "@/utils/portal-context";
import { buildMenuRoutes } from "./menu-registry";

const LoginView = () => import("@/views/auth/LoginView.vue");
const NotFoundView = () => import("@/views/error/NotFoundView.vue");

export const router = createRouter({
  history: createWebHistory(import.meta.env.VITE_PUBLIC_PATH),
  routes: [
    {
      path: "/login",
      name: "login",
      component: LoginView,
      meta: {
        title: "登录",
        hidden: true
      }
    },
    {
      path: "/",
      name: "console",
      component: ConsoleLayout,
      meta: {
        hidden: true
      }
    },
    {
      path: "/:pathMatch(.*)*",
      name: "not-found",
      component: NotFoundView,
      meta: {
        title: "页面不存在",
        hidden: true
      }
    }
  ]
});

router.beforeEach(async (to) => {
  NProgress.start();
  const authStore = useAuthStore();
  const menuStore = useMenuStore();
  const tenantStore = useTenantStore();

  if (!tenantStore.currentTenant) {
    tenantStore.bootstrap();
  }

  if (!authStore.bootstrapped) {
    try {
      await authStore.bootstrap();
    } catch {
      await authStore.logout();
    }
  }

  if (to.path === "/login" && authStore.isAuthenticated) {
    if (!menuStore.bootstrapped) {
      await menuStore.bootstrap();
    }
    return toDefaultRoute(menuStore);
  }

  if (to.path !== "/login" && !authStore.isAuthenticated) {
    menuStore.clear();
    return { path: "/login", query: buildPortalLoginQuery(to.fullPath, to.query) };
  }

  if (authStore.isAuthenticated && !menuStore.bootstrapped) {
    await menuStore.bootstrap();
  }

  if (authStore.isAuthenticated && !menuStore.routesReady) {
    const dynamicRoutes = buildMenuRoutes(menuStore.enabledMenus);
    dynamicRoutes.forEach((route) => {
      const absolutePath = `/${String(route.path).replace(/^\/+/, "")}`;
      const routeExists = router.hasRoute(String(route.name)) || router.getRoutes().some((item) => item.path === absolutePath);
      if (!routeExists) {
        router.addRoute("console", route);
      }
    });
    menuStore.markRoutesReady();
    return {
      path: to.path,
      query: to.query,
      hash: to.hash,
      replace: true
    };
  }

  if (authStore.isAuthenticated && to.path === "/") {
    return toDefaultRoute(menuStore);
  }

  if (authStore.isAuthenticated && to.meta?.permissionCode && !authStore.hasPermission(String(to.meta.permissionCode))) {
    ElMessage.warning("当前账号没有访问该页面的权限");
    return toDefaultRoute(menuStore);
  }

  if (authStore.isAuthenticated && to.name === "not-found") {
    return toDefaultRoute(menuStore);
  }

  if (to.meta?.title) {
    document.title = `${String(to.meta.title)} | ${APP_TITLE}`;
  } else {
    document.title = APP_TITLE;
  }

  return true;
});

router.afterEach(() => {
  NProgress.done();
});

function toDefaultRoute(menuStore: ReturnType<typeof useMenuStore>) {
  const defaultEntry = menuStore.firstEntry;
  if (defaultEntry) {
    return {
      path: defaultEntry.path || "/dashboard",
      query: defaultEntry.query
    };
  }
  return { path: "/login" };
}
