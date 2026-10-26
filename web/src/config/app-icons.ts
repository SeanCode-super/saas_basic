import type { Component } from "vue";
import {
  Bell,
  Briefcase,
  Clock,
  Connection,
  CreditCard,
  DataAnalysis,
  DocumentChecked,
  FolderOpened,
  Grid,
  OfficeBuilding,
  Printer,
  Setting,
  Tickets,
  Tools,
  TrendCharts,
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
  audit: DocumentChecked,
  report: Printer,
  report_print: Printer,
  crm: TrendCharts,
  erp: Briefcase,
  pay: CreditCard
};

export function resolveAppIcon(code?: string, customIcon?: string): Component | string {
  if (customIcon && customIcon.trim()) {
    return customIcon.trim();
  }
  return (code && APP_ICONS[code]) || Grid;
}
