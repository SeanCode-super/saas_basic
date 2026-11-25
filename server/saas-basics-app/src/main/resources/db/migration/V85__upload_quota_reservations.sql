ALTER TABLE file_upload_session
    ADD COLUMN request_key VARCHAR(160) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN quota_managed TINYINT NOT NULL DEFAULT 0,
    ADD COLUMN cleanup_after DATETIME(3) NULL,
    ADD COLUMN file_object_id BIGINT UNSIGNED NULL,
    ADD UNIQUE KEY uk_upload_request (tenant_id, request_key),
    ADD KEY idx_upload_capacity (tenant_id, status, deleted, expire_at),
    ADD KEY idx_upload_target (tenant_id, storage_id, object_key(191), status);

-- Existing tenants may predate automatic provisioning of package quotas.
INSERT INTO plat_tenant_quota
    (tenant_id, quota_code, quota_name, quota_value, alarm_threshold, reset_cycle)
SELECT t.id, q.code, q.name,
       CASE q.code WHEN 'USER_COUNT' THEN p.max_user_count WHEN 'DEPT_COUNT' THEN p.max_dept_count ELSE p.max_storage_mb END,
       FLOOR((CASE q.code WHEN 'USER_COUNT' THEN p.max_user_count WHEN 'DEPT_COUNT' THEN p.max_dept_count ELSE p.max_storage_mb END)
           * CASE q.code WHEN 'STORAGE_MB' THEN 0.85 ELSE 0.8 END), 'NONE'
FROM plat_tenant t
JOIN plat_tenant_package p ON p.id = t.package_id AND p.deleted = 0
CROSS JOIN (SELECT 'USER_COUNT' code, '账号席位上限' name UNION ALL
            SELECT 'DEPT_COUNT', '组织部门上限' UNION ALL SELECT 'STORAGE_MB', '文件存储空间(MB)') q
WHERE t.deleted = 0 AND NOT EXISTS (
    SELECT 1 FROM plat_tenant_quota existing
    WHERE existing.tenant_id = t.id AND existing.quota_code = CONVERT(q.code USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AND existing.deleted = 0
);

-- Legacy exports were stored without a file_object row. Avoid double charging new tracked exports.
CREATE VIEW plat_tenant_storage_sources (tenant_id, file_size) AS
    SELECT tenant_id, file_size FROM file_object WHERE deleted = 0
    UNION ALL
    SELECT e.tenant_id, MAX(e.file_size) AS file_size
    FROM sys_export_task e
    WHERE e.deleted = 0 AND e.status = 'COMPLETED' AND e.object_key IS NOT NULL
      AND NOT EXISTS (
          SELECT 1 FROM file_object f
          WHERE f.tenant_id = e.tenant_id AND CAST(f.object_key AS BINARY) = CAST(e.object_key AS BINARY) AND f.deleted = 0
      )
    GROUP BY e.tenant_id, CAST(e.object_key AS BINARY);
