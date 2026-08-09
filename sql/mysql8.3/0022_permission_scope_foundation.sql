CREATE TABLE IF NOT EXISTS iam_menu_permission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  menu_id BIGINT NOT NULL,
  menu_code VARCHAR(128) NOT NULL,
  subject_type VARCHAR(32) NOT NULL,
  subject_value VARCHAR(64) NOT NULL,
  button_codes_json JSON NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
  created_by BIGINT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_by BIGINT NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted TINYINT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  remark VARCHAR(255) NULL,
  UNIQUE KEY uk_iam_menu_permission_subject (tenant_id, menu_id, subject_type, subject_value),
  KEY idx_iam_menu_permission_subject (tenant_id, subject_type, subject_value, status)
);

CREATE TABLE IF NOT EXISTS iam_data_permission_rule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  resource_code VARCHAR(128) NOT NULL,
  resource_name VARCHAR(128) NOT NULL,
  subject_type VARCHAR(32) NOT NULL,
  subject_value VARCHAR(64) NOT NULL,
  scope_type VARCHAR(32) NOT NULL,
  config_json JSON NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
  created_by BIGINT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_by BIGINT NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted TINYINT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  remark VARCHAR(255) NULL,
  UNIQUE KEY uk_iam_data_permission_rule_subject (tenant_id, resource_code, subject_type, subject_value, scope_type),
  KEY idx_iam_data_permission_rule_subject (tenant_id, resource_code, subject_type, subject_value, status)
);

INSERT INTO iam_menu_permission (
  tenant_id, menu_id, menu_code, subject_type, subject_value, button_codes_json, status,
  created_by, created_at, updated_by, updated_at, deleted, version, remark
)
SELECT DISTINCT
  m.tenant_id,
  m.id,
  m.menu_code,
  'ROLE',
  CAST(ra.role_id AS CHAR),
  JSON_ARRAY(),
  'ENABLED',
  1,
  NOW(3),
  1,
  NOW(3),
  0,
  0,
  'permission bootstrap'
FROM iam_menu m
INNER JOIN iam_api_resource ar
  ON ar.tenant_id = m.tenant_id
 AND ar.resource_code = m.permission_code
 AND ar.deleted = 0
 AND ar.status = 'ENABLED'
INNER JOIN iam_role_api ra
  ON ra.tenant_id = ar.tenant_id
 AND ra.api_resource_id = ar.id
 AND ra.deleted = 0
WHERE m.deleted = 0
  AND m.status = 'ENABLED'
  AND m.permission_code IS NOT NULL
ON DUPLICATE KEY UPDATE
  updated_at = VALUES(updated_at),
  updated_by = VALUES(updated_by),
  deleted = 0,
  status = VALUES(status);

INSERT INTO iam_menu_permission (
  tenant_id, menu_id, menu_code, subject_type, subject_value, button_codes_json, status,
  created_by, created_at, updated_by, updated_at, deleted, version, remark
)
SELECT DISTINCT
  m.tenant_id,
  m.id,
  m.menu_code,
  'ROLE',
  CAST(r.id AS CHAR),
  JSON_ARRAY(),
  'ENABLED',
  1,
  NOW(3),
  1,
  NOW(3),
  0,
  0,
  'baseline menu bootstrap'
FROM iam_menu m
INNER JOIN iam_role r
  ON r.tenant_id = m.tenant_id
 AND r.deleted = 0
 AND r.status = 'ENABLED'
WHERE m.deleted = 0
  AND m.status = 'ENABLED'
  AND m.permission_code IS NULL
ON DUPLICATE KEY UPDATE
  updated_at = VALUES(updated_at),
  updated_by = VALUES(updated_by),
  deleted = 0,
  status = VALUES(status);

INSERT INTO iam_data_permission_rule (
  tenant_id, resource_code, resource_name, subject_type, subject_value, scope_type, config_json, status,
  created_by, created_at, updated_by, updated_at, deleted, version, remark
)
SELECT
  r.tenant_id,
  resource.resource_code,
  resource.resource_name,
  'ROLE',
  CAST(r.id AS CHAR),
  'TENANT',
  JSON_OBJECT(),
  'ENABLED',
  1,
  NOW(3),
  1,
  NOW(3),
  0,
  0,
  'baseline data scope bootstrap'
FROM iam_role r
INNER JOIN (
  SELECT 'iam:user:list' AS resource_code, '用户数据范围' AS resource_name
  UNION ALL SELECT 'iam:department:list', '部门数据范围'
  UNION ALL SELECT 'iam:employee:list', '员工数据范围'
  UNION ALL SELECT 'iam:position:list', '岗位数据范围'
) resource
WHERE r.deleted = 0
  AND r.status = 'ENABLED'
ON DUPLICATE KEY UPDATE
  updated_at = VALUES(updated_at),
  updated_by = VALUES(updated_by),
  deleted = 0,
  status = VALUES(status);
