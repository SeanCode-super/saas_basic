-- ------------------------------------------------------------------------------
-- V38: Ensure iam_menu_permission grants for workflow center menu
-- ------------------------------------------------------------------------------

INSERT INTO iam_menu_permission (
  tenant_id, menu_id, menu_code, subject_type, subject_value, button_codes_json, status,
  created_by, created_at, updated_by, updated_at, deleted, version, remark
)
SELECT DISTINCT
  m.tenant_id,
  m.id,
  m.menu_code,
  'ROLE',
  CAST(r.id AS CHAR),
  JSON_ARRAY(),
  'ENABLED',
  1,
  NOW(3),
  1,
  NOW(3),
  0,
  0,
  'workflow super admin menu grant'
FROM iam_menu m
INNER JOIN iam_role r
  ON r.tenant_id = m.tenant_id
 AND r.deleted = 0
 AND r.status = 'ENABLED'
WHERE m.menu_code = 'workflow'
  AND m.deleted = 0
  AND m.status = 'ENABLED'
  AND (
    r.role_code = 'platform_super_admin'
    OR r.role_code LIKE '%_super_admin'
    OR r.data_scope_type = 'ALL'
  )
ON DUPLICATE KEY UPDATE
  updated_at = NOW(3),
  deleted = 0,
  status = 'ENABLED';
