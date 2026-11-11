-- ===================================================================
-- V66: 可配置 X25519 密钥环持久化与轮换策略字段扩展
-- ===================================================================
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

ALTER TABLE `sys_transport_crypto_policy`
  ADD COLUMN `key_rotation_hours` INT NOT NULL DEFAULT 24 COMMENT 'X25519 密钥建议轮换周期（小时）' AFTER `timestamp_skew_seconds`,
  ADD COLUMN `max_grace_keys` INT NOT NULL DEFAULT 3 COMMENT '宽限期内保留历史私钥数量' AFTER `key_rotation_hours`;

CREATE TABLE IF NOT EXISTS `sys_transport_crypto_key` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `key_id` VARCHAR(64) NOT NULL COMMENT '密钥标识 (kid)',
  `key_source` VARCHAR(32) NOT NULL DEFAULT 'AUTO_GENERATED' COMMENT '来源: AUTO_GENERATED, CUSTOM_IMPORTED',
  `sealed_private_key` TEXT NOT NULL COMMENT '经 Vault AES-256-GCM 托管加密的 PKCS8 私钥',
  `public_key_base64` VARCHAR(128) NOT NULL COMMENT '32字节 X25519 原始公钥 Base64',
  `fingerprint_sha256` VARCHAR(64) NOT NULL COMMENT '公钥 SHA-256 指纹',
  `is_active` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否当前活跃密钥',
  `issued_at` DATETIME(3) NOT NULL COMMENT '签发时间 (UTC)',
  `remark` VARCHAR(500) NULL COMMENT '配置或轮换说明',
  `created_by` BIGINT NOT NULL DEFAULT 0,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_by` BIGINT NOT NULL DEFAULT 0,
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_transport_key_id` (`key_id`, `deleted`),
  KEY `idx_sys_transport_key_active` (`is_active`, `issued_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='可控传输加密 X25519 持久化密钥环表';
