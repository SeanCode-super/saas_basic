CREATE TABLE IF NOT EXISTS `plat_outbox_event` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `event_id` varchar(64) NOT NULL,
    `event_type` varchar(64) NOT NULL,
    `aggregate_type` varchar(64) NOT NULL,
    `aggregate_id` varchar(128) NOT NULL,
    `scope_tenant_id` bigint DEFAULT NULL,
    `payload_json` text NOT NULL,
    `status` varchar(32) NOT NULL DEFAULT 'PENDING',
    `retry_count` int NOT NULL DEFAULT 0,
    `max_retries` int NOT NULL DEFAULT 5,
    `next_retry_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `last_error` varchar(512) DEFAULT NULL,
    `processed_at` datetime DEFAULT NULL,
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plat_outbox_event_id` (`event_id`),
    KEY `idx_plat_outbox_status_next` (`status`, `next_retry_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `plat_tenant_snapshot_record` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `tenant_id` bigint NOT NULL,
    `snapshot_id` varchar(64) NOT NULL,
    `tenant_code` varchar(64) NOT NULL,
    `file_name` varchar(128) NOT NULL,
    `file_size_bytes` bigint NOT NULL DEFAULT 0,
    `checksum_sha256` varchar(128) NOT NULL,
    `storage_path` varchar(512) NOT NULL,
    `table_counts_json` text,
    `status` varchar(32) NOT NULL DEFAULT 'COMPLETED',
    `created_by` varchar(64) DEFAULT NULL,
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plat_snapshot_id` (`snapshot_id`),
    KEY `idx_plat_snapshot_tenant` (`tenant_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
