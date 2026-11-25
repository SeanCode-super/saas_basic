-- V1 included the audit columns; databases initialized with the V44 table may lack them.
SELECT CONCAT_WS(', ',
    IF(COUNT(CASE WHEN column_name = 'created_by' THEN 1 END) = 0, 'ADD COLUMN created_by BIGINT UNSIGNED NOT NULL DEFAULT 0', NULL),
    IF(COUNT(CASE WHEN column_name = 'updated_by' THEN 1 END) = 0, 'ADD COLUMN updated_by BIGINT UNSIGNED NOT NULL DEFAULT 0', NULL),
    IF(COUNT(CASE WHEN column_name = 'updated_at' THEN 1 END) = 0, 'ADD COLUMN updated_at DATETIME(3) NULL', NULL),
    IF(COUNT(CASE WHEN column_name = 'deleted' THEN 1 END) = 0, 'ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0', NULL),
    IF(COUNT(CASE WHEN column_name = 'deleted_at' THEN 1 END) = 0, 'ADD COLUMN deleted_at DATETIME(3) DEFAULT NULL', NULL),
    IF(COUNT(CASE WHEN column_name = 'version' THEN 1 END) = 0, 'ADD COLUMN version INT UNSIGNED NOT NULL DEFAULT 0', NULL),
    IF(COUNT(CASE WHEN column_name = 'remark' THEN 1 END) = 0, 'ADD COLUMN remark VARCHAR(500) DEFAULT NULL', NULL)
) INTO @role_menu_additions
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'iam_role_menu';

SET @role_menu_ddl = IF(@role_menu_additions = '', 'SELECT 1', CONCAT('ALTER TABLE iam_role_menu ', @role_menu_additions));
PREPARE role_menu_stmt FROM @role_menu_ddl;
EXECUTE role_menu_stmt;
DEALLOCATE PREPARE role_menu_stmt;

-- Use the existing creation time as the baseline when there is no recorded update.
UPDATE iam_role_menu SET updated_at = created_at WHERE updated_at IS NULL;
SET @role_menu_ddl = IF(EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'iam_role_menu'
      AND column_name = 'updated_at' AND is_nullable = 'YES'
), 'ALTER TABLE iam_role_menu MODIFY COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)', 'SELECT 1');
PREPARE role_menu_stmt FROM @role_menu_ddl;
EXECUTE role_menu_stmt;
DEALLOCATE PREPARE role_menu_stmt;
