-- ===================================================================
-- V65: 全链路可控接口传输加密动态策略表 (X25519 + HKDF-SHA256 + AES-256-GCM)
-- ===================================================================
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

CREATE TABLE IF NOT EXISTS `sys_transport_crypto_policy` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `policy_code` VARCHAR(64) NOT NULL DEFAULT 'GLOBAL' COMMENT '策略唯一编码(GLOBAL)',
  `mode` VARCHAR(32) NOT NULL DEFAULT 'OPTIONAL' COMMENT '传输模式: OFF, OPTIONAL, REQUIRED',
  `scope_mode` VARCHAR(32) NOT NULL DEFAULT 'ALL_API' COMMENT '作用域: ALL_API, SENSITIVE_ONLY, CUSTOM',
  `algorithm` VARCHAR(64) NOT NULL DEFAULT 'X25519-HKDF-AES-256-GCM' COMMENT '加密套件名称',
  `encrypt_request` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否加密请求体',
  `encrypt_response` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否加密响应体',
  `encrypt_query` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否加密Query查询参数',
  `replay_protection` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用Timestamp+Nonce防重放',
  `timestamp_skew_seconds` INT NOT NULL DEFAULT 300 COMMENT '防重放允许时间偏差秒数',
  `include_paths_json` TEXT NOT NULL COMMENT '包含路径Ant规则JSON数组',
  `exclude_paths_json` TEXT NOT NULL COMMENT '豁免路径Ant规则JSON数组',
  `sensitive_paths_json` TEXT NOT NULL COMMENT '高敏路径Ant规则JSON数组',
  `policy_version` BIGINT NOT NULL DEFAULT 1 COMMENT '动态策略递增版本号',
  `remark` VARCHAR(500) NULL COMMENT '策略变更备注',
  `created_by` BIGINT NOT NULL DEFAULT 0,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_by` BIGINT NOT NULL DEFAULT 0,
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_transport_policy_code` (`policy_code`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='可控接口传输加密动态策略表';

INSERT INTO `sys_transport_crypto_policy` (
  `policy_code`,
  `mode`,
  `scope_mode`,
  `algorithm`,
  `encrypt_request`,
  `encrypt_response`,
  `encrypt_query`,
  `replay_protection`,
  `timestamp_skew_seconds`,
  `include_paths_json`,
  `exclude_paths_json`,
  `sensitive_paths_json`,
  `policy_version`,
  `remark`
)
SELECT
  'GLOBAL',
  'OPTIONAL',
  'ALL_API',
  'X25519-HKDF-AES-256-GCM',
  1,
  1,
  1,
  1,
  300,
  JSON_ARRAY('/api/**'),
  JSON_ARRAY(
    '/api/security/transport/policy',
    '/api/health/**',
    '/api/ws/**',
    '/api/file/download/**',
    '/api/file/preview/**',
    '/api/file/upload/**',
    '/actuator/**',
    '/api-docs/**',
    '/swagger-ui/**',
    '/swagger-ui.html',
    '/doc.html',
    '/webjars/**'
  ),
  JSON_ARRAY(
    '/api/auth/login',
    '/api/auth/refresh-token',
    '/api/auth/sso/**',
    '/api/iam/**',
    '/api/system/**',
    '/api/ai/**',
    '/api/tenant/**'
  ),
  1,
  '初始化全局动态传输加密默认基线（兼容协商模式）'
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_transport_crypto_policy` WHERE `policy_code` = 'GLOBAL' AND `deleted` = 0
);
