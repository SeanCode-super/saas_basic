CREATE TABLE IF NOT EXISTS iam_menu_permission_v83_archive LIKE iam_menu_permission;

INSERT INTO iam_menu_permission_v83_archive (
    id, tenant_id, menu_id, menu_code, subject_type, subject_value, button_codes_json, status,
    created_by, created_at, updated_by, updated_at, deleted, deleted_at, version, remark
)
SELECT p.id, p.tenant_id, p.menu_id, p.menu_code, p.subject_type, p.subject_value, p.button_codes_json, p.status,
       p.created_by, p.created_at, p.updated_by, p.updated_at, p.deleted, p.deleted_at, p.version, p.remark
FROM iam_menu_permission p
JOIN iam_menu m ON m.id = p.menu_id
WHERE (m.menu_type = 'DIRECTORY' OR (m.menu_code = 'tenant' AND m.menu_type <> 'DIRECTORY'))
  AND NOT EXISTS (SELECT 1 FROM iam_menu_permission_v83_archive a WHERE a.id = p.id);
