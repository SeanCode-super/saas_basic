CREATE TABLE IF NOT EXISTS `org_migration_scope` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `public_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `source_namespace` varchar(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `source_scope_type` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `source_scope_key` varchar(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `target_organization_id` bigint unsigned NOT NULL,
  `target_parent_unit_id` bigint unsigned DEFAULT NULL,
  `effective_at` datetime(3) NOT NULL,
  `configuration_fingerprint` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT',
  `created_by` bigint unsigned NOT NULL DEFAULT 0,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_by` bigint unsigned NOT NULL DEFAULT 0,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  `deleted_at` datetime(3) DEFAULT NULL,
  `version` int unsigned NOT NULL DEFAULT 0,
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_org_migration_scope_public_id` (`tenant_id`, `public_id`, `deleted`),
  UNIQUE KEY `uk_org_migration_scope_source` (
    `tenant_id`, `source_namespace`, `source_scope_type`, `source_scope_key`, `deleted`
  ),
  KEY `idx_org_migration_scope_target` (
    `tenant_id`, `target_organization_id`, `status`, `deleted`
  ),
  CONSTRAINT `chk_org_migration_scope_status` CHECK (
    `status` IN ('DRAFT', 'READY', 'FROZEN', 'CUTOVER_READY', 'CUTOVER', 'ARCHIVED')
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Explicit source scope to organization migration configuration';

CREATE TABLE IF NOT EXISTS `org_migration_run` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `public_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `migration_scope_id` bigint unsigned NOT NULL,
  `migration_revision` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `run_mode` varchar(32) NOT NULL,
  `snapshot_at` datetime(3) NOT NULL,
  `source_fingerprint` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `source_resource_count` bigint unsigned NOT NULL DEFAULT 0,
  `mapped_resource_count` bigint unsigned NOT NULL DEFAULT 0,
  `blocking_issue_count` bigint unsigned NOT NULL DEFAULT 0,
  `warning_issue_count` bigint unsigned NOT NULL DEFAULT 0,
  `started_at` datetime(3) DEFAULT NULL,
  `finished_at` datetime(3) DEFAULT NULL,
  `failure_code` varchar(128) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `failure_detail` varchar(1000) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'PLANNED',
  `created_by` bigint unsigned NOT NULL DEFAULT 0,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_by` bigint unsigned NOT NULL DEFAULT 0,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  `deleted_at` datetime(3) DEFAULT NULL,
  `version` int unsigned NOT NULL DEFAULT 0,
  `remark` varchar(500) DEFAULT NULL,
  `active_tenant_id` bigint unsigned GENERATED ALWAYS AS (
    CASE
      WHEN `deleted` = 0 AND `status` IN ('VALIDATING', 'BACKFILLING', 'VERIFYING')
      THEN `tenant_id`
      ELSE NULL
    END
  ) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_org_migration_run_public_id` (`tenant_id`, `public_id`, `deleted`),
  UNIQUE KEY `uk_org_migration_run_active_tenant` (`active_tenant_id`),
  KEY `idx_org_migration_run_scope` (
    `tenant_id`, `migration_scope_id`, `status`, `created_at`, `deleted`
  ),
  CONSTRAINT `chk_org_migration_run_mode` CHECK (
    `run_mode` IN ('VALIDATE', 'APPLY', 'VERIFY')
  ),
  CONSTRAINT `chk_org_migration_run_status` CHECK (
    `status` IN (
      'PLANNED', 'VALIDATING', 'BACKFILLING', 'VERIFYING',
      'SUCCEEDED', 'BLOCKED', 'FAILED', 'CANCELLED'
    )
  ),
  CONSTRAINT `chk_org_migration_run_period` CHECK (
    `finished_at` IS NULL OR `started_at` IS NOT NULL AND `finished_at` >= `started_at`
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Auditable and tenant-serialized organization migration run';

CREATE TABLE IF NOT EXISTS `org_migration_resource_map` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `public_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `migration_scope_id` bigint unsigned NOT NULL,
  `source_namespace` varchar(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `source_resource_type` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `source_resource_key` varchar(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `target_resource_type` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `target_organization_id` bigint unsigned DEFAULT NULL,
  `target_organization_key` bigint unsigned GENERATED ALWAYS AS (
    COALESCE(`target_organization_id`, 0)
  ) STORED,
  `target_resource_id` bigint unsigned DEFAULT NULL,
  `target_public_id` char(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `planned_resource_code` varchar(128) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `mapping_origin` varchar(32) NOT NULL DEFAULT 'GENERATED',
  `managed_target` tinyint(1) NOT NULL DEFAULT 1,
  `source_fingerprint` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `target_fingerprint` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `target_version` int unsigned DEFAULT NULL,
  `last_run_id` bigint unsigned DEFAULT NULL,
  `last_verified_at` datetime(3) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'PLANNED',
  `created_by` bigint unsigned NOT NULL DEFAULT 0,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_by` bigint unsigned NOT NULL DEFAULT 0,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  `deleted_at` datetime(3) DEFAULT NULL,
  `version` int unsigned NOT NULL DEFAULT 0,
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_org_migration_map_public_id` (`tenant_id`, `public_id`, `deleted`),
  UNIQUE KEY `uk_org_migration_map_source_target` (
    `tenant_id`, `source_namespace`, `source_resource_type`, `source_resource_key`,
    `target_resource_type`, `target_organization_key`, `deleted`
  ),
  UNIQUE KEY `uk_org_migration_map_target` (
    `tenant_id`, `target_resource_type`, `target_resource_id`, `deleted`
  ),
  KEY `idx_org_migration_map_scope` (
    `tenant_id`, `migration_scope_id`, `status`, `deleted`
  ),
  KEY `idx_org_migration_map_run` (`tenant_id`, `last_run_id`, `status`, `deleted`),
  CONSTRAINT `chk_org_migration_map_origin` CHECK (
    `mapping_origin` IN ('GENERATED', 'MANUAL')
  ),
  CONSTRAINT `chk_org_migration_map_status` CHECK (
    `status` IN ('PLANNED', 'MAPPED', 'VERIFIED', 'STALE', 'CONFLICT', 'RETIRED')
  ),
  CONSTRAINT `chk_org_migration_map_target_identity` CHECK (
    (`target_resource_id` IS NULL AND `target_public_id` IS NULL)
    OR (`target_resource_id` IS NOT NULL AND `target_public_id` IS NOT NULL)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Idempotent source to organization resource mapping';

CREATE TABLE IF NOT EXISTS `org_migration_issue` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `public_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `migration_scope_id` bigint unsigned NOT NULL,
  `migration_run_id` bigint unsigned NOT NULL,
  `resource_map_id` bigint unsigned DEFAULT NULL,
  `source_resource_type` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `source_resource_key` varchar(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `issue_code` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `severity` varchar(32) NOT NULL,
  `field_name` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '',
  `expected_fingerprint` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `actual_fingerprint` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `detail` varchar(1000) DEFAULT NULL,
  `resolution_code` varchar(128) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `resolved_by` bigint unsigned DEFAULT NULL,
  `resolved_at` datetime(3) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'OPEN',
  `created_by` bigint unsigned NOT NULL DEFAULT 0,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_by` bigint unsigned NOT NULL DEFAULT 0,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  `deleted_at` datetime(3) DEFAULT NULL,
  `version` int unsigned NOT NULL DEFAULT 0,
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_org_migration_issue_public_id` (`tenant_id`, `public_id`, `deleted`),
  UNIQUE KEY `uk_org_migration_issue_identity` (
    `tenant_id`, `migration_run_id`, `source_resource_type`, `source_resource_key`,
    `issue_code`, `field_name`, `deleted`
  ),
  KEY `idx_org_migration_issue_run` (
    `tenant_id`, `migration_run_id`, `severity`, `status`, `deleted`
  ),
  KEY `idx_org_migration_issue_scope` (
    `tenant_id`, `migration_scope_id`, `source_resource_type`, `source_resource_key`, `deleted`
  ),
  CONSTRAINT `chk_org_migration_issue_severity` CHECK (
    `severity` IN ('INFO', 'WARNING', 'BLOCKING')
  ),
  CONSTRAINT `chk_org_migration_issue_status` CHECK (
    `status` IN ('OPEN', 'RESOLVED', 'WAIVED')
  ),
  CONSTRAINT `chk_org_migration_issue_resolution` CHECK (
    (`status` = 'OPEN' AND `resolved_at` IS NULL)
    OR (`status` IN ('RESOLVED', 'WAIVED') AND `resolved_at` IS NOT NULL)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Sanitized migration validation and difference report issue';
