INSERT INTO `sys_config` (
  `id`, `tenant_id`, `config_group`, `config_key`, `config_name`, `config_value`,
  `value_type`, `status`, `remark`, `created_by`, `updated_by`
)
VALUES
  (1, 1, 'SECURITY', 'password.min_length', '密码最小长度', '12', 'NUMBER', 'ENABLED', '平台默认安全配置', 0, 0),
  (2, 1, 'SECURITY', 'password.need_special', '密码必须包含特殊字符', 'false', 'BOOLEAN', 'ENABLED', '平台默认安全配置', 0, 0),
  (3, 1, 'GLOBAL', 'i18n.default_locale', '默认语言环境', 'zh-CN', 'STRING', 'ENABLED', '平台全局默认配置', 0, 0)
ON DUPLICATE KEY UPDATE
  `config_name` = VALUES(`config_name`),
  `config_value` = VALUES(`config_value`),
  `value_type` = VALUES(`value_type`),
  `status` = VALUES(`status`),
  `updated_by` = VALUES(`updated_by`);

INSERT INTO `int_datasource` (
  `id`, `tenant_id`, `datasource_code`, `datasource_name`, `datasource_type`, `usage_type`,
  `host`, `port`, `database_name`, `username`, `status`, `test_status`, `last_tested_at`,
  `remark`, `created_by`, `updated_by`
)
VALUES
  (1, 1, 'primary_mysql', '主业务 MySQL', 'MYSQL', 'BUSINESS',
   '127.0.0.1', 3306, 'saas_basics', 'root', 'ENABLED', 'PASSED', NOW(3),
   '平台当前使用的主数据库连接', 0, 0),
  (2, 1, 'report_http', '报表 HTTP 接口', 'HTTP', 'REPORTING',
   'reports.internal.local', 80, '', 'report-reader', 'ENABLED', 'UNTESTED', NULL,
   '示例报表接口数据源', 0, 0)
ON DUPLICATE KEY UPDATE
  `datasource_name` = VALUES(`datasource_name`),
  `datasource_type` = VALUES(`datasource_type`),
  `usage_type` = VALUES(`usage_type`),
  `host` = VALUES(`host`),
  `port` = VALUES(`port`),
  `database_name` = VALUES(`database_name`),
  `username` = VALUES(`username`),
  `status` = VALUES(`status`),
  `test_status` = VALUES(`test_status`),
  `last_tested_at` = VALUES(`last_tested_at`),
  `remark` = VALUES(`remark`),
  `updated_by` = VALUES(`updated_by`);
