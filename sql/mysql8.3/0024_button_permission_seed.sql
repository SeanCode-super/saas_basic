UPDATE iam_menu_permission
SET button_codes_json = CASE menu_code
  WHEN 'iam_department' THEN JSON_ARRAY('iam_department:create', 'iam_department:edit', 'iam_department:toggle')
  WHEN 'iam_position' THEN JSON_ARRAY('iam_position:create', 'iam_position:edit', 'iam_position:toggle')
  WHEN 'iam_employee' THEN JSON_ARRAY('iam_employee:create', 'iam_employee:edit', 'iam_employee:toggle')
  WHEN 'iam_user' THEN JSON_ARRAY('iam_user:create', 'iam_user:edit', 'iam_user:toggle')
  WHEN 'iam_role' THEN JSON_ARRAY('iam_role:create', 'iam_role:edit', 'iam_role:toggle')
  WHEN 'iam_api_resource' THEN JSON_ARRAY('iam_api_resource:create', 'iam_api_resource:edit', 'iam_api_resource:toggle')
  WHEN 'iam_user_role' THEN JSON_ARRAY('iam_user_role:assign', 'iam_user_role:save')
  WHEN 'iam_role_api' THEN JSON_ARRAY('iam_role_api:assign', 'iam_role_api:save')
  WHEN 'iam_menu_center' THEN JSON_ARRAY(
    'iam_menu:create', 'iam_menu:edit', 'iam_menu:toggle',
    'iam_menu_permission:create', 'iam_menu_permission:edit', 'iam_menu_permission:toggle', 'iam_menu_permission:save'
  )
  WHEN 'iam_data_scope_center' THEN JSON_ARRAY(
    'iam_data_scope_rule:create', 'iam_data_scope_rule:edit', 'iam_data_scope_rule:toggle', 'iam_data_scope_rule:save',
    'iam_data_scope_template:create', 'iam_data_scope_template:edit', 'iam_data_scope_template:toggle'
  )
  ELSE button_codes_json
END,
updated_by = 1,
updated_at = NOW(3),
remark = 'button permission seed'
WHERE deleted = 0
  AND status = 'ENABLED'
  AND menu_code IN (
    'iam_department',
    'iam_position',
    'iam_employee',
    'iam_user',
    'iam_role',
    'iam_api_resource',
    'iam_user_role',
    'iam_role_api',
    'iam_menu_center',
    'iam_data_scope_center'
  )
  AND (button_codes_json IS NULL OR JSON_LENGTH(button_codes_json) = 0);
