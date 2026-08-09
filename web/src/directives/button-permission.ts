import type { App, DirectiveBinding } from "vue";
import { useAuthStore } from "@/stores/modules/auth";

interface ButtonPermissionBindingValue {
  code?: string;
  fallback?: string;
}

function toggleButtonPermission(el: HTMLElement, value?: string | ButtonPermissionBindingValue) {
  const authStore = useAuthStore();
  const code = typeof value === "string" ? value : value?.code;
  const fallback = typeof value === "string" ? undefined : value?.fallback;
  const allowed = authStore.hasButtonPermission(code) || authStore.hasPermission(fallback);
  el.style.display = allowed ? "" : "none";
}

export function registerButtonPermissionDirective(app: App) {
  app.directive("button-permission", {
    mounted(el: HTMLElement, binding: DirectiveBinding<string | ButtonPermissionBindingValue>) {
      toggleButtonPermission(el, binding.value);
    },
    updated(el: HTMLElement, binding: DirectiveBinding<string | ButtonPermissionBindingValue>) {
      toggleButtonPermission(el, binding.value);
    }
  });
}
