UPDATE `iam_menu`
SET
  `route_path` = '/iam/foundation',
  `meta_json` = JSON_OBJECT('section', 'foundation'),
  `remark` = 'IAM 总览控制面'
WHERE `id` = 100;

UPDATE `iam_menu`
SET
  `route_path` = '/iam/organization',
  `meta_json` = JSON_OBJECT('section', 'organization'),
  `remark` = '组织主数据控制面'
WHERE `id` = 101;

UPDATE `iam_menu`
SET
  `route_path` = '/iam/accounts',
  `meta_json` = JSON_OBJECT('section', 'accounts'),
  `remark` = '用户与员工控制面'
WHERE `id` = 102;

UPDATE `iam_menu`
SET
  `route_path` = '/iam/permissions',
  `meta_json` = JSON_OBJECT('section', 'permissions'),
  `remark` = '角色与权限资源控制面'
WHERE `id` = 103;

UPDATE `iam_menu`
SET
  `route_path` = '/iam/assignment',
  `meta_json` = JSON_OBJECT('section', 'assignment'),
  `remark` = '授权编排控制面'
WHERE `id` = 104;

UPDATE `iam_menu`
SET
  `route_path` = '/iam/menu',
  `meta_json` = JSON_OBJECT('section', 'menu'),
  `remark` = '菜单治理入口'
WHERE `id` = 4;

UPDATE `iam_menu`
SET
  `route_path` = '/iam/data-scope',
  `meta_json` = JSON_OBJECT('section', 'data-scope'),
  `remark` = '数据权限治理入口'
WHERE `id` = 5;

UPDATE `iam_menu`
SET
  `route_path` = '/iam/login-policy',
  `meta_json` = JSON_OBJECT('section', 'login-policy'),
  `remark` = '登录策略治理入口'
WHERE `id` = 6;

UPDATE `iam_menu`
SET
  `route_path` = '/iam/password-policy',
  `meta_json` = JSON_OBJECT('section', 'password-policy'),
  `remark` = '密码策略治理入口'
WHERE `id` = 7;
