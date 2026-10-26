-- V56: Complete enterprise menus for Pay Center, Omnichannel Message Gateway, OpenApp, Tenant App Entitlements, and Report Center
-- 1. 租户应用授权与体系管理菜单 (Under Tenant Center, parent_id = 2)
INSERT INTO `iam_menu` (
  `id`, `tenant_id`, `parent_id`, `menu_type`, `menu_code`, `app_code`, `menu_name`,
  `route_path`, `component_path`, `permission_code`, `icon`, `sort_no`,
  `status`, `visible`, `keep_alive`
)
SELECT
  419, 1, 2, 'MENU', 'tenant:apps', 'tenant', '租户应用授权',
  '/tenant/apps', '@/views/tenant/TenantView.vue', 'tenant:query', 'Grid', 15,
  'ENABLED', 1, 1
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM `iam_menu` WHERE `menu_code` = 'tenant:apps' AND `tenant_id` = 1 AND `deleted` = 0
);

-- 2. 统一聚合支付中台 (Under Integration Center, parent_id = 9)
INSERT INTO `iam_menu` (
  `id`, `tenant_id`, `parent_id`, `menu_type`, `menu_code`, `app_code`, `menu_name`,
  `route_path`, `component_path`, `permission_code`, `icon`, `sort_no`,
  `status`, `visible`, `keep_alive`
)
SELECT
  135, 1, 9, 'MENU', 'integration_pay', 'integration', '聚合支付中心',
  '/integration/pay', '@/views/integration/IntegrationView.vue', 'integration:datasource:query', 'CreditCard', 25,
  'ENABLED', 1, 1
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM `iam_menu` WHERE `menu_code` = 'integration_pay' AND `tenant_id` = 1 AND `deleted` = 0
);

-- 3. 多渠道消息与通知中心 (Under Integration Center, parent_id = 9)
INSERT INTO `iam_menu` (
  `id`, `tenant_id`, `parent_id`, `menu_type`, `menu_code`, `app_code`, `menu_name`,
  `route_path`, `component_path`, `permission_code`, `icon`, `sort_no`,
  `status`, `visible`, `keep_alive`
)
SELECT
  136, 1, 9, 'MENU', 'integration_message', 'integration', '消息与通知中心',
  '/integration/message', '@/views/integration/IntegrationView.vue', 'integration:datasource:query', 'Bell', 26,
  'ENABLED', 1, 1
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM `iam_menu` WHERE `menu_code` = 'integration_message' AND `tenant_id` = 1 AND `deleted` = 0
);

-- 4. 企业开放平台与 OpenAPI 网关 (Under Integration Center, parent_id = 9)
INSERT INTO `iam_menu` (
  `id`, `tenant_id`, `parent_id`, `menu_type`, `menu_code`, `app_code`, `menu_name`,
  `route_path`, `component_path`, `permission_code`, `icon`, `sort_no`,
  `status`, `visible`, `keep_alive`
)
SELECT
  137, 1, 9, 'MENU', 'integration_openapp', 'integration', '企业开放平台',
  '/integration/openapp', '@/views/integration/IntegrationView.vue', 'integration:datasource:query', 'Share', 27,
  'ENABLED', 1, 1
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM `iam_menu` WHERE `menu_code` = 'integration_openapp' AND `tenant_id` = 1 AND `deleted` = 0
);

-- 5. 补充 ERP 与 动态中台缺失的 API 权限资源
INSERT INTO `iam_api_resource` (`tenant_id`, `resource_code`, `resource_name`, `http_method`, `url_pattern`, `auth_required`, `status`, `remark`, `created_by`, `updated_by`)
SELECT 1, 'erp:inventory:query', 'ERP库存与物料查询权限', 'GET', '/api/erp/**', 1, 'ENABLED', '进销存ERP查询', 1, 1 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM `iam_api_resource` WHERE `resource_code` = 'erp:inventory:query' AND `tenant_id` = 1 AND `deleted` = 0);

