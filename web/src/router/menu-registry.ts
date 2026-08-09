import type { RouteRecordRaw } from "vue-router";
import type { IamMenuRow } from "@/api/modules/iam";

export interface MenuTarget {
  path: string;
  query: Record<string, string>;
}

const menuComponentRegistry: Record<string, () => Promise<unknown>> = {
  "@/views/dashboard/DashboardView.vue": () => import("@/views/dashboard/DashboardView.vue"),
  "@/views/tenant/TenantView.vue": () => import("@/views/tenant/TenantView.vue"),
  "@/views/tenant/TenantOverviewView.vue": () => import("@/views/tenant/TenantOverviewView.vue"),
  "@/views/tenant/TenantOnboardingView.vue": () => import("@/views/tenant/TenantOnboardingView.vue"),
  "@/views/tenant/TenantPackageView.vue": () => import("@/views/tenant/TenantPackageView.vue"),
  "@/views/tenant/TenantIsolationView.vue": () => import("@/views/tenant/TenantIsolationView.vue"),
  "@/views/tenant/TenantLedgerView.vue": () => import("@/views/tenant/TenantLedgerView.vue"),
  "@/views/iam/IamView.vue": () => import("@/views/iam/IamView.vue"),
  "@/views/iam/IamFoundationView.vue": () => import("@/views/iam/IamFoundationView.vue"),
  "@/views/iam/IamDepartmentView.vue": () => import("@/views/iam/IamDepartmentView.vue"),
  "@/views/iam/IamPositionView.vue": () => import("@/views/iam/IamPositionView.vue"),
  "@/views/iam/IamEmployeeView.vue": () => import("@/views/iam/IamEmployeeView.vue"),
  "@/views/iam/IamUserView.vue": () => import("@/views/iam/IamUserView.vue"),
  "@/views/iam/IamRoleView.vue": () => import("@/views/iam/IamRoleView.vue"),
  "@/views/iam/IamApiResourceView.vue": () => import("@/views/iam/IamApiResourceView.vue"),
  "@/views/iam/IamUserRoleView.vue": () => import("@/views/iam/IamUserRoleView.vue"),
  "@/views/iam/IamRoleApiView.vue": () => import("@/views/iam/IamRoleApiView.vue"),
  "@/views/iam/IamMenuView.vue": () => import("@/views/iam/IamMenuView.vue"),
  "@/views/iam/IamDataScopeView.vue": () => import("@/views/iam/IamDataScopeView.vue"),
  "@/views/iam/IamLoginPolicyView.vue": () => import("@/views/iam/IamLoginPolicyView.vue"),
  "@/views/iam/IamPasswordPolicyView.vue": () => import("@/views/iam/IamPasswordPolicyView.vue"),
  "@/views/system/SystemView.vue": () => import("@/views/system/SystemView.vue"),
  "@/views/system/SystemOverviewView.vue": () => import("@/views/system/SystemOverviewView.vue"),
  "@/views/system/SystemSecurityView.vue": () => import("@/views/system/SystemSecurityView.vue"),
  "@/views/system/SystemLocaleView.vue": () => import("@/views/system/SystemLocaleView.vue"),
  "@/views/system/SystemSequenceView.vue": () => import("@/views/system/SystemSequenceView.vue"),
  "@/views/system/SystemRegistryView.vue": () => import("@/views/system/SystemRegistryView.vue"),
  "@/views/integration/IntegrationView.vue": () => import("@/views/integration/IntegrationView.vue"),
  "@/views/integration/IntegrationOverviewView.vue": () => import("@/views/integration/IntegrationOverviewView.vue"),
  "@/views/integration/IntegrationConnectionView.vue": () => import("@/views/integration/IntegrationConnectionView.vue"),
  "@/views/integration/IntegrationCallbackView.vue": () => import("@/views/integration/IntegrationCallbackView.vue"),
  "@/views/integration/IntegrationHealthView.vue": () => import("@/views/integration/IntegrationHealthView.vue"),
  "@/views/integration/IntegrationRegistryView.vue": () => import("@/views/integration/IntegrationRegistryView.vue"),
  "@/views/file/FileCenterView.vue": () => import("@/views/file/FileCenterView.vue"),
  "@/views/file/FileOverviewView.vue": () => import("@/views/file/FileOverviewView.vue"),
  "@/views/file/FileStorageView.vue": () => import("@/views/file/FileStorageView.vue"),
  "@/views/file/FileUploadView.vue": () => import("@/views/file/FileUploadView.vue"),
  "@/views/file/FileLifecycleView.vue": () => import("@/views/file/FileLifecycleView.vue"),
  "@/views/file/FileAuditView.vue": () => import("@/views/file/FileAuditView.vue"),
  "@/views/scheduler/SchedulerView.vue": () => import("@/views/scheduler/SchedulerView.vue"),
  "@/views/scheduler/SchedulerOverviewView.vue": () => import("@/views/scheduler/SchedulerOverviewView.vue"),
  "@/views/scheduler/SchedulerStrategyView.vue": () => import("@/views/scheduler/SchedulerStrategyView.vue"),
  "@/views/scheduler/SchedulerPipelineView.vue": () => import("@/views/scheduler/SchedulerPipelineView.vue"),
  "@/views/scheduler/SchedulerAlarmView.vue": () => import("@/views/scheduler/SchedulerAlarmView.vue"),
  "@/views/scheduler/SchedulerLogView.vue": () => import("@/views/scheduler/SchedulerLogView.vue"),
  "@/views/codegen/CodegenView.vue": () => import("@/views/codegen/CodegenView.vue"),
  "@/views/codegen/CodegenOverviewView.vue": () => import("@/views/codegen/CodegenOverviewView.vue"),
  "@/views/codegen/CodegenMetadataView.vue": () => import("@/views/codegen/CodegenMetadataView.vue"),
  "@/views/codegen/CodegenTemplateView.vue": () => import("@/views/codegen/CodegenTemplateView.vue"),
  "@/views/codegen/CodegenGateView.vue": () => import("@/views/codegen/CodegenGateView.vue"),
  "@/views/codegen/CodegenArtifactView.vue": () => import("@/views/codegen/CodegenArtifactView.vue"),
  "@/views/audit/AuditView.vue": () => import("@/views/audit/AuditView.vue"),
  "@/views/audit/AuditOverviewView.vue": () => import("@/views/audit/AuditOverviewView.vue"),
  "@/views/audit/AuditTrailView.vue": () => import("@/views/audit/AuditTrailView.vue"),
  "@/views/audit/AuditRiskView.vue": () => import("@/views/audit/AuditRiskView.vue"),
  "@/views/audit/AuditBlacklistView.vue": () => import("@/views/audit/AuditBlacklistView.vue"),
  "@/views/audit/AuditCatalogView.vue": () => import("@/views/audit/AuditCatalogView.vue")
};

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
    const component = menuComponentRegistry[menu.componentPath];
    if (!component) {
      continue;
    }
    const target = parseMenuTarget(menu.routePath);
    if (!target.path || seenPaths.has(target.path)) {
      continue;
    }
    seenPaths.add(target.path);
    routes.push({
      path: target.path.replace(/^\/+/, ""),
      name: `menu-${menu.id}`,
      component,
      meta: {
        title: menu.menuName,
        icon: menu.icon,
        permissionCode: menu.permissionCode,
        keepAlive: menu.keepAlive,
        hidden: !menu.visible,
        menuId: menu.id
      }
    });
  }

  return routes;
}
