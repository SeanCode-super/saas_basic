CREATE TABLE IF NOT EXISTS `file_lifecycle_policy` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `policy_code` varchar(64) NOT NULL,
  `policy_name` varchar(128) NOT NULL,
  `file_scope` varchar(64) NOT NULL DEFAULT 'GENERAL',
  `retention_days` int NOT NULL DEFAULT 180,
  `archive_after_days` int NOT NULL DEFAULT 30,
  `delete_after_days` int NOT NULL DEFAULT 365,
  `deduplicate_enabled` tinyint(1) NOT NULL DEFAULT 1,
  `version_retention_count` int NOT NULL DEFAULT 5,
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `remark` varchar(500) DEFAULT NULL,
  `created_by` bigint unsigned NOT NULL DEFAULT 0,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_by` bigint unsigned NOT NULL DEFAULT 0,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  `deleted_at` datetime(3) DEFAULT NULL,
  `version` int unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_lifecycle_policy` (`tenant_id`, `policy_code`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件生命周期策略';

INSERT INTO `file_lifecycle_policy` (
  `tenant_id`, `policy_code`, `policy_name`, `file_scope`, `retention_days`, `archive_after_days`,
  `delete_after_days`, `deduplicate_enabled`, `version_retention_count`, `status`, `remark`, `created_by`, `updated_by`, `deleted`
)
SELECT 1, 'default_archive_policy', '默认归档策略', 'GENERAL', 180, 30, 365, 1, 5, 'ENABLED', '文件中心默认生命周期策略', 0, 0, 0
WHERE NOT EXISTS (
  SELECT 1 FROM `file_lifecycle_policy` WHERE `tenant_id` = 1 AND `policy_code` = 'default_archive_policy' AND `deleted` = 0
);

INSERT INTO `file_access_log` (
  `tenant_id`, `file_id`, `access_type`, `operator_user_id`, `operator_ip`, `success`, `occurred_at`, `remark`
)
SELECT 1, fo.`id`, 'PREVIEW', 1, '127.0.0.1', 1, NOW(3), '初始化访问日志'
FROM `file_object` fo
WHERE fo.`tenant_id` = 1 AND fo.`deleted` = 0
  AND NOT EXISTS (SELECT 1 FROM `file_access_log` fal WHERE fal.`tenant_id` = 1 AND fal.`file_id` = fo.`id`)
LIMIT 1;

INSERT INTO `sched_job_alarm` (
  `tenant_id`, `job_id`, `alarm_code`, `alarm_name`, `channel_type`, `trigger_rule`, `receiver_json`,
  `template_code`, `silence_minutes`, `status`, `remark`, `created_by`, `updated_by`, `deleted`
)
SELECT 1, sj.`id`, CONCAT(sj.`job_code`, '_failure'), CONCAT(sj.`job_name`, '失败告警'), 'WEBHOOK', 'ON_FAILURE',
       JSON_OBJECT('webhook', 'https://ops.example.com/hooks/scheduler'), 'scheduler_failure', 10, 'ENABLED',
       '初始化调度告警规则', 0, 0, 0
FROM `sched_job` sj
WHERE sj.`tenant_id` = 1 AND sj.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `sched_job_alarm` sja
    WHERE sja.`tenant_id` = 1 AND sja.`job_id` = sj.`id` AND sja.`alarm_code` = CONCAT(sj.`job_code`, '_failure') AND sja.`deleted` = 0
  )
LIMIT 1;