INSERT INTO `iam_api_resource` (`tenant_id`, `resource_code`, `resource_name`, `http_method`, `url_pattern`, `auth_required`, `status`, `remark`, `created_by`, `updated_by`)
SELECT 1, 'erp:product:query', 'ERP商品物料档案查询权限', 'GET', '/api/erp/product/**', 1, 'ENABLED', '进销存ERP物料查询', 1, 1 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM `iam_api_resource` WHERE `resource_code` = 'erp:product:query' AND `tenant_id` = 1 AND `deleted` = 0);

INSERT INTO `iam_api_resource` (`tenant_id`, `resource_code`, `resource_name`, `http_method`, `url_pattern`, `auth_required`, `status`, `remark`, `created_by`, `updated_by`)
SELECT 1, 'erp:purchase:query', 'ERP采购订单查询权限', 'GET', '/api/erp/purchase/**', 1, 'ENABLED', '进销存ERP采购查询', 1, 1 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM `iam_api_resource` WHERE `resource_code` = 'erp:purchase:query' AND `tenant_id` = 1 AND `deleted` = 0);

INSERT INTO `iam_api_resource` (`tenant_id`, `resource_code`, `resource_name`, `http_method`, `url_pattern`, `auth_required`, `status`, `remark`, `created_by`, `updated_by`)
SELECT 1, 'dynamic:meeting_booking:query', '会议室预约中台查询权限', 'GET', '/api/dynamic/meeting_booking/**', 1, 'ENABLED', '动态模型查询', 1, 1 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM `iam_api_resource` WHERE `resource_code` = 'dynamic:meeting_booking:query' AND `tenant_id` = 1 AND `deleted` = 0);

INSERT INTO `iam_api_resource` (`tenant_id`, `resource_code`, `resource_name`, `http_method`, `url_pattern`, `auth_required`, `status`, `remark`, `created_by`, `updated_by`)
SELECT 1, 'dynamic:supply_requisition:query', '办公用品领用中台查询权限', 'GET', '/api/dynamic/supply_requisition/**', 1, 'ENABLED', '动态模型查询', 1, 1 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM `iam_api_resource` WHERE `resource_code` = 'dynamic:supply_requisition:query' AND `tenant_id` = 1 AND `deleted` = 0);

INSERT INTO `iam_api_resource` (`tenant_id`, `resource_code`, `resource_name`, `http_method`, `url_pattern`, `auth_required`, `status`, `remark`, `created_by`, `updated_by`)
SELECT 1, 'dynamic:asset_inventory:query', '固定资产盘点中台查询权限', 'GET', '/api/dynamic/asset_inventory/**', 1, 'ENABLED', '动态模型查询', 1, 1 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM `iam_api_resource` WHERE `resource_code` = 'dynamic:asset_inventory:query' AND `tenant_id` = 1 AND `deleted` = 0);

-- 6. 将所有启用的菜单授权给超级管理员角色 (role_id = 1)
INSERT IGNORE INTO `iam_role_menu` (`tenant_id`, `role_id`, `menu_id`)
SELECT 1, 1, `id` FROM `iam_menu` WHERE `deleted` = 0 AND `status` = 'ENABLED';

-- 7. 将所有启用的 API 权限资源授权给超级管理员角色 (role_id = 1)
INSERT IGNORE INTO `iam_role_api` (`tenant_id`, `role_id`, `api_resource_id`)
SELECT 1, 1, `id` FROM `iam_api_resource` WHERE `deleted` = 0 AND `status` = 'ENABLED';

-- 8. 确保平台根租户 (tenant_id = 1) 拥有 report 等所有应用的授权记录
INSERT INTO `plat_tenant_app` (`tenant_id`, `app_code`, `app_name`, `status`, `remark`, `created_by`, `updated_by`)
SELECT 1, 'report', '单据套打与报表中心', 'ENABLED', '增值税发票、采购单、出入库单、物流面单套打与报表中心', 1, 1 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM `plat_tenant_app` WHERE `tenant_id` = 1 AND `app_code` = 'report' AND `deleted` = 0);
