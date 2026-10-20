-- ------------------------------------------------------------------------------
-- V36: Grant workflow permissions to super admin roles across all active tenants
-- ------------------------------------------------------------------------------

-- 1. Ensure API resources exist for all active tenants
INSERT INTO `iam_api_resource` (
  `tenant_id`, `resource_code`, `resource_name`, `http_method`, `url_pattern`,
  `status`, `created_by`, `updated_by`
)
SELECT
  tenant.id,
  res.resource_code,
  res.resource_name,
  res.http_method,
  res.url_pattern,
  'ENABLED',
  0,
  0
FROM `plat_tenant` tenant
CROSS JOIN (
  SELECT 'workflow:definition:query' AS resource_code, '查询流程定义' AS resource_name, 'GET' AS http_method, '/api/workflow/definitions/**' AS url_pattern
  UNION ALL SELECT 'workflow:definition:write', '设计与维护流程定义', 'POST', '/api/workflow/definitions/**'
  UNION ALL SELECT 'workflow:instance:query', '查询审批单据', 'GET', '/api/workflow/instances/**'
  UNION ALL SELECT 'workflow:instance:write', '发起与撤回审批单', 'POST', '/api/workflow/instances/**'
  UNION ALL SELECT 'workflow:task:query', '查询审批待办与经办', 'GET', '/api/workflow/tasks/**'
  UNION ALL SELECT 'workflow:task:write', '办理审批任务(同意/驳回)', 'POST', '/api/workflow/tasks/**'
) res
WHERE tenant.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM `iam_api_resource` existing
    WHERE existing.tenant_id = tenant.id
      AND existing.resource_code = res.resource_code
      AND existing.deleted = 0
  );

-- 2. Grant workflow permissions to platform_super_admin and tenant_super_admin roles
INSERT INTO `iam_role_api` (`tenant_id`, `role_id`, `api_resource_id`, `remark`, `created_by`, `updated_by`)
SELECT role.tenant_id, role.id, resource.id, 'Workflow engine administration grant', 0, 0
FROM `iam_role` role
INNER JOIN `plat_tenant` tenant
  ON tenant.id = role.tenant_id
 AND tenant.deleted = 0
INNER JOIN `iam_api_resource` resource
  ON resource.tenant_id = role.tenant_id
 AND resource.deleted = 0
 AND resource.resource_code LIKE 'workflow:%'
WHERE role.deleted = 0
  AND role.status = 'ENABLED'
  AND (
    role.role_code = 'platform_super_admin'
    OR role.role_code = CONCAT(tenant.tenant_code, '_super_admin')
    OR role.data_scope_type = 'ALL'
  )
  AND NOT EXISTS (
    SELECT 1 FROM `iam_role_api` existing
    WHERE existing.tenant_id = role.tenant_id
      AND existing.role_id = role.id
      AND existing.api_resource_id = resource.id
      AND existing.deleted = 0
  );

-- 3. Also grant workflow button permissions to menus
INSERT INTO `iam_menu` (
  `tenant_id`, `parent_id`, `menu_type`, `menu_code`, `menu_name`,
  `route_path`, `component_path`, `permission_code`, `icon`, `visible`,
  `keep_alive`, `sort_no`, `status`, `meta_json`, `remark`, `created_by`, `updated_by`
)
SELECT
  tenant.id,
  0,
  'MENU',
  'workflow',
  '工作流中心',
  '/workflow',
  '@/views/workflow/WorkflowView.vue',
  'workflow:definition:query',
  'Tickets',
  1,
  1,
  35,
  'ENABLED',
  JSON_OBJECT('domain', 'workflow'),
  '企业级可视化工作流与审批中心',
  0,
  0
FROM `plat_tenant` tenant
WHERE tenant.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM `iam_menu` m
    WHERE m.tenant_id = tenant.id
      AND m.menu_code = 'workflow'
      AND m.deleted = 0
  );
