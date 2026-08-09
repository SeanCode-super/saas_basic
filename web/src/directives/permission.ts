import type { App, DirectiveBinding } from "vue";
import { useAuthStore } from "@/stores/modules/auth";

function togglePermission(el: HTMLElement, value?: string) {
  const authStore = useAuthStore();
  if (!authStore.hasPermission(value)) {
    el.style.display = "none";
  }
}

export function registerPermissionDirective(app: App) {
  app.directive("permission", {
    mounted(el: HTMLElement, binding: DirectiveBinding<string>) {
      togglePermission(el, binding.value);
    },
    updated(el: HTMLElement, binding: DirectiveBinding<string>) {
      togglePermission(el, binding.value);
    }
  });
}
