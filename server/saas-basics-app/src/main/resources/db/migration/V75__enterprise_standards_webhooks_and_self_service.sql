-- =====================================================================
-- V75: Outbound CloudEvents Webhooks, Persistent Message Delivery Ledger,
--      and Tenant Self-Service Invitation Tokens
-- =====================================================================
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- 1. Outbound Webhook Subscriptions (CloudEvents 1.0 + HMAC-SHA256)
CREATE TABLE IF NOT EXISTS `int_webhook_subscription` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `public_id` VARCHAR(36) NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 1,
  `subscription_name` VARCHAR(128) NOT NULL,
  `target_url` VARCHAR(512) NOT NULL,
  `event_types` VARCHAR(512) NOT NULL,
  `secret_ciphertext` VARCHAR(512) NOT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
  `timeout_ms` INT NOT NULL DEFAULT 5000,
  `max_retries` INT NOT NULL DEFAULT 3,
  `created_by` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_by` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_int_webhook_pub_id` (`public_id`),
  KEY `idx_int_webhook_tenant` (`tenant_id`, `status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='租户出站 CloudEvents Webhook 订阅配置';

-- 2. Outbound Webhook Delivery Audit & Replay Ledger
CREATE TABLE IF NOT EXISTS `int_webhook_delivery_log` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `event_id` VARCHAR(64) NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 1,
  `subscription_id` BIGINT UNSIGNED NOT NULL,
  `event_type` VARCHAR(128) NOT NULL,
  `target_url` VARCHAR(512) NOT NULL,
  `cloud_event_json` MEDIUMTEXT NOT NULL,
  `http_status` INT NOT NULL DEFAULT 0,
  `response_excerpt` VARCHAR(512) NULL,
  `status` VARCHAR(32) NOT NULL,
  `retry_count` INT NOT NULL DEFAULT 0,
  `duration_ms` BIGINT NOT NULL DEFAULT 0,
  `delivered_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_int_wh_delivery_tenant` (`tenant_id`, `created_at`),
  KEY `idx_int_wh_delivery_sub` (`subscription_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Webhook 投递审计与补偿重放流水';

-- 3. Persistent SMS / Email / Bot Gateway Delivery Ledger
CREATE TABLE IF NOT EXISTS `int_message_delivery_log` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `message_uid` VARCHAR(64) NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 1,
  `channel_type` VARCHAR(32) NOT NULL,
  `provider_code` VARCHAR(64) NOT NULL,
  `recipient` VARCHAR(255) NOT NULL,
  `subject` VARCHAR(255) NULL,
  `content_summary` VARCHAR(512) NULL,
  `status` VARCHAR(32) NOT NULL,
  `provider_message_id` VARCHAR(128) NULL,
  `error_message` VARCHAR(512) NULL,
  `cost_ms` BIGINT NOT NULL DEFAULT 0,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_int_msg_delivery_tenant` (`tenant_id`, `channel_type`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='多渠道消息网关持久化投递审计台账';

-- 4. Self-Service Tenant Member Invite Links
CREATE TABLE IF NOT EXISTS `iam_tenant_invite` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `invite_token` VARCHAR(64) NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 1,
  `inviter_user_id` BIGINT UNSIGNED NOT NULL,
  `invited_email` VARCHAR(191) NULL,
  `role_id` BIGINT UNSIGNED NULL,
  `max_uses` INT NOT NULL DEFAULT 1,
  `used_count` INT NOT NULL DEFAULT 0,
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  `expire_at` DATETIME(3) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_iam_invite_token` (`invite_token`),
  KEY `idx_iam_invite_tenant` (`tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='租户成员自助入驻邀请凭证表';
