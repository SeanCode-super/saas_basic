-- V41__seed_policy_and_tenant_buttons.sql
-- Seed granular BUTTON action resources for Tenant, Login Policy, and Password Policy

INSERT INTO iam_menu (
  tenant_id, parent_id, menu_type, menu_code, menu_name, route_path, component_path,
  permission_code, icon, visible, keep_alive, sort_no, status, meta_json, remark,
  created_by, updated_by
)
SELECT
  parent_menu.tenant_id,
  parent_menu.id,
  'BUTTON',
  action_catalog.action_code,
  action_catalog.action_name,
  NULL,
  NULL,
  NULL,
  action_catalog.icon,
  0,
  0,
  action_catalog.sort_no,
  'ENABLED',
  JSON_OBJECT('resourceType', 'ACTION'),
  '标准页面动作资源',
  0,
  0
FROM iam_menu parent_menu
INNER JOIN (
  -- Tenant Center Actions
  SELECT 'tenant' AS parent_code, 'tenant:create' AS action_code, '新增租户' AS action_name, 'Plus' AS icon, 10 AS sort_no
  UNION ALL SELECT 'tenant', 'tenant:edit', '编辑租户', 'Edit', 20
  UNION ALL SELECT 'tenant', 'tenant:toggle', '启停租户', 'Switch', 30
  UNION ALL SELECT 'tenant', 'tenant:import', '导入配置', 'Upload', 40

  -- Login Policy Center Actions
  UNION ALL SELECT 'iam_login_policy_center', 'iam_login_policy:create', '新增登录策略', 'Plus', 10
  UNION ALL SELECT 'iam_login_policy_center', 'iam_login_policy:edit', '编辑登录策略', 'Edit', 20
  UNION ALL SELECT 'iam_login_policy_center', 'iam_login_policy:toggle', '启停登录策略', 'Switch', 30

  -- Password Policy Center Actions
  UNION ALL SELECT 'iam_password_policy_center', 'iam_password_policy:create', '新增密码策略', 'Plus', 10
  UNION ALL SELECT 'iam_password_policy_center', 'iam_password_policy:edit', '编辑密码策略', 'Edit', 20
  UNION ALL SELECT 'iam_password_policy_center', 'iam_password_policy:toggle', '启停密码策略', 'Switch', 30
) action_catalog ON action_catalog.parent_code = parent_menu.menu_code
WHERE parent_menu.deleted = 0
  AND parent_menu.menu_type <> 'BUTTON'
  AND NOT EXISTS (
    SELECT 1
    FROM iam_menu existing_action
    WHERE existing_action.tenant_id = parent_menu.tenant_id
      AND existing_action.menu_code = action_catalog.action_code
      AND existing_action.deleted = 0
  );

-- Update Super Admin (role 1) default grant with the new button actions
UPDATE iam_menu_permission
SET button_codes_json = '["tenant:create", "tenant:edit", "tenant:toggle", "tenant:import"]'
WHERE subject_type = 'ROLE' AND subject_value = '1' AND menu_id = 2;

UPDATE iam_menu_permission
SET button_codes_json = '["iam_login_policy:create", "iam_login_policy:edit", "iam_login_policy:toggle"]'
WHERE subject_type = 'ROLE' AND subject_value = '1' AND menu_id = 6;

UPDATE iam_menu_permission
SET button_codes_json = '["iam_password_policy:create", "iam_password_policy:edit", "iam_password_policy:toggle"]'
WHERE subject_type = 'ROLE' AND subject_value = '1' AND menu_id = 7;
