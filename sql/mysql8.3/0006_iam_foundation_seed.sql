INSERT INTO `iam_department` (
  `id`, `tenant_id`, `parent_id`, `dept_code`, `dept_name`, `dept_full_name`,
  `leader_user_id`, `status`, `sort_no`, `remark`, `created_by`, `updated_by`
)
VALUES
  (1, 1, 0, 'PLATFORM', '平台管理部', '平台管理部', 1, 'ENABLED', 1, '平台级治理部门', 0, 0),
  (2, 1, 1, 'PLATFORM_SECURITY', '平台安全部', '平台管理部/平台安全部', 1, 'ENABLED', 2, '审计与安全治理部门', 0, 0),
  (3, 1, 1, 'PLATFORM_OPS', '平台运维部', '平台管理部/平台运维部', 1, 'ENABLED', 3, '调度与连接运维部门', 0, 0)
ON DUPLICATE KEY UPDATE
  `dept_name` = VALUES(`dept_name`),
  `dept_full_name` = VALUES(`dept_full_name`),
  `leader_user_id` = VALUES(`leader_user_id`),
  `status` = VALUES(`status`),
  `sort_no` = VALUES(`sort_no`),
  `remark` = VALUES(`remark`),
  `updated_by` = VALUES(`updated_by`);

INSERT INTO `iam_position` (
  `id`, `tenant_id`, `position_code`, `position_name`, `position_level`,
  `status`, `sort_no`, `remark`, `created_by`, `updated_by`
)
VALUES
  (1, 1, 'PLATFORM_ADMIN', '平台管理员', 'P7', 'ENABLED', 1, '平台最高管理岗位', 0, 0),
  (2, 1, 'SEC_AUDITOR', '安全审计岗', 'P6', 'ENABLED', 2, '负责审计与风险规则', 0, 0),
  (3, 1, 'OPS_ENGINEER', '平台运维岗', 'P6', 'ENABLED', 3, '负责调度与集成运维', 0, 0)
ON DUPLICATE KEY UPDATE
  `position_name` = VALUES(`position_name`),
  `position_level` = VALUES(`position_level`),
  `status` = VALUES(`status`),
  `sort_no` = VALUES(`sort_no`),
  `remark` = VALUES(`remark`),
  `updated_by` = VALUES(`updated_by`);

INSERT INTO `iam_employee` (
  `id`, `tenant_id`, `employee_no`, `employee_name`, `dept_id`, `position_id`,
  `mobile`, `email`, `gender`, `hire_date`, `employee_status`, `remark`, `created_by`, `updated_by`
)
VALUES
  (1, 1, 'EMP-0001', '平台管理员', 1, 1, '13800000000', 'admin@saasbasics.local', 'MALE', '2026-01-01', 'ACTIVE', '平台默认管理员员工档案', 0, 0),
  (2, 1, 'EMP-0002', '审计专员', 2, 2, '13800000001', 'auditor@saasbasics.local', 'FEMALE', '2026-01-01', 'ACTIVE', '平台审计员工档案', 0, 0),
  (3, 1, 'EMP-0003', '运维工程师', 3, 3, '13800000002', 'ops@saasbasics.local', 'MALE', '2026-01-01', 'ACTIVE', '平台运维员工档案', 0, 0)
ON DUPLICATE KEY UPDATE
  `employee_name` = VALUES(`employee_name`),
  `dept_id` = VALUES(`dept_id`),
  `position_id` = VALUES(`position_id`),
  `mobile` = VALUES(`mobile`),
  `email` = VALUES(`email`),
  `employee_status` = VALUES(`employee_status`),
  `remark` = VALUES(`remark`),
  `updated_by` = VALUES(`updated_by`);

UPDATE `iam_user`
SET `employee_id` = 1,
    `user_type` = 'PLATFORM',
    `updated_by` = 0
WHERE `id` = 1;

INSERT INTO `iam_user` (
  `id`, `tenant_id`, `user_code`, `username`, `nickname`, `employee_id`,
  `user_type`, `status`, `mobile`, `email`, `password_hash`, `password_changed_at`,
  `need_reset_password`, `remark`, `created_by`, `updated_by`
)
VALUES
  (2, 1, 'SEC_AUDITOR', 'platform.auditor', '审计专员', 2, 'PLATFORM', 'ENABLED',
   '13800000001', 'auditor@saasbasics.local', 'ad89b64d66caa8e30e5d5ce4a9763f4ecc205814c412175f3e2c50027471426d', NOW(3),
   0, '平台默认审计账号', 0, 0),
  (3, 1, 'OPS_ENGINEER', 'platform.ops', '运维工程师', 3, 'PLATFORM', 'ENABLED',
   '13800000002', 'ops@saasbasics.local', 'ad89b64d66caa8e30e5d5ce4a9763f4ecc205814c412175f3e2c50027471426d', NOW(3),
   0, '平台默认运维账号', 0, 0)
