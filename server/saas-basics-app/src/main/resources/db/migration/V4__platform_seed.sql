INSERT INTO `plat_tenant_package` (
  `id`, `package_code`, `package_name`, `package_type`, `status`,
  `max_user_count`, `max_dept_count`, `max_storage_mb`, `max_api_per_minute`,
  `feature_flags_json`, `sort_no`, `remark`, `created_by`, `updated_by`
)
SELECT
  1, 'SUPREME', '至尊版', 'PLATFORM', 'ENABLED',
  10000, 2000, 102400, 200000,
  JSON_ARRAY('saas.codegen.preview', 'saas.tenant.package', 'saas.file.versioning'),
  1, '平台默认套餐', 0, 0
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM `plat_tenant_package` WHERE `id` = 1 OR `package_code` = 'SUPREME'
);

INSERT INTO `plat_tenant` (
  `id`, `tenant_code`, `tenant_name`, `tenant_type`, `package_id`, `status`,
  `isolation_mode`, `contact_name`, `contact_mobile`, `contact_email`,
  `timezone`, `locale`, `remark`, `created_by`, `updated_by`
)
SELECT
  1, 'platform', '平台空间', 'PLATFORM', 1, 'ENABLED',
  'PLATFORM', '平台管理员', '13800000000', 'admin@saasbasics.local',
  'Asia/Shanghai', 'zh-CN', '默认平台租户', 0, 0
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM `plat_tenant` WHERE `id` = 1 OR `tenant_code` = 'platform'
);

INSERT INTO `plat_tenant_app` (
  `id`, `tenant_id`, `app_code`, `app_name`, `status`, `opened_at`,
  `config_json`, `remark`, `created_by`, `updated_by`
)
SELECT
  1, 1, 'console', '平台控制台', 'ENABLED', NOW(3),
  JSON_OBJECT('theme', 'saas-basics', 'locale', 'zh-CN'),
  '默认控制台应用', 0, 0
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM `plat_tenant_app` WHERE `id` = 1 OR (`tenant_id` = 1 AND `app_code` = 'console' AND `deleted` = 0)
);

INSERT INTO `iam_login_policy` (
  `id`, `tenant_id`, `policy_code`, `policy_name`, `allow_password_login`, `allow_sms_login`,
  `allow_email_login`, `allow_social_login`, `force_mfa`, `session_timeout_minutes`,
  `max_failed_count`, `lock_minutes`, `device_trust_days`, `status`, `remark`, `created_by`, `updated_by`
)
SELECT
  1, 1, 'DEFAULT', '平台默认登录策略', 1, 0,
  0, 0, 0, 480,
  5, 30, 0, 'ENABLED', '平台默认登录策略', 0, 0
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM `iam_login_policy` WHERE `id` = 1 OR (`tenant_id` = 1 AND `policy_code` = 'DEFAULT' AND `deleted` = 0)
);

INSERT INTO `iam_password_policy` (
  `id`, `tenant_id`, `policy_code`, `policy_name`, `min_length`, `max_length`,
  `require_uppercase`, `require_lowercase`, `require_number`, `require_special`,
  `password_history_limit`, `password_expire_days`, `temp_password_expire_hours`,
  `status`, `remark`, `created_by`, `updated_by`
)
SELECT
  1, 1, 'DEFAULT', '平台默认密码策略', 8, 20,
  1, 1, 1, 0,
  5, 0, 24,
  'ENABLED', '平台默认密码策略', 0, 0
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM `iam_password_policy` WHERE `id` = 1 OR (`tenant_id` = 1 AND `policy_code` = 'DEFAULT' AND `deleted` = 0)
);

