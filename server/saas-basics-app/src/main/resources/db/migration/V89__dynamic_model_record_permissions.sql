INSERT INTO iam_api_resource (tenant_id, resource_code, resource_name, http_method, url_pattern, created_by, updated_by)
SELECT m.tenant_id, CONCAT('dynamic:', m.model_code, ':', a.action), LEFT(CONCAT(m.model_name, ' · ', a.label), 128),
       a.method, CONCAT('/api/dynamic/', m.model_code, a.suffix), m.created_by, m.updated_by
FROM gen_dynamic_model m
CROSS JOIN (
    SELECT 'create' AS action, '新增' AS label, 'POST' AS method, '' AS suffix
    UNION ALL SELECT 'update', '编辑', 'PUT', '/*'
    UNION ALL SELECT 'delete', '删除', 'DELETE', '/*'
) a
WHERE m.deleted = 0 AND m.status = 'PUBLISHED'
  AND NOT EXISTS (SELECT 1 FROM iam_api_resource r WHERE r.tenant_id = m.tenant_id AND r.deleted = 0
                  AND r.resource_code = CONCAT('dynamic:', m.model_code, ':', a.action) COLLATE utf8mb4_0900_ai_ci);
