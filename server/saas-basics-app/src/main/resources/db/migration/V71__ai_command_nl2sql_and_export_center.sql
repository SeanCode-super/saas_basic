-- =====================================================================
-- V71: Enterprise AI Command Router, NL2SQL History, and Data Export Center
-- =====================================================================
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- 1. AI Command & NL2SQL Execution History
CREATE TABLE IF NOT EXISTS `ai_command_history` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL DEFAULT 1,
  `user_id` BIGINT NOT NULL DEFAULT 1,
  `username` VARCHAR(64) NOT NULL DEFAULT 'system',
  `raw_command` VARCHAR(1000) NOT NULL,
  `intent_code` VARCHAR(64) NOT NULL,
  `confidence` DECIMAL(5,4) NOT NULL DEFAULT 0.9000,
  `generated_sql` TEXT NULL,
  `result_summary` VARCHAR(1000) NULL,
  `row_count` INT NOT NULL DEFAULT 0,
  `latency_ms` BIGINT NOT NULL DEFAULT 0,
  `status` VARCHAR(32) NOT NULL DEFAULT 'COMPLETED',
  `remark` VARCHAR(500) NULL,
  `created_by` BIGINT NOT NULL DEFAULT 0,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_by` BIGINT NOT NULL DEFAULT 0,
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  INDEX `idx_ai_cmd_hist_tenant` (`tenant_id`, `user_id`, `created_at`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Command & NL2SQL History';

-- 2. Enterprise Async Export Tasks & Download Center Ledger
CREATE TABLE IF NOT EXISTS `sys_export_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL DEFAULT 1,
  `task_no` VARCHAR(64) NOT NULL,
  `task_name` VARCHAR(200) NOT NULL,
  `source_type` VARCHAR(32) NOT NULL DEFAULT 'DATASET',
  `source_code` VARCHAR(128) NOT NULL,
  `export_format` VARCHAR(32) NOT NULL DEFAULT 'XLSX',
  `columns_json` TEXT NULL,
  `query_params_json` TEXT NULL,
  `watermark_text` VARCHAR(255) NULL,
  `trace_code` VARCHAR(64) NOT NULL,
  `desensitized` TINYINT(1) NOT NULL DEFAULT 1,
  `total_rows` BIGINT NOT NULL DEFAULT 0,
  `processed_rows` BIGINT NOT NULL DEFAULT 0,
  `progress_percent` INT NOT NULL DEFAULT 0,
  `file_size` BIGINT NOT NULL DEFAULT 0,
  `object_key` VARCHAR(512) NULL,
  `file_sha256` VARCHAR(64) NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  `error_message` VARCHAR(1000) NULL,
  `expire_at` DATETIME(3) NULL,
  `operator_user_id` BIGINT NOT NULL DEFAULT 1,
  `operator_name` VARCHAR(64) NOT NULL DEFAULT 'system',
  `remark` VARCHAR(500) NULL,
  `created_by` BIGINT NOT NULL DEFAULT 0,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_by` BIGINT NOT NULL DEFAULT 0,
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_export_task_no` (`tenant_id`, `task_no`, (CASE WHEN `deleted` = 0 THEN 0 ELSE NULL END)),
  INDEX `idx_export_task_user` (`tenant_id`, `operator_user_id`, `status`, `created_at`, `deleted`),
  INDEX `idx_export_trace_code` (`trace_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Enterprise Data Export Queue and Download Center';

-- 3. Saved Custom Export Field Templates
CREATE TABLE IF NOT EXISTS `sys_export_template` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL DEFAULT 1,
  `template_code` VARCHAR(64) NOT NULL,
  `template_name` VARCHAR(128) NOT NULL,
  `source_code` VARCHAR(128) NOT NULL,
  `fields_json` TEXT NOT NULL,
  `default_format` VARCHAR(32) NOT NULL DEFAULT 'XLSX',
  `include_watermark` TINYINT(1) NOT NULL DEFAULT 1,
  `desensitize_enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
  `remark` VARCHAR(500) NULL,
  `created_by` BIGINT NOT NULL DEFAULT 0,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_by` BIGINT NOT NULL DEFAULT 0,
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_export_tpl_code` (`tenant_id`, `template_code`, (CASE WHEN `deleted` = 0 THEN 0 ELSE NULL END))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Saved Custom Export Templates';

-- 4. Seed Default Prompt Assets for NL2SQL & AI App Architect
INSERT INTO `ai_prompt` (
  `tenant_id`, `prompt_code`, `prompt_name`, `category`, `active_version`, `status`, `remark`
) VALUES
(
  1, 'nl2sql-analyst', 'NL2SQL 数据分析与洞察专家', 'GENERAL', 1, 'ENABLED',
  '自然语言转多租户安全 SELECT SQL 与图表洞察推荐提示词'
),
(
  1, 'ai-app-architect', 'AI 业务模型与动态表单架构师', 'CODEGEN', 1, 'ENABLED',
  '自然语言描述业务场景推导动态实体模型、字段类型、控件类型与示例数据提示词'
)
ON DUPLICATE KEY UPDATE `prompt_name` = VALUES(`prompt_name`);

INSERT INTO `ai_prompt_version` (
  `tenant_id`, `prompt_id`, `version_no`, `system_prompt`, `user_template`,
  `variables_json`, `model_hint`, `changelog`, `is_published`
)
SELECT
  1, p.`id`, 1,
  '你是企业级多租户数据分析专家。必须仅输出单条安全 SELECT SQL（严禁 DDL/DML/UNION/子查询越权），所有查询表必须包含 tenant_id = {{tenantId}} 与 deleted = 0 条件，并以 JSON 格式返回 sql、chartType(BAR/LINE/PIE/STAT/TABLE)、chartTitle、xAxisField、seriesField、insights 与 followUps。',
  '可查询 Schema：\n{{schemas}}\n\n业务问题：{{question}}',
  '["tenantId","schemas","question"]',
  'deepseek-chat',
  'v1 initial release',
  1
FROM `ai_prompt` p
WHERE p.`prompt_code` = 'nl2sql-analyst' AND p.`deleted` = 0
AND NOT EXISTS (
  SELECT 1 FROM `ai_prompt_version` v WHERE v.`prompt_id` = p.`id` AND v.`version_no` = 1 AND v.`deleted` = 0
);

INSERT INTO `ai_prompt_version` (
  `tenant_id`, `prompt_id`, `version_no`, `system_prompt`, `user_template`,
  `variables_json`, `model_hint`, `changelog`, `is_published`
)
SELECT
  1, p.`id`, 1,
  '你是企业级低代码模型架构师。根据用户的自然语言描述，设计业务模型 JSON（包含 modelCode、modelName、category、icon、description、fields 数组和 sampleData）。fieldCode 必须为小写蛇形英文标识，columnType 兼容 MySQL 8，componentType 仅使用 input/textarea/number/select/date/datetime。',
  '业务需求描述：{{prompt}}',
  '["prompt"]',
  'deepseek-chat',
  'v1 initial release',
  1
FROM `ai_prompt` p
WHERE p.`prompt_code` = 'ai-app-architect' AND p.`deleted` = 0
AND NOT EXISTS (
  SELECT 1 FROM `ai_prompt_version` v WHERE v.`prompt_id` = p.`id` AND v.`version_no` = 1 AND v.`deleted` = 0
);

-- 5. Seed Default Export Templates for Tenant 1
INSERT INTO `sys_export_template` (
  `tenant_id`, `template_code`, `template_name`, `source_code`, `fields_json`,
  `default_format`, `include_watermark`, `desensitize_enabled`, `status`, `remark`
) VALUES
(
  1, 'crm_customer_executive', '客户档案高管脱敏简表', 'crm_customer',
  '["customer_code","name","industry","level","mobile","email","deal_status","owner_user_name","created_at"]',
  'XLSX', 1, 1, 'ENABLED', '默认脱敏手机号与邮箱并附带 45 度防伪溯源水印'
),
(
  1, 'crm_receivable_finance', '财务回款核销对账模板', 'crm_receivable',
  '["receivable_no","contract_name","customer_name","price","return_time","pay_type","status"]',
  'XLSX', 1, 1, 'ENABLED', '财务回款月度对账专用导出字段集'
)
ON DUPLICATE KEY UPDATE `template_name` = VALUES(`template_name`);

-- 6. Seed Console Menus: AI Data Copilot (/ai/data-copilot) and Download Center (/file/download-center)
INSERT INTO `iam_menu` (
  `tenant_id`, `parent_id`, `menu_type`, `menu_code`, `app_code`, `menu_name`,
  `route_path`, `component_path`, `permission_code`, `icon`, `sort_no`,
  `status`, `visible`, `keep_alive`
)
SELECT
  1,
  COALESCE((SELECT p.`id` FROM (SELECT `id` FROM `iam_menu` WHERE `menu_code` = 'system' AND `tenant_id` = 1 AND `deleted` = 0 LIMIT 1) p), 8),
  'MENU', 'system_ai_copilot', 'system', 'AI 数据副驾驶',
  '/ai/data-copilot', '@/views/ai/AiDataCopilotView.vue', 'system:config:query', 'DataAnalysis', 69,
  'ENABLED', 1, 1
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM `iam_menu` WHERE `menu_code` = 'system_ai_copilot' AND `tenant_id` = 1 AND `deleted` = 0
);

INSERT INTO `iam_menu` (
  `tenant_id`, `parent_id`, `menu_type`, `menu_code`, `app_code`, `menu_name`,
  `route_path`, `component_path`, `permission_code`, `icon`, `sort_no`,
  `status`, `visible`, `keep_alive`
)
SELECT
  1,
  COALESCE((SELECT p.`id` FROM (SELECT `id` FROM `iam_menu` WHERE `menu_code` = 'file' AND `tenant_id` = 1 AND `deleted` = 0 LIMIT 1) p), 10),
  'MENU', 'file_download_center', 'file', '数据导出与下载中心',
  '/file/download-center', '@/views/export/DownloadCenterView.vue', 'file:object:query', 'Download', 55,
  'ENABLED', 1, 1
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM `iam_menu` WHERE `menu_code` = 'file_download_center' AND `tenant_id` = 1 AND `deleted` = 0
);

INSERT IGNORE INTO `iam_role_menu` (`tenant_id`, `role_id`, `menu_id`)
SELECT 1, 1, `id` FROM `iam_menu` WHERE `menu_code` IN ('system_ai_copilot', 'file_download_center') AND `tenant_id` = 1 AND `deleted` = 0;
