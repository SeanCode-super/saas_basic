SET @menu_deleted_at_sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'iam_menu_permission'
              AND column_name = 'deleted_at'
        ),
        'SELECT 1',
        'ALTER TABLE iam_menu_permission ADD COLUMN deleted_at DATETIME NULL COMMENT ''删除时间'' AFTER deleted'
    )
);
PREPARE menu_deleted_at_stmt FROM @menu_deleted_at_sql;
EXECUTE menu_deleted_at_stmt;
DEALLOCATE PREPARE menu_deleted_at_stmt;

SET @menu_version_sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'iam_menu_permission'
              AND column_name = 'version'
        ),
        'SELECT 1',
        'ALTER TABLE iam_menu_permission ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT ''版本号'' AFTER deleted_at'
    )
);
PREPARE menu_version_stmt FROM @menu_version_sql;
EXECUTE menu_version_stmt;
DEALLOCATE PREPARE menu_version_stmt;

SET @rule_deleted_at_sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'iam_data_permission_rule'
              AND column_name = 'deleted_at'
        ),
        'SELECT 1',
        'ALTER TABLE iam_data_permission_rule ADD COLUMN deleted_at DATETIME NULL COMMENT ''删除时间'' AFTER deleted'
    )
);
PREPARE rule_deleted_at_stmt FROM @rule_deleted_at_sql;
EXECUTE rule_deleted_at_stmt;
DEALLOCATE PREPARE rule_deleted_at_stmt;

SET @rule_version_sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'iam_data_permission_rule'
              AND column_name = 'version'
        ),
        'SELECT 1',
        'ALTER TABLE iam_data_permission_rule ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT ''版本号'' AFTER deleted_at'
    )
);
PREPARE rule_version_stmt FROM @rule_version_sql;
EXECUTE rule_version_stmt;
DEALLOCATE PREPARE rule_version_stmt;