ON DUPLICATE KEY UPDATE
  `nickname` = VALUES(`nickname`),
  `employee_id` = VALUES(`employee_id`),
  `user_type` = VALUES(`user_type`),
  `status` = VALUES(`status`),
  `mobile` = VALUES(`mobile`),
  `email` = VALUES(`email`),
  `remark` = VALUES(`remark`),
  `updated_by` = VALUES(`updated_by`);

INSERT INTO `iam_role` (
  `id`, `tenant_id`, `role_group_id`, `role_code`, `role_name`, `role_type`,
  `data_scope_type`, `status`, `is_system`, `sort_no`, `remark`, `created_by`, `updated_by`
)
VALUES
  (2, 1, 1, 'platform_auditor', '平台审计员', 'PLATFORM', 'ALL', 'ENABLED', 1, 2, '平台审计角色', 0, 0),
  (3, 1, 1, 'platform_ops', '平台运维员', 'PLATFORM', 'ALL', 'ENABLED', 1, 3, '平台运维角色', 0, 0)
ON DUPLICATE KEY UPDATE
  `role_name` = VALUES(`role_name`),
  `role_type` = VALUES(`role_type`),
  `data_scope_type` = VALUES(`data_scope_type`),
  `status` = VALUES(`status`),
  `sort_no` = VALUES(`sort_no`),
  `remark` = VALUES(`remark`),
  `updated_by` = VALUES(`updated_by`);

INSERT INTO `iam_user_role` (
  `tenant_id`, `user_id`, `role_id`, `source_type`, `remark`, `created_by`, `updated_by`
)
SELECT 1, 2, 2, 'MANUAL', '默认平台审计授权', 0, 0
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM `iam_user_role` WHERE `tenant_id` = 1 AND `user_id` = 2 AND `role_id` = 2 AND `deleted` = 0
);

INSERT INTO `iam_user_role` (
  `tenant_id`, `user_id`, `role_id`, `source_type`, `remark`, `created_by`, `updated_by`
)
SELECT 1, 3, 3, 'MANUAL', '默认平台运维授权', 0, 0
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM `iam_user_role` WHERE `tenant_id` = 1 AND `user_id` = 3 AND `role_id` = 3 AND `deleted` = 0
);

INSERT INTO `iam_role_api` (
  `tenant_id`, `role_id`, `api_resource_id`, `remark`, `created_by`, `updated_by`
)
SELECT 1, 2, ar.id, '平台审计角色默认授权', 0, 0
FROM `iam_api_resource` ar
WHERE ar.tenant_id = 1
  AND ar.resource_code IN (
    'dashboard:metrics:query',
    'tenant:query',
    'iam:overview:query',
    'iam:user:query',
    'iam:role:query',
    'iam:api-resource:query',
    'iam:user-role:query',
    'iam:role-api:query',
    'system:config:query',
    'audit:operation:query'
  )
  AND NOT EXISTS (
    SELECT 1 FROM `iam_role_api` ra
    WHERE ra.tenant_id = 1
      AND ra.role_id = 2
      AND ra.api_resource_id = ar.id
      AND ra.deleted = 0
  );

INSERT INTO `iam_role_api` (
  `tenant_id`, `role_id`, `api_resource_id`, `remark`, `created_by`, `updated_by`
)
SELECT 1, 3, ar.id, '平台运维角色默认授权', 0, 0
FROM `iam_api_resource` ar
WHERE ar.tenant_id = 1
  AND ar.resource_code IN (
    'dashboard:metrics:query',
    'tenant:query',
    'integration:datasource:query',
    'integration:datasource:write',
    'file:object:query',
    'file:storage:write',
    'scheduler:job:query',
    'scheduler:job:write',
    'codegen:project:query'
  )
  AND NOT EXISTS (
    SELECT 1 FROM `iam_role_api` ra
    WHERE ra.tenant_id = 1
      AND ra.role_id = 3
      AND ra.api_resource_id = ar.id
      AND ra.deleted = 0
  );
