import type { App, DirectiveBinding } from "vue";
import { useAuthStore } from "@/stores/modules/auth";

function toggleFeature(el: HTMLElement, value?: string) {
  const authStore = useAuthStore();
  if (!authStore.hasFeature(value)) {
    el.style.display = "none";
  }
}

export function registerFeatureDirective(app: App) {
  app.directive("feature", {
    mounted(el: HTMLElement, binding: DirectiveBinding<string>) {
      toggleFeature(el, binding.value);
    },
    updated(el: HTMLElement, binding: DirectiveBinding<string>) {
      toggleFeature(el, binding.value);
    }
  });
}
