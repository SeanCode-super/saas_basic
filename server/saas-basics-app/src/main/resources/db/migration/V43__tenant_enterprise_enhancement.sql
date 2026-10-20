-- V43__tenant_enterprise_enhancement.sql
-- Enterprise SaaS Multi-Tenant Plans, Lifecycle, and Quota Enhancement

-- 1. Ensure plat_tenant_package has standard enterprise tiers
INSERT INTO plat_tenant_package (
  id, package_code, package_name, package_type, status,
  max_user_count, max_dept_count, max_storage_mb, max_api_per_minute,
  feature_flags_json, sort_no, remark, created_by, updated_by
) VALUES
  (1, 'BASIC', '基础体验版', 'STANDARD', 'ENABLED', 50, 5, 5120, 1000,
   '["saas.iam.basic", "saas.org.basic"]', 10, '面向小型团队及试用企业，包含核心身份与组织管理能力', 0, 0)
ON DUPLICATE KEY UPDATE
  package_code = 'BASIC',
  package_name = '基础体验版',
  package_type = 'STANDARD',
  max_user_count = 50,
  max_dept_count = 5,
  max_storage_mb = 5120,
  max_api_per_minute = 1000,
  feature_flags_json = '["saas.iam.basic", "saas.org.basic"]',
  sort_no = 10,
  remark = '面向小型团队及试用企业，包含核心身份与组织管理能力';

INSERT INTO plat_tenant_package (
  id, package_code, package_name, package_type, status,
  max_user_count, max_dept_count, max_storage_mb, max_api_per_minute,
  feature_flags_json, sort_no, remark, created_by, updated_by
) VALUES
  (2, 'PROFESSIONAL', '专业成长版', 'STANDARD', 'ENABLED', 200, 25, 20480, 5000,
   '["saas.iam.basic", "saas.org.basic", "saas.file.basic", "saas.workflow.basic"]', 20, '面向成长型企业，支持审批工作流及轻量文件存储', 0, 0)
ON DUPLICATE KEY UPDATE
  package_code = 'PROFESSIONAL',
  package_name = '专业成长版',
  package_type = 'STANDARD',
  max_user_count = 200,
  max_dept_count = 25,
  max_storage_mb = 20480,
  max_api_per_minute = 5000,
  feature_flags_json = '["saas.iam.basic", "saas.org.basic", "saas.file.basic", "saas.workflow.basic"]',
  sort_no = 20,
  remark = '面向成长型企业，支持审批工作流及轻量文件存储';

INSERT INTO plat_tenant_package (
  id, package_code, package_name, package_type, status,
  max_user_count, max_dept_count, max_storage_mb, max_api_per_minute,
  feature_flags_json, sort_no, remark, created_by, updated_by
) VALUES
  (3, 'ENTERPRISE', '企业尊享版', 'ENTERPRISE', 'ENABLED', 1000, 100, 102400, 20000,
   '["saas.iam.all", "saas.org.all", "saas.file.all", "saas.workflow.all", "saas.scheduler.basic", "saas.audit.basic"]', 30, '面向中大型企业，提供全功能安全审计、任务调度与自动化审批', 0, 0)
ON DUPLICATE KEY UPDATE
  package_code = 'ENTERPRISE',
  package_name = '企业尊享版',
  package_type = 'ENTERPRISE',
  max_user_count = 1000,
  max_dept_count = 100,
  max_storage_mb = 102400,
  max_api_per_minute = 20000,
  feature_flags_json = '["saas.iam.all", "saas.org.all", "saas.file.all", "saas.workflow.all", "saas.scheduler.basic", "saas.audit.basic"]',
  sort_no = 30,
  remark = '面向中大型企业，提供全功能安全审计、任务调度与自动化审批';

INSERT INTO plat_tenant_package (
  id, package_code, package_name, package_type, status,
  max_user_count, max_dept_count, max_storage_mb, max_api_per_minute,
  feature_flags_json, sort_no, remark, created_by, updated_by
) VALUES
  (4, 'SUPREME', '平台旗舰版', 'PLATFORM', 'ENABLED', 10000, 2000, 512000, 100000,
   '["*"]', 40, '最高规格旗舰方案，无任何功能限制，专享极致性能与专属独立扩展', 0, 0)
