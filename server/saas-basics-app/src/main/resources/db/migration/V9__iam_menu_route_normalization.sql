UPDATE `iam_menu`
SET
  `route_path` = '/iam?tab=menu',
  `meta_json` = JSON_OBJECT('tab', 'menu'),
  `updated_by` = 0
WHERE `id` = 4 AND `deleted` = 0;

UPDATE `iam_menu`
SET
  `route_path` = '/iam?tab=data-scope',
  `meta_json` = JSON_OBJECT('tab', 'data-scope'),
  `updated_by` = 0
WHERE `id` = 5 AND `deleted` = 0;

UPDATE `iam_menu`
SET
  `route_path` = '/iam?tab=login-policy',
  `meta_json` = JSON_OBJECT('tab', 'login-policy'),
  `updated_by` = 0
WHERE `id` = 6 AND `deleted` = 0;

UPDATE `iam_menu`
SET
  `route_path` = '/iam?tab=password-policy',
  `meta_json` = JSON_OBJECT('tab', 'password-policy'),
  `updated_by` = 0
WHERE `id` = 7 AND `deleted` = 0;
