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
  SELECT 'iam_department' AS parent_code, 'iam_department:create' AS action_code, '新增部门' AS action_name, 'Plus' AS icon, 10 AS sort_no
  UNION ALL SELECT 'iam_department', 'iam_department:edit', '编辑部门', 'Edit', 20
  UNION ALL SELECT 'iam_department', 'iam_department:toggle', '启停部门', 'Switch', 30
  UNION ALL SELECT 'iam_position', 'iam_position:create', '新增岗位', 'Plus', 10
  UNION ALL SELECT 'iam_position', 'iam_position:edit', '编辑岗位', 'Edit', 20
  UNION ALL SELECT 'iam_position', 'iam_position:toggle', '启停岗位', 'Switch', 30
  UNION ALL SELECT 'iam_employee', 'iam_employee:create', '新增员工', 'Plus', 10
  UNION ALL SELECT 'iam_employee', 'iam_employee:edit', '编辑员工', 'Edit', 20
  UNION ALL SELECT 'iam_employee', 'iam_employee:toggle', '启停员工', 'Switch', 30
  UNION ALL SELECT 'iam_user', 'iam_user:create', '新增用户', 'Plus', 10
  UNION ALL SELECT 'iam_user', 'iam_user:edit', '编辑用户', 'Edit', 20
  UNION ALL SELECT 'iam_user', 'iam_user:toggle', '启停用户', 'Switch', 30
  UNION ALL SELECT 'iam_role', 'iam_role:create', '新增角色', 'Plus', 10
  UNION ALL SELECT 'iam_role', 'iam_role:edit', '编辑角色', 'Edit', 20
  UNION ALL SELECT 'iam_role', 'iam_role:toggle', '启停角色', 'Switch', 30
  UNION ALL SELECT 'iam_api_resource', 'iam_api_resource:create', '新增接口权限', 'Plus', 10
  UNION ALL SELECT 'iam_api_resource', 'iam_api_resource:edit', '编辑接口权限', 'Edit', 20
  UNION ALL SELECT 'iam_api_resource', 'iam_api_resource:toggle', '启停接口权限', 'Switch', 30
  UNION ALL SELECT 'iam_user_role', 'iam_user_role:assign', '分配角色', 'Connection', 10
  UNION ALL SELECT 'iam_user_role', 'iam_user_role:save', '保存用户角色', 'Check', 20
  UNION ALL SELECT 'iam_role_api', 'iam_role_api:assign', '分配接口权限', 'Connection', 10
  UNION ALL SELECT 'iam_role_api', 'iam_role_api:save', '保存角色权限', 'Check', 20
  UNION ALL SELECT 'iam_menu_center', 'iam_menu:create', '新增菜单资源', 'Plus', 10
  UNION ALL SELECT 'iam_menu_center', 'iam_menu:edit', '编辑菜单资源', 'Edit', 20
  UNION ALL SELECT 'iam_menu_center', 'iam_menu:toggle', '启停菜单资源', 'Switch', 30
  UNION ALL SELECT 'iam_menu_center', 'iam_menu_permission:create', '新增例外授权', 'Plus', 40
  UNION ALL SELECT 'iam_menu_center', 'iam_menu_permission:edit', '编辑例外授权', 'Edit', 50
  UNION ALL SELECT 'iam_menu_center', 'iam_menu_permission:toggle', '启停例外授权', 'Switch', 60
  UNION ALL SELECT 'iam_menu_center', 'iam_menu_permission:save', '保存角色授权', 'Check', 70
  UNION ALL SELECT 'iam_data_scope_center', 'iam_data_scope_rule:create', '新增数据规则', 'Plus', 10
  UNION ALL SELECT 'iam_data_scope_center', 'iam_data_scope_rule:edit', '编辑数据规则', 'Edit', 20
  UNION ALL SELECT 'iam_data_scope_center', 'iam_data_scope_rule:toggle', '启停数据规则', 'Switch', 30
  UNION ALL SELECT 'iam_data_scope_center', 'iam_data_scope_rule:save', '保存数据规则', 'Check', 40
  UNION ALL SELECT 'iam_data_scope_center', 'iam_data_scope_template:create', '新增范围模板', 'Plus', 50
  UNION ALL SELECT 'iam_data_scope_center', 'iam_data_scope_template:edit', '编辑范围模板', 'Edit', 60
  UNION ALL SELECT 'iam_data_scope_center', 'iam_data_scope_template:toggle', '启停范围模板', 'Switch', 70
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
