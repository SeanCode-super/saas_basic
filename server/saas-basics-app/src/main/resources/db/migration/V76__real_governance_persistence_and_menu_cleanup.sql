-- =====================================================================
-- V76: Seed real risk rules & sequence rules, add governance API
--      permissions, hide redundant overview/legacy menus, and expose
--      unified /organization workspace entry.
-- =====================================================================
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- 1. Seed baseline security risk rules for active tenants
INSERT INTO `sec_risk_rule` (
  `tenant_id`, `rule_code`, `rule_name`, `risk_type`, `rule_expr`,
  `action_json`, `status`, `remark`, `created_by`, `updated_by`, `deleted`, `version`
)
SELECT
  t.`id`,
  seed.`rule_code`,
  seed.`rule_name`,
  seed.`risk_type`,
  seed.`rule_expr`,
  seed.`action_json`,
  'ENABLED',
  seed.`remark`,
  1,
  1,
  0,
  0
FROM `plat_tenant` t
CROSS JOIN (
  SELECT
    'RULE_BRUTE_FORCE' AS `rule_code`,
    '暴力破解防范规则' AS `rule_name`,
    'HIGH' AS `risk_type`,
    'failed_login_5m >= 5' AS `rule_expr`,
    JSON_OBJECT('action', 'LOCK_ACCOUNT', 'threshold', 5, 'windowMinutes', 5) AS `action_json`,
    '同一 IP 或账号 5 分钟内连续认证失败达 5 次，自动触发告警并锁定 15 分钟。' AS `remark`
  UNION ALL
  SELECT
    'RULE_EXPORT_BURST',
    '大批量敏捷数据导出',
    'CRITICAL',
    'export_count_1h >= 3',
    JSON_OBJECT('action', 'REQUIRE_APPROVAL', 'threshold', 3, 'windowMinutes', 60),
    '单次导出数据超过 5,000 条，或 1 小时内累计导出 3 次以上，触发二次主管审批与留痕。'
  UNION ALL
  SELECT
    'RULE_PRIV_ESCALATION',
    '未授权提权与越权拦截',
    'CRITICAL',
    'unauthorized_role_grant >= 1',
    JSON_OBJECT('action', 'REVOKE_SESSION', 'threshold', 1, 'windowMinutes', 15),
    '非超级管理员账号试图直接修改 iam_role_api 或分配根组织管理权限，立即拦截并阻断会话。'
  UNION ALL
  SELECT
    'RULE_GEO_ANOMALY',
    '异地/多端并发登录异常',
    'MEDIUM',
    'distinct_ip_10m >= 3',
    JSON_OBJECT('action', 'TRIGGER_MFA', 'threshold', 3, 'windowMinutes', 10),
    '同一用户账号在 10 分钟内出现跨区多 IP 并发登录，强制触发 MFA 验证码二次校验。'
) seed
WHERE t.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `sec_risk_rule` r
    WHERE r.`tenant_id` = t.`id`
      AND r.`rule_code` = seed.`rule_code`
      AND r.`deleted` = 0
  );

-- 2. Seed baseline sequence generator rules for active tenants
INSERT INTO `sys_sequence_rule` (
  `tenant_id`, `rule_code`, `rule_name`, `prefix`, `date_pattern`,
  `seq_length`, `step`, `current_value`, `reset_cycle`, `status`,
  `remark`, `created_by`, `updated_by`, `deleted`, `version`
)
SELECT
  t.`id`,
  seed.`rule_code`,
  seed.`rule_name`,
  seed.`prefix`,
  seed.`date_pattern`,
  seed.`seq_length`,
  1,
  seed.`current_value`,
  seed.`reset_cycle`,
  'ENABLED',
  seed.`remark`,
  1,
  1,
  0,
  0
