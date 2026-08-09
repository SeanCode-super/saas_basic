UPDATE iam_menu
SET visible = 0,
    remark = '兼容旧入口：组织聚合页'
WHERE id = 101;

UPDATE iam_menu
SET visible = 0,
    remark = '兼容旧入口：账号聚合页'
WHERE id = 102;

UPDATE iam_menu
SET visible = 0,
    remark = '兼容旧入口：权限聚合页'
WHERE id = 103;

UPDATE iam_menu
SET visible = 0,
    remark = '兼容旧入口：授权聚合页'
WHERE id = 104;

UPDATE iam_menu
SET sort_no = 27
WHERE id = 4;

UPDATE iam_menu
SET sort_no = 28
WHERE id = 5;

UPDATE iam_menu
SET sort_no = 29
WHERE id = 6;

UPDATE iam_menu
SET sort_no = 30
WHERE id = 7;

INSERT INTO iam_menu (
  id, tenant_id, parent_id, menu_type, menu_code, menu_name, route_path, component_path,
  permission_code, icon, visible, keep_alive, sort_no, status, meta_json, remark, created_by, updated_by
)
VALUES
  (110, 1, 3, 'MENU', 'iam_department', '部门管理', '/iam/department', '@/views/iam/IamDepartmentView.vue', 'iam:user:query', 'OfficeBuilding', 1, 0, 21, 'ENABLED', JSON_OBJECT('section', 'department'), '部门单一控制面', 0, 0),
  (111, 1, 3, 'MENU', 'iam_position', '岗位管理', '/iam/position', '@/views/iam/IamPositionView.vue', 'iam:user:query', 'Postcard', 1, 0, 22, 'ENABLED', JSON_OBJECT('section', 'position'), '岗位单一控制面', 0, 0),
  (112, 1, 3, 'MENU', 'iam_employee', '员工档案', '/iam/employee', '@/views/iam/IamEmployeeView.vue', 'iam:user:query', 'User', 1, 0, 23, 'ENABLED', JSON_OBJECT('section', 'employee'), '员工单一控制面', 0, 0),
  (113, 1, 3, 'MENU', 'iam_user', '用户管理', '/iam/user', '@/views/iam/IamUserView.vue', 'iam:user:query', 'Avatar', 1, 0, 24, 'ENABLED', JSON_OBJECT('section', 'user'), '用户单一控制面', 0, 0),
  (114, 1, 3, 'MENU', 'iam_role', '角色管理', '/iam/role', '@/views/iam/IamRoleView.vue', 'iam:role:query', 'UserFilled', 1, 0, 25, 'ENABLED', JSON_OBJECT('section', 'role'), '角色单一控制面', 0, 0),
  (115, 1, 3, 'MENU', 'iam_api_resource', '权限资源', '/iam/api-resource', '@/views/iam/IamApiResourceView.vue', 'iam:api-resource:query', 'Key', 1, 0, 26, 'ENABLED', JSON_OBJECT('section', 'api-resource'), '权限资源单一控制面', 0, 0),
  (116, 1, 3, 'MENU', 'iam_user_role', '用户角色', '/iam/user-role', '@/views/iam/IamUserRoleView.vue', 'iam:user-role:query', 'Connection', 1, 0, 31, 'ENABLED', JSON_OBJECT('section', 'user-role'), '用户角色单一控制面', 0, 0),
  (117, 1, 3, 'MENU', 'iam_role_api', '角色权限', '/iam/role-api', '@/views/iam/IamRoleApiView.vue', 'iam:role-api:query', 'Link', 1, 0, 32, 'ENABLED', JSON_OBJECT('section', 'role-api'), '角色权限单一控制面', 0, 0)
ON DUPLICATE KEY UPDATE
  tenant_id = VALUES(tenant_id),
  parent_id = VALUES(parent_id),
  menu_code = VALUES(menu_code),
  menu_name = VALUES(menu_name),
  route_path = VALUES(route_path),
  component_path = VALUES(component_path),
  permission_code = VALUES(permission_code),
  icon = VALUES(icon),
  visible = VALUES(visible),
  keep_alive = VALUES(keep_alive),
  sort_no = VALUES(sort_no),
  status = VALUES(status),
  meta_json = VALUES(meta_json),
  remark = VALUES(remark),
  updated_by = VALUES(updated_by);
