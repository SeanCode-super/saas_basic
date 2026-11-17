CREATE TABLE IF NOT EXISTS `sys_api_endpoint_stat` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `endpoint_key` varchar(255) NOT NULL,
    `http_method` varchar(16) NOT NULL,
    `path_pattern` varchar(255) NOT NULL,
    `module_code` varchar(64) DEFAULT NULL,
    `total_calls` bigint NOT NULL DEFAULT 0,
    `encrypted_calls` bigint NOT NULL DEFAULT 0,
    `plaintext_calls` bigint NOT NULL DEFAULT 0,
    `rejected_calls` bigint NOT NULL DEFAULT 0,
    `error_calls` bigint NOT NULL DEFAULT 0,
    `total_duration_ms` bigint NOT NULL DEFAULT 0,
    `p95_latency_ms` double NOT NULL DEFAULT 0,
    `p99_latency_ms` double NOT NULL DEFAULT 0,
    `last_called_at_ms` bigint NOT NULL DEFAULT 0,
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_api_endpoint_stat_key` (`endpoint_key`),
    KEY `idx_sys_api_endpoint_stat_module` (`module_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sys_alert_rule` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `rule_code` varchar(64) NOT NULL,
    `rule_name` varchar(128) NOT NULL,
    `metric_type` varchar(64) NOT NULL,
    `comparison_op` varchar(16) NOT NULL DEFAULT 'GTE',
    `threshold_value` double NOT NULL DEFAULT 0,
    `severity` varchar(32) NOT NULL DEFAULT 'WARNING',
    `cooldown_seconds` int NOT NULL DEFAULT 300,
    `notify_channels` varchar(128) NOT NULL DEFAULT 'IN_APP',
    `webhook_url` varchar(512) DEFAULT NULL,
    `enabled` tinyint(1) NOT NULL DEFAULT 1,
    `last_triggered_at` datetime DEFAULT NULL,
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_alert_rule_code` (`rule_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sys_alert_event` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `rule_id` bigint NOT NULL,
    `rule_code` varchar(64) NOT NULL,
    `rule_name` varchar(128) NOT NULL,
    `severity` varchar(32) NOT NULL,
    `metric_type` varchar(64) NOT NULL,
    `metric_value` double NOT NULL,
    `threshold_value` double NOT NULL,
    `target_endpoint` varchar(255) DEFAULT NULL,
    `message` varchar(1024) NOT NULL,
    `dispatch_status` varchar(32) NOT NULL DEFAULT 'LOCAL_ONLY',
    `dispatch_detail` varchar(512) DEFAULT NULL,
    `acknowledged` tinyint(1) NOT NULL DEFAULT 0,
    `acknowledged_by` varchar(64) DEFAULT NULL,
    `acknowledged_at` datetime DEFAULT NULL,
    `triggered_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_sys_alert_event_rule` (`rule_id`, `triggered_at`),
    KEY `idx_sys_alert_event_ack` (`acknowledged`, `triggered_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `sys_alert_rule` (`rule_code`, `rule_name`, `metric_type`, `comparison_op`, `threshold_value`, `severity`, `cooldown_seconds`, `notify_channels`, `webhook_url`, `enabled`)
SELECT 'API_P99_LATENCY_HIGH', '核心接口 P99 响应延迟过高告警', 'API_P99_MS', 'GTE', 800, 'WARNING', 300, 'IN_APP,WEBHOOK', NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM `sys_alert_rule` WHERE `rule_code` = 'API_P99_LATENCY_HIGH');

INSERT INTO `sys_alert_rule` (`rule_code`, `rule_name`, `metric_type`, `comparison_op`, `threshold_value`, `severity`, `cooldown_seconds`, `notify_channels`, `webhook_url`, `enabled`)
SELECT 'TRANSPORT_REJECTED_SPIKE', '非法明文或防重放拦截突发告警', 'TRANSPORT_REJECTED', 'GTE', 20, 'CRITICAL', 180, 'IN_APP,WEBHOOK', NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM `sys_alert_rule` WHERE `rule_code` = 'TRANSPORT_REJECTED_SPIKE');

INSERT INTO `sys_alert_rule` (`rule_code`, `rule_name`, `metric_type`, `comparison_op`, `threshold_value`, `severity`, `cooldown_seconds`, `notify_channels`, `webhook_url`, `enabled`)
SELECT 'JVM_HEAP_HIGH', 'JVM 堆内存使用率超阈值预警', 'JVM_HEAP_PERCENT', 'GTE', 85, 'CRITICAL', 300, 'IN_APP,WEBHOOK', NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM `sys_alert_rule` WHERE `rule_code` = 'JVM_HEAP_HIGH');
