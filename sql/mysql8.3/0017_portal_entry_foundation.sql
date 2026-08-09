CREATE TABLE IF NOT EXISTS `iam_portal_client` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL DEFAULT 0,
  `client_id` varchar(64) NOT NULL,
  `tenant_code` varchar(64) NOT NULL,
  `client_name` varchar(100) NOT NULL,
  `portal_title` varchar(120) NOT NULL,
  `welcome_title` varchar(160) DEFAULT NULL,
  `welcome_text` varchar(500) DEFAULT NULL,
  `logo_url` varchar(255) DEFAULT NULL,
  `theme_code` varchar(32) NOT NULL DEFAULT 'saas-basics',
  `background_image_url` varchar(255) DEFAULT NULL,
  `background_color` varchar(32) DEFAULT NULL,
  `filing_info` varchar(255) DEFAULT NULL,
  `login_policy_id` bigint unsigned DEFAULT NULL,
  `password_policy_id` bigint unsigned DEFAULT NULL,
  `captcha_mode` varchar(16) NOT NULL DEFAULT 'IMAGE',
  `slider_reserved` tinyint(1) NOT NULL DEFAULT 1,
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint unsigned NOT NULL DEFAULT 0,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_by` bigint unsigned NOT NULL DEFAULT 0,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  `deleted_at` datetime(3) DEFAULT NULL,
  `version` int NOT NULL DEFAULT 0,
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_portal_client_client_id` (`client_id`),
  KEY `idx_portal_client_tenant` (`tenant_id`, `status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `iam_portal_terminal` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL DEFAULT 0,
  `portal_client_id` bigint unsigned NOT NULL,
  `terminal_code` varchar(64) NOT NULL,
  `terminal_name` varchar(100) NOT NULL,
  `terminal_type` varchar(32) NOT NULL DEFAULT 'BROWSER',
  `portal_title` varchar(120) DEFAULT NULL,
  `logo_url` varchar(255) DEFAULT NULL,
  `theme_code` varchar(32) DEFAULT NULL,
  `background_image_url` varchar(255) DEFAULT NULL,
  `background_color` varchar(32) DEFAULT NULL,
  `login_policy_id` bigint unsigned DEFAULT NULL,
  `password_policy_id` bigint unsigned DEFAULT NULL,
  `captcha_mode` varchar(16) NOT NULL DEFAULT 'IMAGE',
  `slider_reserved` tinyint(1) NOT NULL DEFAULT 1,
  `is_default` tinyint(1) NOT NULL DEFAULT 0,
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint unsigned NOT NULL DEFAULT 0,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_by` bigint unsigned NOT NULL DEFAULT 0,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  `deleted_at` datetime(3) DEFAULT NULL,
  `version` int NOT NULL DEFAULT 0,
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_portal_terminal_code` (`portal_client_id`, `terminal_code`),
  KEY `idx_portal_terminal_tenant` (`tenant_id`, `status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `iam_portal_client` (
  `id`, `tenant_id`, `client_id`, `tenant_code`, `client_name`, `portal_title`, `welcome_title`, `welcome_text`,
  `logo_url`, `theme_code`, `background_image_url`, `background_color`, `filing_info`, `login_policy_id`, `password_policy_id`,
  `captcha_mode`, `slider_reserved`, `status`, `remark`, `created_by`, `updated_by`
)
SELECT
  1, 1, '72cf4f659dc54dcfaeecd32eabf483b5', 'platform', '平台统一门户', 'SaaS 基础底座控制台',
  '统一门户入口', '客户端、终端、登录策略和密码策略统一在入口层编排，承接平台控制台第一屏。',
  '', 'saas-basics', '', '#0f2740', '备案信息预留位：沪ICP备2026000001号-1',
  (SELECT `id` FROM `iam_login_policy` WHERE `tenant_id` = 1 AND `status` = 'ENABLED' AND `deleted` = 0 ORDER BY `id` ASC LIMIT 1),
  (SELECT `id` FROM `iam_password_policy` WHERE `tenant_id` = 1 AND `status` = 'ENABLED' AND `deleted` = 0 ORDER BY `id` ASC LIMIT 1),
  'IMAGE', 1, 'ENABLED', '平台门户入口第一版', 0, 0
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM `iam_portal_client` WHERE `client_id` = '72cf4f659dc54dcfaeecd32eabf483b5'
);

INSERT INTO `iam_portal_terminal` (
  `id`, `tenant_id`, `portal_client_id`, `terminal_code`, `terminal_name`, `terminal_type`, `portal_title`,
  `logo_url`, `theme_code`, `background_image_url`, `background_color`, `login_policy_id`, `password_policy_id`,
  `captcha_mode`, `slider_reserved`, `is_default`, `status`, `remark`, `created_by`, `updated_by`
)
SELECT
  1, 1, `id`, 'web', 'Web 控制台', 'BROWSER', 'SaaS 基础底座控制台',
  '', 'saas-basics', '', '#0f2740',
  `login_policy_id`, `password_policy_id`, 'IMAGE', 1, 1, 'ENABLED', '浏览器终端入口', 0, 0
FROM `iam_portal_client`
WHERE `client_id` = '72cf4f659dc54dcfaeecd32eabf483b5'
  AND NOT EXISTS (
    SELECT 1 FROM `iam_portal_terminal` WHERE `portal_client_id` = `iam_portal_client`.`id` AND `terminal_code` = 'web'
  );
