INSERT INTO `iam_menu` (
  `id`, `tenant_id`, `parent_id`, `menu_type`, `menu_code`, `menu_name`, `route_path`,
  `component_path`, `permission_code`, `icon`, `visible`, `keep_alive`, `sort_no`,
  `status`, `meta_json`, `remark`, `created_by`, `updated_by`
)
VALUES
  (1, 1, 0, 'MENU', 'dashboard', '平台总览', '/dashboard', '@/views/dashboard/DashboardView.vue', 'dashboard:metrics:query', 'Grid', 1, 1, 1, 'ENABLED', JSON_OBJECT('affix', true), '控制台首页', 0, 0),
  (2, 1, 0, 'MENU', 'tenant', '租户中心', '/tenant', '@/views/tenant/TenantView.vue', 'tenant:query', 'OfficeBuilding', 1, 1, 10, 'ENABLED', JSON_OBJECT('domain', 'tenant'), '租户治理入口', 0, 0),
  (3, 1, 0, 'MENU', 'iam', '身份与权限中心', '/iam', '@/views/iam/IamView.vue', 'iam:user:query', 'UserFilled', 1, 1, 20, 'ENABLED', JSON_OBJECT('domain', 'iam'), 'IAM 统一控制面', 0, 0),
  (4, 1, 3, 'MENU', 'iam_menu_center', '菜单中心', '/iam?tab=menus', '@/views/iam/IamView.vue', 'iam:menu:query', 'Menu', 1, 0, 21, 'ENABLED', JSON_OBJECT('tab', 'menus'), '菜单治理入口', 0, 0),
  (5, 1, 3, 'MENU', 'iam_data_scope_center', '数据权限中心', '/iam?tab=dataScopes', '@/views/iam/IamView.vue', 'iam:data-scope:query', 'Lock', 1, 0, 22, 'ENABLED', JSON_OBJECT('tab', 'dataScopes'), '数据权限治理入口', 0, 0),
  (6, 1, 3, 'MENU', 'iam_login_policy_center', '登录策略中心', '/iam?tab=loginPolicies', '@/views/iam/IamView.vue', 'iam:login-policy:query', 'Key', 1, 0, 23, 'ENABLED', JSON_OBJECT('tab', 'loginPolicies'), '登录策略治理入口', 0, 0),
  (7, 1, 3, 'MENU', 'iam_password_policy_center', '密码策略中心', '/iam?tab=passwordPolicies', '@/views/iam/IamView.vue', 'iam:password-policy:query', 'Lock', 1, 0, 24, 'ENABLED', JSON_OBJECT('tab', 'passwordPolicies'), '密码策略治理入口', 0, 0)
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

INSERT INTO `iam_data_scope` (
  `id`, `tenant_id`, `scope_code`, `scope_name`, `scope_type`, `scope_rule_json`,
  `status`, `remark`, `created_by`, `updated_by`
)
VALUES
  (1, 1, 'ALL_ACCESS', '全量数据范围', 'ALL', JSON_OBJECT('mode', 'ALL'), 'ENABLED', '平台默认全量数据范围', 0, 0),
  (2, 1, 'ORG_TREE', '组织树数据范围', 'DEPARTMENT_AND_CHILDREN', JSON_OBJECT('rootDeptIds', JSON_ARRAY(1), 'inheritChildren', true), 'ENABLED', '按组织树下钻的默认范围', 0, 0),
  (3, 1, 'SELF_ONLY', '仅本人数据范围', 'SELF', JSON_OBJECT('ownerField', 'created_by'), 'ENABLED', '最小权限默认范围', 0, 0)
AS new
ON DUPLICATE KEY UPDATE
  `scope_name` = new.`scope_name`,
  `scope_type` = new.`scope_type`,
  `scope_rule_json` = new.`scope_rule_json`,
  `status` = new.`status`,
  `remark` = new.`remark`,
  `updated_by` = new.`updated_by`;

INSERT INTO `iam_api_resource` (
  `id`, `tenant_id`, `resource_code`, `resource_name`, `http_method`, `url_pattern`,
  `auth_required`, `status`, `remark`, `created_by`, `updated_by`
)
VALUES
  (26, 1, 'iam:menu:query', '菜单查询', 'GET', '/api/iam/menus/**', 1, 'ENABLED', '菜单治理权限', 0, 0),
  (27, 1, 'iam:menu:write', '菜单写入', 'POST', '/api/iam/menus/**', 1, 'ENABLED', '菜单治理权限', 0, 0),
  (28, 1, 'iam:data-scope:query', '数据权限查询', 'GET', '/api/iam/data-scopes/**', 1, 'ENABLED', '数据权限治理权限', 0, 0),
  (29, 1, 'iam:data-scope:write', '数据权限写入', 'POST', '/api/iam/data-scopes/**', 1, 'ENABLED', '数据权限治理权限', 0, 0),
  (30, 1, 'iam:login-policy:query', '登录策略查询', 'GET', '/api/iam/login-policies/**', 1, 'ENABLED', '登录策略治理权限', 0, 0),
  (31, 1, 'iam:login-policy:write', '登录策略写入', 'POST', '/api/iam/login-policies/**', 1, 'ENABLED', '登录策略治理权限', 0, 0),
  (32, 1, 'iam:password-policy:query', '密码策略查询', 'GET', '/api/iam/password-policies/**', 1, 'ENABLED', '密码策略治理权限', 0, 0),
  (33, 1, 'iam:password-policy:write', '密码策略写入', 'POST', '/api/iam/password-policies/**', 1, 'ENABLED', '密码策略治理权限', 0, 0)
AS new
ON DUPLICATE KEY UPDATE
  `resource_name` = new.`resource_name`,
  `http_method` = new.`http_method`,
  `url_pattern` = new.`url_pattern`,
  `auth_required` = new.`auth_required`,
  `status` = new.`status`,
  `remark` = new.`remark`,
  `updated_by` = new.`updated_by`;

INSERT INTO `iam_role_api` (
  `tenant_id`, `role_id`, `api_resource_id`, `remark`, `created_by`, `updated_by`
)
SELECT 1, 1, ar.id, '平台超级管理员新增治理权限', 0, 0
FROM `iam_api_resource` ar
WHERE ar.tenant_id = 1
  AND ar.resource_code IN (
    'iam:menu:query',
    'iam:menu:write',
    'iam:data-scope:query',
    'iam:data-scope:write',
    'iam:login-policy:query',
    'iam:login-policy:write',
    'iam:password-policy:query',
    'iam:password-policy:write'
  )
  AND NOT EXISTS (
    SELECT 1 FROM `iam_role_api` ra
    WHERE ra.tenant_id = 1
      AND ra.role_id = 1
      AND ra.api_resource_id = ar.id
      AND ra.deleted = 0
  );