FROM `plat_tenant` t
CROSS JOIN (
  SELECT 'SEQ_TENANT' AS `rule_code`, '多租户组织编号' AS `rule_name`, 'TNT' AS `prefix`, 'yyyyMMdd' AS `date_pattern`, 6 AS `seq_length`, 1 AS `current_value`, 'DAILY' AS `reset_cycle`, '租户开通自动分配编码序列' AS `remark`
  UNION ALL
  SELECT 'SEQ_ORDER', '商城与履约订单流水号', 'ORD', 'yyyyMMdd', 8, 1, 'DAILY', '订单与采购履约流水序列'
  UNION ALL
  SELECT 'SEQ_CONTRACT', '电子合同编号', 'CTR', 'yyyy', 6, 1, 'YEARLY', 'CRM 商务合同归档编码序列'
  UNION ALL
  SELECT 'SEQ_DISPATCH', '调度中心批次跟踪号', 'DSP', 'yyyyMMddHH', 4, 1, 'DAILY', '自动化调度批次跟踪序列'
) seed
WHERE t.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `sys_sequence_rule` s
    WHERE s.`tenant_id` = t.`id`
      AND s.`rule_code` = seed.`rule_code`
      AND s.`deleted` = 0
  );

-- 3. Add API permissions for audit security & system sequence management and grant to admin roles
INSERT INTO `iam_api_resource` (
  `tenant_id`, `resource_code`, `resource_name`, `http_method`, `url_pattern`,
  `auth_required`, `status`, `remark`, `created_by`, `updated_by`, `deleted`, `version`
)
SELECT
  t.`id`,
  api.`resource_code`,
  api.`resource_name`,
  api.`http_method`,
  api.`url_pattern`,
  1,
  'ENABLED',
  api.`remark`,
  1,
  1,
  0,
  0
FROM `plat_tenant` t
CROSS JOIN (
  SELECT 'audit:security:write' AS `resource_code`, '安全风控与黑名单管控' AS `resource_name`, 'POST' AS `http_method`, '/api/audit/**' AS `url_pattern`, '黑名单拦截与风控规则维护权限' AS `remark`
  UNION ALL
  SELECT 'system:sequence:query', '系统序列规则查询', 'GET', '/api/system/sequences/**', '查询分布式序列规则权限'
  UNION ALL
  SELECT 'system:sequence:write', '系统序列规则维护', 'POST', '/api/system/sequences/**', '修改、发号与重置序列权限'
) api
WHERE t.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `iam_api_resource` ar
    WHERE ar.`tenant_id` = t.`id`
      AND ar.`resource_code` = api.`resource_code`
      AND ar.`deleted` = 0
  );

INSERT INTO `iam_role_api` (
  `tenant_id`, `role_id`, `api_resource_id`, `remark`, `created_by`, `updated_by`, `deleted`, `version`
)
SELECT
  ar.`tenant_id`,
  r.`id`,
  ar.`id`,
  'V76 governance api grant',
  1,
  1,
  0,
  0
FROM `iam_api_resource` ar
INNER JOIN `iam_role` r
  ON r.`tenant_id` = ar.`tenant_id`
 AND r.`deleted` = 0
 AND r.`status` = 'ENABLED'
WHERE ar.`deleted` = 0
  AND ar.`resource_code` IN ('audit:security:write', 'system:sequence:query', 'system:sequence:write')
  AND (
    r.`role_code` = 'platform_super_admin'
    OR r.`role_code` LIKE '%_super_admin'
    OR r.`data_scope_type` = 'ALL'
  )
  AND NOT EXISTS (
    SELECT 1 FROM `iam_role_api` ra
    WHERE ra.`tenant_id` = ar.`tenant_id`
      AND ra.`role_id` = r.`id`
      AND ra.`api_resource_id` = ar.`id`
      AND ra.`deleted` = 0
  );

-- 4. Hide redundant filler Overview, static/probe pages, and legacy IAM org submenus
UPDATE `iam_menu`
SET `visible` = 0
WHERE `deleted` = 0
  AND `menu_code` IN (
    'iam_overview',
    'iam_department',
    'iam_position',
    'iam_employee',
    'tenant_overview',
    'system_overview',
    'integration_overview',
    'integration_connection',
    'integration_health',
    'file_overview',
    'scheduler_overview',
    'scheduler_pipeline',
    'codegen_overview',
    'codegen_template',
    'audit_overview'
  );

