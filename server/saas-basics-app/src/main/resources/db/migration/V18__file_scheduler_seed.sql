INSERT INTO `file_storage` (
  `id`, `tenant_id`, `storage_code`, `storage_name`, `storage_type`, `endpoint`, `bucket_default`,
  `public_base_url`, `status`, `remark`, `created_by`, `updated_by`, `deleted`
)
SELECT 1, 1, 'main_minio', '主存储池', 'MINIO', 'http://127.0.0.1:9000', 'saas-basics',
       'http://127.0.0.1:9000/saas-basics', 'ENABLED', '文件中心默认存储器', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM `file_storage` WHERE `tenant_id` = 1 AND `storage_code` = 'main_minio' AND `deleted` = 0);

INSERT INTO `file_bucket` (
  `id`, `tenant_id`, `storage_id`, `bucket_code`, `bucket_name`, `bucket_type`, `bucket_path`,
  `status`, `remark`, `created_by`, `updated_by`, `deleted`
)
SELECT 1, 1, 1, 'default_bucket', '默认文件桶', 'PRIVATE', '/platform/default',
       'ENABLED', '文件中心默认桶', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM `file_bucket` WHERE `tenant_id` = 1 AND `bucket_code` = 'default_bucket' AND `deleted` = 0);

INSERT INTO `file_object` (
  `id`, `tenant_id`, `bucket_id`, `object_key`, `file_name`, `file_ext`, `content_type`, `file_size`,
  `storage_path`, `visibility`, `biz_type`, `version_no`, `status`, `remark`, `created_by`, `updated_by`, `deleted`
)
SELECT 1, 1, 1, 'tenant/contracts/2026/platform-contract.pdf', '平台租户合同.pdf', 'pdf', 'application/pdf', 524288,
       '/platform/default/tenant/contracts/2026/platform-contract.pdf', 'PRIVATE', 'TENANT_CONTRACT', 1, 'ACTIVE', '示例文件对象', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM `file_object` WHERE `id` = 1);

INSERT INTO `file_object` (
  `id`, `tenant_id`, `bucket_id`, `object_key`, `file_name`, `file_ext`, `content_type`, `file_size`,
  `storage_path`, `visibility`, `biz_type`, `version_no`, `status`, `remark`, `created_by`, `updated_by`, `deleted`
)
SELECT 2, 1, 1, 'audit/export/2026/audit-report.csv', '审计导出.csv', 'csv', 'text/csv', 20480,
       '/platform/default/audit/export/2026/audit-report.csv', 'PRIVATE', 'AUDIT_EXPORT', 1, 'ACTIVE', '示例导出对象', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM `file_object` WHERE `id` = 2);

INSERT INTO `file_upload_session` (
  `id`, `tenant_id`, `bucket_id`, `storage_id`, `session_code`, `object_key`, `file_name`, `file_size`,
  `upload_mode`, `part_count`, `owner_user_id`, `status`, `expire_at`, `created_by`, `updated_by`, `deleted`
)
SELECT 1, 1, 1, 1, 'UP-PLATFORM-DEMO-01', 'manual/upload/demo.png', '门户背景图.png', 1048576,
       'SINGLE', 1, 1, 'INIT', DATE_ADD(NOW(3), INTERVAL 2 HOUR), 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM `file_upload_session` WHERE `tenant_id` = 1 AND `session_code` = 'UP-PLATFORM-DEMO-01' AND `deleted` = 0);

INSERT INTO `sched_executor` (
  `id`, `tenant_id`, `executor_code`, `executor_name`, `executor_type`, `address`, `status`, `heartbeat_at`,
  `remark`, `created_by`, `updated_by`, `deleted`
)
SELECT 1, 1, 'local-default', '本地执行器', 'LOCAL', '127.0.0.1', 'ENABLED', NOW(3),
       '调度中心默认执行器', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM `sched_executor` WHERE `tenant_id` = 1 AND `executor_code` = 'local-default' AND `deleted` = 0);

INSERT INTO `sched_job` (
  `id`, `tenant_id`, `job_code`, `job_name`, `job_type`, `executor_id`, `handler_name`,
  `concurrency_policy`, `misfire_policy`, `retry_times`, `timeout_seconds`, `status`,
  `remark`, `created_by`, `updated_by`, `deleted`
)
SELECT 1, 1, 'tenantQuotaRefresh', '租户配额刷新', 'JAVA', 1, 'tenantQuotaRefreshHandler',
       'SERIAL', 'LAST', 1, 60, 'ENABLED',
       '默认调度任务', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM `sched_job` WHERE `tenant_id` = 1 AND `job_code` = 'tenantQuotaRefresh' AND `deleted` = 0);

INSERT INTO `sched_job` (
  `id`, `tenant_id`, `job_code`, `job_name`, `job_type`, `executor_id`, `handler_name`,
  `concurrency_policy`, `misfire_policy`, `retry_times`, `timeout_seconds`, `status`,
  `remark`, `created_by`, `updated_by`, `deleted`
)
SELECT 2, 1, 'auditArchive', '审计归档', 'JAVA', 1, 'auditArchiveHandler',
       'SERIAL', 'LAST', 2, 120, 'DISABLED',
       '示例审计任务', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM `sched_job` WHERE `tenant_id` = 1 AND `job_code` = 'auditArchive' AND `deleted` = 0);

INSERT INTO `sched_job_log` (
  `id`, `tenant_id`, `job_id`, `trigger_id`, `execution_no`, `executor_id`,
  `started_at`, `finished_at`, `run_status`, `success`, `retry_count`, `error_message`, `trace_id`
)
SELECT 1, 1, 1, 0, 'EXEC-20260311-0001', 1,
       DATE_SUB(NOW(3), INTERVAL 5 MINUTE), DATE_SUB(NOW(3), INTERVAL 4 MINUTE), 'SUCCESS', 1, 0, NULL, 'trace-sched-0001'
WHERE NOT EXISTS (SELECT 1 FROM `sched_job_log` WHERE `tenant_id` = 1 AND `execution_no` = 'EXEC-20260311-0001');
