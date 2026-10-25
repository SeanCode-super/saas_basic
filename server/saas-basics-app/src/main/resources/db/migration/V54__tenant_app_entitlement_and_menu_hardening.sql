-- V54__tenant_app_entitlement_and_menu_hardening.sql
-- Hardening of Top-Level Tenant Application Entitlements and Menu Visibility Isolation

-- 1. Ensure ERP system menus exist with non-conflicting IDs (500-503)
INSERT INTO `iam_menu` (`id`, `tenant_id`, `parent_id`, `menu_type`, `menu_code`, `app_code`, `menu_name`, `route_path`, `component_path`, `permission_code`, `icon`, `sort_no`, `status`, `deleted`)
VALUES
  (500, 1, 0, 'DIRECTORY', 'erp', 'erp', '进销存 ERP', '/erp', '', 'erp:inventory:query', 'Briefcase', 6, 'ENABLED', 0),
  (501, 1, 500, 'MENU', 'erp_product', 'erp', '物料商品档案', '/erp/product', '@/views/erp/ErpView.vue', 'erp:product:query', 'Goods', 1, 'ENABLED', 0),
  (502, 1, 500, 'MENU', 'erp_inventory', 'erp', '实时库存台账', '/erp/inventory', '@/views/erp/ErpView.vue', 'erp:inventory:query', 'Box', 2, 'ENABLED', 0),
  (503, 1, 500, 'MENU', 'erp_purchase', 'erp', '采购订单履约', '/erp/purchase', '@/views/erp/ErpView.vue', 'erp:purchase:query', 'ShoppingCart', 3, 'ENABLED', 0)
ON DUPLICATE KEY UPDATE
  `app_code` = VALUES(`app_code`),
  `menu_name` = VALUES(`menu_name`),
  `route_path` = VALUES(`route_path`),
  `status` = VALUES(`status`),
  `deleted` = 0;

-- 2. Seed baseline applications for Tenant 1 into plat_tenant_app
INSERT INTO `plat_tenant_app` (`tenant_id`, `app_code`, `app_name`, `status`, `opened_at`, `remark`, `created_by`, `updated_by`, `deleted`)
VALUES
  (1, 'iam', '统一身份与权限中心 (IAM)', 'ENABLED', NOW(3), '基线应用权限', 1, 1, 0),
  (1, 'tenant', '多租户与计量治理中心', 'ENABLED', NOW(3), '基线应用权限', 1, 1, 0),
  (1, 'workflow', '企业可视化审批流 (BPMN)', 'ENABLED', NOW(3), '基线应用权限', 1, 1, 0),
  (1, 'file', '分布式文件存储中心', 'ENABLED', NOW(3), '基线应用权限', 1, 1, 0),
  (1, 'crm', '客户关系管理 (CRM)', 'ENABLED', NOW(3), '基线应用权限', 1, 1, 0),
  (1, 'erp', '进销存 ERP 供应链', 'ENABLED', NOW(3), '基线应用权限', 1, 1, 0),
  (1, 'scheduler', '分布式任务调度中心', 'ENABLED', NOW(3), '基线应用权限', 1, 1, 0),
  (1, 'audit', '合规审计与风控中心', 'ENABLED', NOW(3), '基线应用权限', 1, 1, 0),
  (1, 'integration', '开放集成与智能中心', 'ENABLED', NOW(3), '基线应用权限', 1, 1, 0),
  (1, 'system', '系统中心与企业 SSO', 'ENABLED', NOW(3), '基线应用权限', 1, 1, 0),
  (1, 'codegen', '低代码与代码生成中心', 'ENABLED', NOW(3), '基线应用权限', 1, 1, 0)
ON DUPLICATE KEY UPDATE
  `app_name` = VALUES(`app_name`),
  `deleted` = 0;