-- 5. Point parent module menu redirects at real data workbenches
UPDATE `iam_menu` SET `redirect` = '/tenant/ledger' WHERE `deleted` = 0 AND `menu_code` = 'tenant';
UPDATE `iam_menu` SET `redirect` = '/iam/user' WHERE `deleted` = 0 AND `menu_code` = 'iam';
UPDATE `iam_menu` SET `redirect` = '/system/registry' WHERE `deleted` = 0 AND `menu_code` = 'system';
UPDATE `iam_menu` SET `redirect` = '/integration/registry' WHERE `deleted` = 0 AND `menu_code` = 'integration';
UPDATE `iam_menu` SET `redirect` = '/file/upload' WHERE `deleted` = 0 AND `menu_code` = 'file';
UPDATE `iam_menu` SET `redirect` = '/scheduler/strategy' WHERE `deleted` = 0 AND `menu_code` = 'scheduler';
UPDATE `iam_menu` SET `redirect` = '/codegen/ai-builder' WHERE `deleted` = 0 AND `menu_code` = 'codegen';
UPDATE `iam_menu` SET `redirect` = '/audit/trail' WHERE `deleted` = 0 AND `menu_code` = 'audit';

-- 6. Expose unified /organization (组织与成员中心) menu under 基础治理 for each active tenant
INSERT INTO `iam_menu` (
  `tenant_id`, `parent_id`, `menu_type`, `menu_code`, `app_code`, `menu_name`,
  `route_path`, `component_path`, `component_name`, `permission_code`, `icon`,
  `visible`, `keep_alive`, `sort_no`, `status`, `meta_json`, `remark`,
  `created_by`, `updated_by`, `deleted`, `version`
)
SELECT
  t.`id`,
  0,
  'MENU',
  'organization',
  'iam',
  '组织与成员中心',
  '/organization',
  '@/views/organization/OrganizationView.vue',
  'OrganizationView',
  'iam:user:query',
  'OfficeBuilding',
  1,
  1,
  15,
  'ENABLED',
  JSON_OBJECT('cluster', 'foundation', 'domain', 'organization'),
  '组织主数据、岗位体系与员工入职统一工作台',
  1,
  1,
  0,
  0
FROM `plat_tenant` t
WHERE t.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `iam_menu` m
    WHERE m.`tenant_id` = t.`id`
      AND m.`menu_code` = 'organization'
      AND m.`deleted` = 0
  );

-- Grant organization menu to admin roles in both iam_role_menu & iam_menu_permission
INSERT IGNORE INTO `iam_role_menu` (`tenant_id`, `role_id`, `menu_id`)
SELECT
  m.`tenant_id`,
  r.`id`,
  m.`id`
FROM `iam_menu` m
INNER JOIN `iam_role` r
  ON r.`tenant_id` = m.`tenant_id`
 AND r.`deleted` = 0
 AND r.`status` = 'ENABLED'
WHERE m.`menu_code` = 'organization'
  AND m.`deleted` = 0
  AND (
    r.`role_code` = 'platform_super_admin'
    OR r.`role_code` LIKE '%_super_admin'
    OR r.`data_scope_type` = 'ALL'
  );

INSERT INTO `iam_menu_permission` (
  `tenant_id`, `menu_id`, `menu_code`, `subject_type`, `subject_value`,
  `button_codes_json`, `status`, `created_by`, `created_at`, `updated_by`,
  `updated_at`, `deleted`, `version`, `remark`
)
SELECT
  m.`tenant_id`,
  m.`id`,
  m.`menu_code`,
  'ROLE',
  CAST(r.`id` AS CHAR),
  JSON_ARRAY(),
  'ENABLED',
  1,
  NOW(3),
  1,
  NOW(3),
  0,
  0,
  'V76 organization menu grant'
FROM `iam_menu` m
INNER JOIN `iam_role` r
  ON r.`tenant_id` = m.`tenant_id`
 AND r.`deleted` = 0
 AND r.`status` = 'ENABLED'
WHERE m.`menu_code` = 'organization'
  AND m.`deleted` = 0
  AND (
    r.`role_code` = 'platform_super_admin'
    OR r.`role_code` LIKE '%_super_admin'
    OR r.`data_scope_type` = 'ALL'
  )
ON DUPLICATE KEY UPDATE
  `updated_at` = NOW(3),
  `deleted` = 0,
  `status` = 'ENABLED';
