-- V55: Add Report & Print Center App, Menu and Permissions
INSERT INTO `iam_menu` (
  `id`, `tenant_id`, `parent_id`, `menu_type`, `menu_code`, `app_code`, `menu_name`,
  `route_path`, `component_path`, `permission_code`, `icon`, `sort_no`,
  `status`, `visible`, `keep_alive`
)
SELECT
  510, 1, 0, 'MENU', 'report_print', 'report', '单据套打与报表中心',
  '/print', '@/views/report/PrintCenterView.vue', 'report:print:query', 'Printer', 88,
  'ENABLED', 1, 1
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM `iam_menu` WHERE `menu_code` = 'report_print' AND `tenant_id` = 1 AND `deleted` = 0
);

INSERT IGNORE INTO `iam_role_menu` (`tenant_id`, `role_id`, `menu_id`)
SELECT 1, 1, `id` FROM `iam_menu` WHERE `menu_code` = 'report_print' AND `tenant_id` = 1 AND `deleted` = 0;

INSERT INTO `iam_api_resource` (
  `tenant_id`, `resource_code`, `resource_name`, `http_method`, `url_pattern`,
  `auth_required`, `status`, `remark`, `created_by`, `updated_by`
)
SELECT
  1, 'report:print:query', '单据套打与报表预览权限', 'GET', '/api/report/**',
  1, 'ENABLED', '报表与套打中心查询权限', 1, 1
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM `iam_api_resource` WHERE `resource_code` = 'report:print:query' AND `tenant_id` = 1 AND `deleted` = 0
);

INSERT IGNORE INTO `iam_role_api` (`tenant_id`, `role_id`, `api_resource_id`)
SELECT 1, 1, `id` FROM `iam_api_resource` WHERE `resource_code` = 'report:print:query' AND `tenant_id` = 1 AND `deleted` = 0;

INSERT INTO `plat_tenant_app` (
  `tenant_id`, `app_code`, `app_name`, `status`, `remark`, `created_by`, `updated_by`
)
SELECT
  1, 'report', '单据套打与报表中心', 'ENABLED', '增值税发票、采购单、出入库单、物流面单套打与报表中心', 1, 1
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM `plat_tenant_app` WHERE `tenant_id` = 1 AND `app_code` = 'report' AND `deleted` = 0
);