ON DUPLICATE KEY UPDATE
  package_code = 'SUPREME',
  package_name = '平台旗舰版',
  package_type = 'PLATFORM',
  max_user_count = 10000,
  max_dept_count = 2000,
  max_storage_mb = 512000,
  max_api_per_minute = 100000,
  feature_flags_json = '["*"]',
  sort_no = 40,
  remark = '最高规格旗舰方案，无任何功能限制，专享极致性能与专属独立扩展';

-- 2. Update existing tenants with realistic packages and expiration dates
UPDATE plat_tenant
SET package_id = 4, status = 'ACTIVE', expire_at = DATE_ADD(NOW(), INTERVAL 365 DAY)
WHERE id = 1;

UPDATE plat_tenant
SET package_id = 2, status = 'ACTIVE', expire_at = DATE_ADD(NOW(), INTERVAL 180 DAY)
WHERE id = 2;

UPDATE plat_tenant
SET package_id = 3, status = 'ACTIVE', expire_at = DATE_ADD(NOW(), INTERVAL 90 DAY)
WHERE id = 4;

-- 3. Initialize quota records in plat_tenant_quota for all existing tenants based on their package
DELETE FROM plat_tenant_quota;

INSERT INTO plat_tenant_quota (
  tenant_id, quota_code, quota_name, quota_value, used_value, alarm_threshold, reset_cycle, remark, created_by, updated_by
)
SELECT
  t.id AS tenant_id,
  'USER_COUNT' AS quota_code,
  '账号席位上限' AS quota_name,
  p.max_user_count AS quota_value,
  (SELECT COUNT(*) FROM iam_user u WHERE u.tenant_id = t.id AND u.deleted = 0) AS used_value,
  FLOOR(p.max_user_count * 0.8) AS alarm_threshold,
  'NONE' AS reset_cycle,
  '席位配额基线' AS remark,
  0, 0
FROM plat_tenant t
JOIN plat_tenant_package p ON p.id = t.package_id
WHERE t.deleted = 0;

INSERT INTO plat_tenant_quota (
  tenant_id, quota_code, quota_name, quota_value, used_value, alarm_threshold, reset_cycle, remark, created_by, updated_by
)
SELECT
  t.id AS tenant_id,
  'DEPT_COUNT' AS quota_code,
  '组织部门上限' AS quota_name,
  p.max_dept_count AS quota_value,
  (SELECT COUNT(*) FROM iam_department d WHERE d.tenant_id = t.id AND d.deleted = 0) AS used_value,
  FLOOR(p.max_dept_count * 0.8) AS alarm_threshold,
  'NONE' AS reset_cycle,
  '组织架构节点配额' AS remark,
  0, 0
FROM plat_tenant t
JOIN plat_tenant_package p ON p.id = t.package_id
WHERE t.deleted = 0;

INSERT INTO plat_tenant_quota (
  tenant_id, quota_code, quota_name, quota_value, used_value, alarm_threshold, reset_cycle, remark, created_by, updated_by
)
SELECT
  t.id AS tenant_id,
  'STORAGE_MB' AS quota_code,
  '文件存储容量(MB)' AS quota_name,
  p.max_storage_mb AS quota_value,
  0 AS used_value,
  FLOOR(p.max_storage_mb * 0.85) AS alarm_threshold,
  'NONE' AS reset_cycle,
  '存储配额基线' AS remark,
  0, 0
FROM plat_tenant t
JOIN plat_tenant_package p ON p.id = t.package_id
WHERE t.deleted = 0;

INSERT INTO plat_tenant_quota (
  tenant_id, quota_code, quota_name, quota_value, used_value, alarm_threshold, reset_cycle, remark, created_by, updated_by
)
SELECT
  t.id AS tenant_id,
  'API_PER_MINUTE' AS quota_code,
  'API每分钟调用限流' AS quota_name,
  p.max_api_per_minute AS quota_value,
  0 AS used_value,
  FLOOR(p.max_api_per_minute * 0.9) AS alarm_threshold,
  'MINUTE' AS reset_cycle,
  'API防护限流阈值' AS remark,
  0, 0
FROM plat_tenant t
JOIN plat_tenant_package p ON p.id = t.package_id
WHERE t.deleted = 0;
