import type { RouteRecordRaw } from "vue-router";
import ConsoleLayout from "@/layouts/ConsoleLayout.vue";

export const platformRoutes: RouteRecordRaw[] = [
  {
    path: "/",
    component: ConsoleLayout,
    redirect: "/dashboard",
    children: [
      {
        path: "dashboard",
        name: "dashboard",
        component: () => import("@/views/dashboard/DashboardView.vue"),
        meta: {
          title: "平台总览",
          icon: "Grid",
          affix: true
        }
      },
      {
        path: "tenant",
        name: "tenant",
        component: () => import("@/views/tenant/TenantView.vue"),
        meta: {
          title: "租户中心",
          icon: "OfficeBuilding",
          permissionCode: "tenant:query"
        }
      },
      {
        path: "iam",
        name: "iam",
        component: () => import("@/views/iam/IamView.vue"),
        meta: {
          title: "身份与权限中心",
          icon: "UserFilled",
          permissionCode: "iam:user:query"
        }
      },
      {
        path: "system",
        name: "system",
        component: () => import("@/views/system/SystemView.vue"),
        meta: {
          title: "系统中心",
          icon: "Setting",
          permissionCode: "system:config:query"
        }
      },
      {
        path: "integration",
        name: "integration",
        component: () => import("@/views/integration/IntegrationView.vue"),
        meta: {
          title: "集成中心",
          icon: "Connection",
          permissionCode: "integration:datasource:query"
        }
      },
      {
        path: "file",
        name: "file",
        component: () => import("@/views/file/FileCenterView.vue"),
        meta: {
          title: "文件中心",
          icon: "FolderOpened",
          permissionCode: "file:object:query"
        }
      },
      {
        path: "scheduler",
        name: "scheduler",
        component: () => import("@/views/scheduler/SchedulerView.vue"),
        meta: {
          title: "调度中心",
          icon: "Timer",
          permissionCode: "scheduler:job:query"
        }
      },
      {
        path: "codegen",
        name: "codegen",
        component: () => import("@/views/codegen/CodegenView.vue"),
        meta: {
          title: "代码生成中心",
          icon: "MagicStick",
          permissionCode: "codegen:project:query"
        }
      },
      {
        path: "audit",
        name: "audit",
        component: () => import("@/views/audit/AuditView.vue"),
        meta: {
          title: "审计中心",
          icon: "DataAnalysis",
          permissionCode: "audit:operation:query"
        }
      }
    ]
  }
];
