DELETE permission_record
FROM iam_menu_permission permission_record
INNER JOIN iam_menu parent_menu
  ON parent_menu.id = permission_record.menu_id
 AND parent_menu.tenant_id = permission_record.tenant_id
WHERE parent_menu.deleted = 0
  AND parent_menu.menu_type = 'MENU'
  AND EXISTS (
    SELECT 1
    FROM iam_menu child_menu
    WHERE child_menu.tenant_id = parent_menu.tenant_id
      AND child_menu.parent_id = parent_menu.id
      AND child_menu.menu_type IN ('DIRECTORY', 'MENU', 'LINK')
      AND child_menu.deleted = 0
  );

UPDATE iam_menu parent_menu
INNER JOIN (
  SELECT DISTINCT candidate.id
  FROM iam_menu candidate
  INNER JOIN iam_menu child_menu
    ON child_menu.tenant_id = candidate.tenant_id
   AND child_menu.parent_id = candidate.id
   AND child_menu.menu_type IN ('DIRECTORY', 'MENU', 'LINK')
   AND child_menu.deleted = 0
  WHERE candidate.deleted = 0
    AND candidate.menu_type = 'MENU'
) directory_candidate ON directory_candidate.id = parent_menu.id
SET parent_menu.menu_type = 'DIRECTORY',
    parent_menu.route_path = NULL,
    parent_menu.component_path = NULL,
    parent_menu.permission_code = NULL,
    parent_menu.keep_alive = 0,
    parent_menu.meta_json = JSON_SET(COALESCE(parent_menu.meta_json, JSON_OBJECT()), '$.resourceType', 'DIRECTORY'),
    parent_menu.remark = '标准导航目录',
    parent_menu.updated_at = CURRENT_TIMESTAMP(3)
WHERE parent_menu.deleted = 0
  AND parent_menu.menu_type = 'MENU';
