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
        path: "tenant/:section*",
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
        path: "organization",
        name: "organization",
        component: () => import("@/views/organization/OrganizationView.vue"),
        meta: {
          title: "组织与人员中心",
          icon: "OfficeBuilding",
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
        path: "system/:section",
        name: "system-section",
        component: () => import("@/views/system/SystemView.vue"),
        props: true,
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
      },
      {
        path: "workflow",
        name: "workflow",
        component: () => import("@/views/workflow/WorkflowView.vue"),
        meta: {
          title: "工作流与审批中心",
          icon: "Tickets",
          permissionCode: "workflow:task:query"
        }
      },
      {
        path: "print",
        name: "print",
        component: () => import("@/views/report/PrintCenterView.vue"),
        meta: {
          title: "单据套打中心",
          icon: "Printer",
          permissionCode: "report:print:query"
        }
      },
      {
        path: "erp",
        name: "erp",
        component: () => import("@/views/erp/ErpView.vue"),
        meta: {
          title: "进销存 ERP",
          icon: "Briefcase",
          permissionCode: "erp:inventory:query"
        }
      },
      {
        path: "server-monitor",
        name: "server-monitor",
        component: () => import("@/views/monitor/ServerMonitorView.vue"),
        meta: {
          title: "服务与应用监控",
          icon: "Monitor",
          permissionCode: "system:config:query"
        }
      },
      {
        path: "crm",
        name: "crm",
        component: () => import("@/views/crm/CrmView.vue"),
        meta: {
          title: "客户关系 CRM",
          icon: "TrendCharts"
        }
      },
      {
        path: "crm/:tab",
        name: "crm-tab",
        component: () => import("@/views/crm/CrmView.vue"),
        meta: {
          title: "客户关系 CRM",
          icon: "TrendCharts"
        }
      },
      {
        path: "profile",
        name: "profile",
        component: () => import("@/views/profile/ProfileView.vue"),
        meta: {
          title: "个人中心",
          icon: "User"
        }
      }
    ]

  }
];
