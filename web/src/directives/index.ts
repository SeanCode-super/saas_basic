import type { App } from "vue";
import { registerButtonPermissionDirective } from "./button-permission";
import { registerFeatureDirective } from "./feature";
import { registerPermissionDirective } from "./permission";

export function registerDirectives(app: App) {
  registerPermissionDirective(app);
  registerButtonPermissionDirective(app);
  registerFeatureDirective(app);
}
