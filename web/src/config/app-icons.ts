import type { Component } from "vue";
import {
  Clock,
  Connection,
  DataAnalysis,
  DocumentChecked,
  FolderOpened,
  Grid,
  OfficeBuilding,
  Setting,
  Tickets,
  Tools,
  User
} from "@element-plus/icons-vue";

const APP_ICONS: Record<string, Component> = {
  dashboard: DataAnalysis,
  tenant: OfficeBuilding,
  iam: User,
  workflow: Tickets,
  system: Setting,
  integration: Connection,
  file: FolderOpened,
  scheduler: Clock,
  codegen: Tools,
  audit: DocumentChecked
};

export function resolveAppIcon(code?: string, customIcon?: string): Component | string {
  if (customIcon && customIcon.trim()) {
    return customIcon.trim();
  }
  return (code && APP_ICONS[code]) || Grid;
}