INSERT INTO `iam_user` (
  `id`, `tenant_id`, `user_code`, `username`, `nickname`, `employee_id`,
  `user_type`, `status`, `mobile`, `email`, `password_hash`, `password_changed_at`,
  `need_reset_password`, `remark`, `created_by`, `updated_by`
)
SELECT
  1, 1, 'PLATFORM_ADMIN', 'platform.admin', '平台管理员', 0,
  'PLATFORM', 'ENABLED', '13800000000', 'admin@saasbasics.local',
  'ad89b64d66caa8e30e5d5ce4a9763f4ecc205814c412175f3e2c50027471426d', NOW(3),
  0, '默认平台管理员账号', 0, 0
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM `iam_user` WHERE `id` = 1 OR (`tenant_id` = 1 AND `username` = 'platform.admin' AND `deleted` = 0)
);

INSERT INTO `iam_role_group` (
  `id`, `tenant_id`, `group_code`, `group_name`, `status`, `sort_no`,
  `remark`, `created_by`, `updated_by`
)
SELECT
  1, 1, 'PLATFORM', '平台角色组', 'ENABLED', 1,
  '默认平台角色组', 0, 0
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM `iam_role_group` WHERE `id` = 1 OR (`tenant_id` = 1 AND `group_code` = 'PLATFORM' AND `deleted` = 0)
);

INSERT INTO `iam_role` (
  `id`, `tenant_id`, `role_group_id`, `role_code`, `role_name`, `role_type`,
  `data_scope_type`, `status`, `is_system`, `sort_no`, `remark`, `created_by`, `updated_by`
)
SELECT
  1, 1, 1, 'platform_super_admin', '平台超级管理员', 'PLATFORM',
  'ALL', 'ENABLED', 1, 1, '默认平台管理员角色', 0, 0
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM `iam_role` WHERE `id` = 1 OR (`tenant_id` = 1 AND `role_code` = 'platform_super_admin' AND `deleted` = 0)
);

INSERT INTO `iam_user_role` (
  `id`, `tenant_id`, `user_id`, `role_id`, `source_type`, `remark`, `created_by`, `updated_by`
)
SELECT
  1, 1, 1, 1, 'MANUAL', '默认平台管理员授权', 0, 0
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM `iam_user_role` WHERE `id` = 1 OR (`tenant_id` = 1 AND `user_id` = 1 AND `role_id` = 1 AND `deleted` = 0)
);

