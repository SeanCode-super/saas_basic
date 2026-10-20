import type { App, DirectiveBinding } from "vue";
import { useAuthStore } from "@/stores/modules/auth";

export function usePermission() {
  const authStore = useAuthStore();

  function hasPermi(permission?: string | string[]): boolean {
    if (!permission) return true;
    if (authStore.currentUser?.userType === "PLATFORM") return true;

    const userPerms = authStore.currentUser?.permissions || [];
    const buttonPerms = authStore.currentUser?.buttonPermissions || [];
    const allPerms = new Set([...userPerms, ...buttonPerms]);

    if (Array.isArray(permission)) {
      return permission.some((p) => allPerms.has(p) || userPerms.includes(p) || buttonPerms.includes(p));
    }
    return allPerms.has(permission) || userPerms.includes(permission) || buttonPerms.includes(permission);
  }

  function hasRole(role?: string | string[]): boolean {
    if (!role) return true;
    if (authStore.currentUser?.userType === "PLATFORM") return true;
    // In our system, roles can be checked if role codes are exposed or admin check
    return true;
  }

  return {
    hasPermi,
    hasRole
  };
}

function checkPermission(el: HTMLElement, binding: DirectiveBinding<string | string[]>) {
  const { value } = binding;
  const { hasPermi } = usePermission();

  if (value && !hasPermi(value)) {
    el.style.display = "none";
  } else {
    el.style.display = "";
  }
}

export function registerPermissionDirective(app: App) {
  // 1. 标准企业级按钮权限指令: v-hasPermi="['iam:menu:create']"
  app.directive("hasPermi", {
    mounted(el: HTMLElement, binding: DirectiveBinding<string | string[]>) {
      checkPermission(el, binding);
    },
    updated(el: HTMLElement, binding: DirectiveBinding<string | string[]>) {
      checkPermission(el, binding);
    }
  });

  // 2. 兼容现有 v-permission
  app.directive("permission", {
    mounted(el: HTMLElement, binding: DirectiveBinding<string | string[]>) {
      checkPermission(el, binding);
    },
    updated(el: HTMLElement, binding: DirectiveBinding<string | string[]>) {
      checkPermission(el, binding);
    }
  });
}
