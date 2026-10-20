-- V49: Add Server & Application Monitor menu and role grant
INSERT INTO `iam_menu` (
  `tenant_id`, `parent_id`, `menu_type`, `menu_code`, `menu_name`,
  `route_path`, `component_path`, `permission_code`, `icon`, `sort_no`,
  `status`, `visible`, `keep_alive`
)
SELECT
  1, 8, 'MENU', 'server_monitor', '服务与应用监控',
  '/server-monitor', '@/views/monitor/ServerMonitorView.vue', 'system:config:query', 'Monitor', 60,
  'ENABLED', 1, 1
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM `iam_menu` WHERE `menu_code` = 'server_monitor' AND `tenant_id` = 1 AND `deleted` = 0
);

INSERT IGNORE INTO `iam_role_menu` (`tenant_id`, `role_id`, `menu_id`)
SELECT 1, 1, `id` FROM `iam_menu` WHERE `menu_code` = 'server_monitor' AND `tenant_id` = 1 AND `deleted` = 0;
