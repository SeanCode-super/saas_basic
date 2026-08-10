INSERT INTO `iam_api_resource` (
  `tenant_id`, `resource_code`, `resource_name`, `http_method`, `url_pattern`,
  `auth_required`, `status`, `remark`, `created_by`, `updated_by`
)
SELECT 1, resource_code, resource_name, http_method, url_pattern, 1, 'ENABLED', 'User-Person binding governance', 0, 0
FROM (
  SELECT 'iam:user-person-binding:query' AS resource_code, 'User-Person binding query' AS resource_name,
         'GET' AS http_method, '/api/iam/user-person-bindings/**' AS url_pattern
  UNION ALL SELECT 'iam:user-person-binding:write', 'User-Person binding write',
         'POST' AS http_method, '/api/iam/user-person-bindings' AS url_pattern
  UNION ALL SELECT 'iam:user-person-binding:update', 'User-Person binding update',
         'PUT' AS http_method, '/api/iam/user-person-bindings/*' AS url_pattern
  UNION ALL SELECT 'iam:user-person-binding:lifecycle', 'User-Person binding lifecycle', 'PATCH', '/api/iam/user-person-bindings/*/lifecycle'
  UNION ALL SELECT 'iam:user-person-binding:delete', 'User-Person binding delete', 'DELETE', '/api/iam/user-person-bindings/*'
) resources
WHERE NOT EXISTS (
  SELECT 1 FROM `iam_api_resource` existing
  WHERE existing.tenant_id = 1 AND existing.resource_code = resources.resource_code AND existing.deleted = 0
);

INSERT INTO `iam_role_api` (`tenant_id`, `role_id`, `api_resource_id`, `remark`, `created_by`, `updated_by`)
SELECT 1, 1, resource.id, 'User-Person binding governance', 0, 0
FROM `iam_api_resource` resource
WHERE resource.tenant_id = 1
  AND resource.resource_code IN (
    'iam:user-person-binding:query',
    'iam:user-person-binding:write',
    'iam:user-person-binding:update',
    'iam:user-person-binding:lifecycle',
    'iam:user-person-binding:delete'
  )
  AND NOT EXISTS (
    SELECT 1 FROM `iam_role_api` existing
    WHERE existing.tenant_id = 1 AND existing.role_id = 1
      AND existing.api_resource_id = resource.id AND existing.deleted = 0
  );
