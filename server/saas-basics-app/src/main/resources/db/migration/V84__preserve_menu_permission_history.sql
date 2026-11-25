-- The archive keeps the complete, unmodified source row. Restoring a historical row must never grant access.
CREATE TABLE IF NOT EXISTS iam_menu_permission_v83_archive LIKE iam_menu_permission;

SET @v84_has_active_key = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'iam_menu_permission'
      AND index_name = 'uk_iam_menu_permission_subject' AND expression IS NOT NULL
);
SET @v84_sql = IF(@v84_has_active_key > 0, 'SELECT 1',
    'ALTER TABLE iam_menu_permission DROP INDEX uk_iam_menu_permission_subject, ADD UNIQUE KEY uk_iam_menu_permission_subject (tenant_id, menu_id, subject_type, subject_value, (CASE WHEN deleted = 0 THEN 0 ELSE NULL END))');
PREPARE v84_statement FROM @v84_sql;
EXECUTE v84_statement;
DEALLOCATE PREPARE v84_statement;

-- V1 and V44 used different unique keys for the same atomic role/menu grant.
SET @v84_role_indexes = (
    SELECT GROUP_CONCAT(CONCAT('DROP INDEX ', index_name) ORDER BY index_name SEPARATOR ', ')
    FROM (SELECT DISTINCT index_name FROM information_schema.statistics
          WHERE table_schema = DATABASE() AND table_name = 'iam_role_menu'
            AND index_name IN ('uk_role_menu', 'uk_role_menu_unique')) existing_keys
);
SET @v84_sql = IF(EXISTS (
    SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE()
      AND table_name = 'iam_role_menu' AND index_name = 'uk_role_menu_active' AND expression IS NOT NULL
), 'SELECT 1', CONCAT('ALTER TABLE iam_role_menu ',
    IFNULL(CONCAT(@v84_role_indexes, ', '), ''),
    'ADD UNIQUE KEY uk_role_menu_active (tenant_id, role_id, menu_id, (CASE WHEN deleted = 0 THEN 0 ELSE NULL END))'));
PREPARE v84_statement FROM @v84_sql;
EXECUTE v84_statement;
DEALLOCATE PREPARE v84_statement;

-- Restore archived rows as historical grants. Also used by the offline recovery package.
INSERT INTO iam_menu_permission (
    id, tenant_id, menu_id, menu_code, subject_type, subject_value, button_codes_json, status,
    created_by, created_at, updated_by, updated_at, deleted, deleted_at, version, remark
)
SELECT a.id, a.tenant_id, a.menu_id, a.menu_code, a.subject_type, a.subject_value, a.button_codes_json, a.status,
       a.created_by, a.created_at, IF(a.deleted = 0, 0, a.updated_by),
       IF(a.deleted = 0, h.installed_on, a.updated_at), 1,
       IF(a.deleted = 0, h.installed_on, a.deleted_at),
       IF(a.deleted = 0, a.version + 1, a.version), a.remark
FROM iam_menu_permission_v83_archive a
JOIN flyway_schema_history h ON h.version = '83' AND h.success = 1
WHERE NOT EXISTS (
    SELECT 1 FROM iam_menu_permission p
    WHERE p.id = a.id AND p.tenant_id = a.tenant_id AND p.menu_id = a.menu_id
      AND p.menu_code = a.menu_code AND p.subject_type = a.subject_type AND p.subject_value = a.subject_value
      AND p.button_codes_json <=> a.button_codes_json AND p.status = a.status AND p.remark <=> a.remark
      AND p.created_by <=> a.created_by AND p.created_at = a.created_at AND p.deleted = 1
      AND p.updated_by <=> IF(a.deleted = 0, 0, a.updated_by)
      AND p.updated_at = IF(a.deleted = 0, h.installed_on, a.updated_at)
      AND p.deleted_at <=> IF(a.deleted = 0, h.installed_on, a.deleted_at)
      AND p.version = IF(a.deleted = 0, a.version + 1, a.version)
);
