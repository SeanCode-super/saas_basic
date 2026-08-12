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
  Tools,
  User
} from "@element-plus/icons-vue";

const APP_ICONS: Record<string, Component> = {
  dashboard: DataAnalysis,
  tenant: OfficeBuilding,
  iam: User,
  system: Setting,
  integration: Connection,
  file: FolderOpened,
  scheduler: Clock,
  codegen: Tools,
  audit: DocumentChecked
};

export function resolveAppIcon(code?: string): Component {
  return (code && APP_ICONS[code]) || Grid;
}
