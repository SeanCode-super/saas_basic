-- ===================================================================
-- V67: 真实 TOTP 多因子认证 (MFA) 与安全基线闭环扩展
-- ===================================================================
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

ALTER TABLE `iam_user`
  ADD COLUMN `mfa_enabled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用 TOTP 多因子认证' AFTER `need_reset_password`,
  ADD COLUMN `mfa_secret_enc` VARCHAR(255) NULL COMMENT 'Vault AES-256-GCM 托管的 Base32 TOTP 密钥' AFTER `mfa_enabled`,
  ADD COLUMN `mfa_recovery_codes_enc` TEXT NULL COMMENT 'Vault AES-256-GCM 托管的一次性应急恢复码' AFTER `mfa_secret_enc`;