INSERT INTO `iam_api_resource` (
  `id`, `tenant_id`, `resource_code`, `resource_name`, `http_method`, `url_pattern`,
  `auth_required`, `status`, `remark`, `created_by`, `updated_by`
)
VALUES
  (1, 1, 'dashboard:metrics:query', '平台概览查询', 'GET', '/api/dashboard/overview', 1, 'ENABLED', '默认平台权限资源', 0, 0),
  (2, 1, 'tenant:query', '租户查询', 'GET', '/api/tenants/**', 1, 'ENABLED', '默认平台权限资源', 0, 0),
  (3, 1, 'tenant:write', '租户写入', 'POST', '/api/tenants/**', 1, 'ENABLED', '默认平台权限资源', 0, 0),
  (4, 1, 'iam:overview:query', 'IAM 总览查询', 'GET', '/api/iam/overview', 1, 'ENABLED', '默认平台权限资源', 0, 0),
  (5, 1, 'iam:user:query', '用户查询', 'GET', '/api/iam/users/**', 1, 'ENABLED', '默认平台权限资源', 0, 0),
  (6, 1, 'iam:user:write', '用户写入', 'POST', '/api/iam/users/**', 1, 'ENABLED', '默认平台权限资源', 0, 0),
  (7, 1, 'iam:role:query', '角色查询', 'GET', '/api/iam/roles/**', 1, 'ENABLED', '默认平台权限资源', 0, 0),
  (8, 1, 'iam:role:write', '角色写入', 'POST', '/api/iam/roles/**', 1, 'ENABLED', '默认平台权限资源', 0, 0),
  (9, 1, 'iam:api-resource:query', 'API 资源查询', 'GET', '/api/iam/api-resources/**', 1, 'ENABLED', '默认平台权限资源', 0, 0),
  (10, 1, 'iam:api-resource:write', 'API 资源写入', 'POST', '/api/iam/api-resources/**', 1, 'ENABLED', '默认平台权限资源', 0, 0),
  (11, 1, 'iam:user-role:query', '用户角色查询', 'GET', '/api/iam/users/*/roles', 1, 'ENABLED', '默认平台权限资源', 0, 0),
  (12, 1, 'iam:user-role:write', '用户角色写入', 'PUT', '/api/iam/users/*/roles', 1, 'ENABLED', '默认平台权限资源', 0, 0),
  (13, 1, 'iam:role-api:query', '角色接口权限查询', 'GET', '/api/iam/roles/*/api-resources', 1, 'ENABLED', '默认平台权限资源', 0, 0),
  (14, 1, 'iam:role-api:write', '角色接口权限写入', 'PUT', '/api/iam/roles/*/api-resources', 1, 'ENABLED', '默认平台权限资源', 0, 0),
  (15, 1, 'system:config:query', '系统配置查询', 'GET', '/api/system/configs/**', 1, 'ENABLED', '默认平台权限资源', 0, 0),
  (16, 1, 'system:config:write', '系统配置写入', 'POST', '/api/system/configs/**', 1, 'ENABLED', '默认平台权限资源', 0, 0),
  (17, 1, 'integration:datasource:query', '数据源查询', 'GET', '/api/integrations/datasources/**', 1, 'ENABLED', '默认平台权限资源', 0, 0),
  (18, 1, 'integration:datasource:write', '数据源写入', 'POST', '/api/integrations/datasources/**', 1, 'ENABLED', '默认平台权限资源', 0, 0),
  (19, 1, 'file:object:query', '文件查询', 'GET', '/api/files/**', 1, 'ENABLED', '默认平台权限资源', 0, 0),
  (20, 1, 'file:storage:write', '文件存储写入', 'POST', '/api/files/storages/**', 1, 'ENABLED', '默认平台权限资源', 0, 0),
  (21, 1, 'scheduler:job:query', '调度任务查询', 'GET', '/api/scheduler/jobs/**', 1, 'ENABLED', '默认平台权限资源', 0, 0),
  (22, 1, 'scheduler:job:write', '调度任务写入', 'POST', '/api/scheduler/jobs/**', 1, 'ENABLED', '默认平台权限资源', 0, 0),
  (23, 1, 'codegen:project:query', '代码生成项目查询', 'GET', '/api/codegen/projects/**', 1, 'ENABLED', '默认平台权限资源', 0, 0),
  (24, 1, 'codegen:project:write', '代码生成项目写入', 'POST', '/api/codegen/projects/**', 1, 'ENABLED', '默认平台权限资源', 0, 0),
  (25, 1, 'audit:operation:query', '审计日志查询', 'GET', '/api/audit/operations/**', 1, 'ENABLED', '默认平台权限资源', 0, 0)
ON DUPLICATE KEY UPDATE
  `resource_name` = VALUES(`resource_name`),
  `http_method` = VALUES(`http_method`),
  `url_pattern` = VALUES(`url_pattern`),
  `status` = VALUES(`status`),
  `updated_by` = VALUES(`updated_by`);

INSERT INTO `iam_role_api` (
  `id`, `tenant_id`, `role_id`, `api_resource_id`, `remark`, `created_by`, `updated_by`
)
SELECT
  ar.id, 1, 1, ar.id, '平台超级管理员默认授权', 0, 0
FROM `iam_api_resource` ar
WHERE ar.tenant_id = 1
  AND ar.deleted = 0
  AND NOT EXISTS (
    SELECT 1
    FROM `iam_role_api` ra
    WHERE ra.tenant_id = 1
      AND ra.role_id = 1
      AND ra.api_resource_id = ar.id
      AND ra.deleted = 0
  );
