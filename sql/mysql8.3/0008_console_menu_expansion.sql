INSERT INTO `iam_menu` (
  `id`, `tenant_id`, `parent_id`, `menu_type`, `menu_code`, `menu_name`, `route_path`,
  `component_path`, `permission_code`, `icon`, `visible`, `keep_alive`, `sort_no`,
  `status`, `meta_json`, `remark`, `created_by`, `updated_by`
)
VALUES
  (8, 1, 0, 'MENU', 'system', '系统中心', '/system', '@/views/system/SystemView.vue', 'system:config:query', 'Setting', 1, 1, 30, 'ENABLED', JSON_OBJECT('domain', 'system'), '系统配置与基线入口', 0, 0),
  (9, 1, 0, 'MENU', 'integration', '集成中心', '/integration', '@/views/integration/IntegrationView.vue', 'integration:datasource:query', 'Connection', 1, 1, 40, 'ENABLED', JSON_OBJECT('domain', 'integration'), '数据源与集成入口', 0, 0),
  (10, 1, 0, 'MENU', 'file', '文件中心', '/file', '@/views/file/FileCenterView.vue', 'file:object:query', 'FolderOpened', 1, 1, 50, 'ENABLED', JSON_OBJECT('domain', 'file'), '文件与存储治理入口', 0, 0),
  (11, 1, 0, 'MENU', 'scheduler', '调度中心', '/scheduler', '@/views/scheduler/SchedulerView.vue', 'scheduler:job:query', 'Timer', 1, 1, 60, 'ENABLED', JSON_OBJECT('domain', 'scheduler'), '任务调度治理入口', 0, 0),
  (12, 1, 0, 'MENU', 'codegen', '代码生成中心', '/codegen', '@/views/codegen/CodegenView.vue', 'codegen:project:query', 'MagicStick', 1, 1, 70, 'ENABLED', JSON_OBJECT('domain', 'codegen'), '代码生成治理入口', 0, 0),
  (13, 1, 0, 'MENU', 'audit', '审计中心', '/audit', '@/views/audit/AuditView.vue', 'audit:operation:query', 'DataAnalysis', 1, 1, 80, 'ENABLED', JSON_OBJECT('domain', 'audit'), '审计与风控入口', 0, 0)
AS new
ON DUPLICATE KEY UPDATE
  `parent_id` = new.`parent_id`,
  `menu_type` = new.`menu_type`,
  `menu_name` = new.`menu_name`,
  `route_path` = new.`route_path`,
  `component_path` = new.`component_path`,
  `permission_code` = new.`permission_code`,
  `icon` = new.`icon`,
  `visible` = new.`visible`,
  `keep_alive` = new.`keep_alive`,
  `sort_no` = new.`sort_no`,
  `status` = new.`status`,
  `meta_json` = new.`meta_json`,
  `remark` = new.`remark`,
  `updated_by` = new.`updated_by`;
