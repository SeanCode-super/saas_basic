-- V51: Enterprise SSO Federation, Open API Platform, Metering & Workflow Advanced Semantics

-- 1. Enterprise SSO Identity Provider Configuration
CREATE TABLE IF NOT EXISTS `iam_sso_config` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `idp_type` varchar(32) NOT NULL COMMENT 'WECOM, FEISHU, DINGTALK, SAML, OIDC',
  `app_id` varchar(128) NOT NULL,
  `app_secret` varchar(255) NOT NULL,
  `redirect_uri` varchar(512) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `auto_create_user` tinyint(1) NOT NULL DEFAULT 1,
  `config_json` text DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `created_by` bigint unsigned NOT NULL DEFAULT 0,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_by` bigint unsigned NOT NULL DEFAULT 0,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  `deleted_at` datetime(3) DEFAULT NULL,
  `version` int unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_sso_tenant` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='企业级单点登录SSO配置表';

-- 2. Open API Developer Applications
CREATE TABLE IF NOT EXISTS `plat_open_app` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `app_key` varchar(64) NOT NULL,
  `app_secret` varchar(128) NOT NULL,
  `app_name` varchar(128) NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `rate_limit` int NOT NULL DEFAULT 100 COMMENT 'QPS配额',
  `allowed_permissions` text DEFAULT NULL COMMENT '逗号分隔或JSON权限范围',
  `remark` varchar(500) DEFAULT NULL,
  `created_by` bigint unsigned NOT NULL DEFAULT 0,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_by` bigint unsigned NOT NULL DEFAULT 0,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  `deleted_at` datetime(3) DEFAULT NULL,
  `version` int unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_open_tenant` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='开发者开放平台应用表';

-- 3. Tenant Metering Usage Logs
CREATE TABLE IF NOT EXISTS `plat_tenant_metering_log` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `metering_type` varchar(32) NOT NULL COMMENT 'API_CALL, STORAGE_BYTES, ACTIVE_SEATS, SMS_COUNT',
  `metric_value` bigint NOT NULL DEFAULT 1,
  `record_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `billable` tinyint(1) NOT NULL DEFAULT 1,
  `remark` varchar(500) DEFAULT NULL,
  `created_by` bigint unsigned NOT NULL DEFAULT 0,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_by` bigint unsigned NOT NULL DEFAULT 0,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  `deleted_at` datetime(3) DEFAULT NULL,
  `version` int unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_metering_tenant` (`tenant_id`, `metering_type`, `record_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='租户用量计量明细表';

-- 4. Tenant Monthly Billing Records
CREATE TABLE IF NOT EXISTS `plat_tenant_bill` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `bill_no` varchar(64) NOT NULL,
  `billing_period` varchar(16) NOT NULL COMMENT 'e.g. 2026-10',
  `total_amount` decimal(12,2) NOT NULL DEFAULT 0.00,
  `paid_amount` decimal(12,2) NOT NULL DEFAULT 0.00,
  `payment_status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, PAID, OVERDUE',
  `item_breakdown_json` text DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `created_by` bigint unsigned NOT NULL DEFAULT 0,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_by` bigint unsigned NOT NULL DEFAULT 0,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  `deleted_at` datetime(3) DEFAULT NULL,
  `version` int unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_bill_tenant` (`tenant_id`, `billing_period`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='租户账单与结算表';

-- 5. Seed default Open API App for Tenant 1
INSERT INTO `plat_open_app` (
  `id`, `tenant_id`, `app_key`, `app_secret`, `app_name`, `status`, `rate_limit`, `allowed_permissions`, `remark`
) VALUES (
  1, 1, 'ak_dev_enterprise_sandbox', 'sk_sec_789f41b2c608a1d490efc483a9', '企业开放平台官方沙箱应用', 'ENABLED', 200, 'open:user:read,open:dept:read,open:workflow:read', '用于第三方对接、OpenAPI开发及沙箱联调'
) AS new_app ON DUPLICATE KEY UPDATE `app_name` = new_app.`app_name`;

-- 6. Seed sample bill and metering metrics for Tenant 1
INSERT INTO `plat_tenant_bill` (
  `id`, `tenant_id`, `bill_no`, `billing_period`, `total_amount`, `paid_amount`, `payment_status`, `item_breakdown_json`, `remark`
) VALUES (
  1, 1, 'BILL202610001', '2026-10', 3680.00, 3680.00, 'PAID', '{"apiCalls": 182400, "storageGb": 256, "activeSeats": 85, "supportTier": "PLATINUM"}', '2026年10月度租户资源计量结算单'
) AS new_bill ON DUPLICATE KEY UPDATE `total_amount` = new_bill.`total_amount`;
