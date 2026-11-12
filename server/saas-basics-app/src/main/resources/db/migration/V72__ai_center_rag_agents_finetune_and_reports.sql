-- =====================================================================
-- V72: Standalone AI Center Directory, Export Approval & Scheduled Reports,
--      Enterprise RAG Knowledge Base, Agent Studio & Fine-Tune Workbench
-- =====================================================================
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- 1. Export Approval Columns on sys_export_task
ALTER TABLE `sys_export_task`
  ADD COLUMN `approval_status` VARCHAR(32) NOT NULL DEFAULT 'NOT_REQUIRED' AFTER `status`,
  ADD COLUMN `approved_by` BIGINT NULL AFTER `approval_status`,
  ADD COLUMN `approved_by_name` VARCHAR(64) NULL AFTER `approved_by`,
  ADD COLUMN `approved_at` DATETIME(3) NULL AFTER `approved_by_name`,
  ADD COLUMN `approve_reason` VARCHAR(500) NULL AFTER `approved_at`;

-- 2. Scheduled Automatic Reports Ledger (L2)
CREATE TABLE IF NOT EXISTS `sys_report_schedule` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL DEFAULT 1,
  `schedule_code` VARCHAR(64) NOT NULL,
  `schedule_name` VARCHAR(160) NOT NULL,
  `source_code` VARCHAR(128) NOT NULL,
  `export_format` VARCHAR(32) NOT NULL DEFAULT 'XLSX',
  `fields_json` TEXT NULL,
  `filters_json` TEXT NULL,
  `frequency` VARCHAR(32) NOT NULL DEFAULT 'WEEKLY',
  `cron_expr` VARCHAR(64) NOT NULL DEFAULT '0 0 8 ? * MON',
  `run_hour` INT NOT NULL DEFAULT 8,
  `run_minute` INT NOT NULL DEFAULT 0,
  `include_watermark` TINYINT(1) NOT NULL DEFAULT 1,
  `desensitize_enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `recipient_user_ids` VARCHAR(500) NOT NULL DEFAULT '1',
  `last_run_at` DATETIME(3) NULL,
  `next_run_at` DATETIME(3) NULL,
  `last_task_id` BIGINT NULL,
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
  UNIQUE KEY `uk_report_sched_code` (`tenant_id`, `schedule_code`, (CASE WHEN `deleted` = 0 THEN 0 ELSE NULL END))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Enterprise Scheduled Reports';

INSERT INTO `sys_report_schedule` (
  `tenant_id`, `schedule_code`, `schedule_name`, `source_code`, `export_format`,
  `fields_json`, `frequency`, `cron_expr`, `run_hour`, `run_minute`,
  `include_watermark`, `desensitize_enabled`, `recipient_user_ids`, `next_run_at`, `status`, `remark`
) VALUES
(
  1, 'crm_weekly_exec_report', '每周 CRM 高管脱敏客户分析周报', 'crm_customer', 'PDF',
  '["customer_code","name","industry","level","mobile","deal_status","owner_user_name","created_at"]',
  'WEEKLY', '0 0 8 ? * MON', 8, 0,
  1, 1, '1', DATE_ADD(NOW(), INTERVAL 7 DAY), 'ENABLED',
  '每周一早 8 点自动生成带防伪水印的 PDF 分析周报并推送至下载中心与站内通知'
),
(
  1, 'finance_daily_receivable', '每日财务回款核销对账单', 'crm_receivable', 'XLSX',
  '["receivable_no","contract_name","customer_name","price","return_time","pay_type","status"]',
  'DAILY', '0 30 18 * * ?', 18, 30,
  1, 1, '1', DATE_ADD(NOW(), INTERVAL 1 DAY), 'ENABLED',
  '每日 18:30 自动汇总当日回款台账生成 Excel 归档'
)
ON DUPLICATE KEY UPDATE `schedule_name` = VALUES(`schedule_name`);

-- 3. Enterprise RAG Knowledge Base Tables (Phase 2)
CREATE TABLE IF NOT EXISTS `ai_knowledge_base` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL DEFAULT 1,
  `kb_code` VARCHAR(64) NOT NULL,
  `kb_name` VARCHAR(128) NOT NULL,
  `category` VARCHAR(64) NOT NULL DEFAULT 'COMPLIANCE',
  `embedding_model` VARCHAR(128) NOT NULL DEFAULT 'hybrid-bm25-bge',
  `chunk_size` INT NOT NULL DEFAULT 480,
  `chunk_overlap` INT NOT NULL DEFAULT 80,
  `doc_count` INT NOT NULL DEFAULT 0,
  `chunk_count` INT NOT NULL DEFAULT 0,
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
  UNIQUE KEY `uk_ai_kb_code` (`tenant_id`, `kb_code`, (CASE WHEN `deleted` = 0 THEN 0 ELSE NULL END))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Enterprise AI Knowledge Base';

CREATE TABLE IF NOT EXISTS `ai_knowledge_doc` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL DEFAULT 1,
  `kb_id` BIGINT NOT NULL,
  `doc_name` VARCHAR(255) NOT NULL,
  `file_type` VARCHAR(32) NOT NULL DEFAULT 'TXT',
  `file_size` BIGINT NOT NULL DEFAULT 0,
  `object_key` VARCHAR(512) NULL,
  `parse_status` VARCHAR(32) NOT NULL DEFAULT 'INDEXED',
  `chunk_count` INT NOT NULL DEFAULT 0,
  `char_count` INT NOT NULL DEFAULT 0,
  `summary` VARCHAR(1000) NULL,
  `raw_text` LONGTEXT NULL,
  `created_by` BIGINT NOT NULL DEFAULT 0,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_by` BIGINT NOT NULL DEFAULT 0,
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  INDEX `idx_ai_kb_doc_kb` (`tenant_id`, `kb_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Enterprise AI Knowledge Documents';

CREATE TABLE IF NOT EXISTS `ai_knowledge_chunk` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL DEFAULT 1,
  `kb_id` BIGINT NOT NULL,
  `doc_id` BIGINT NOT NULL,
  `chunk_index` INT NOT NULL DEFAULT 0,
  `page_no` INT NOT NULL DEFAULT 1,
  `section_title` VARCHAR(255) NULL,
  `content` TEXT NOT NULL,
  `token_count` INT NOT NULL DEFAULT 0,
  `embedding_json` LONGTEXT NULL,
  `keywords` VARCHAR(500) NULL,
  `created_by` BIGINT NOT NULL DEFAULT 0,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_by` BIGINT NOT NULL DEFAULT 0,
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  INDEX `idx_ai_kb_chunk_kb` (`tenant_id`, `kb_id`, `doc_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Enterprise AI Knowledge Chunks';

-- Seed Starter KBs, Docs and Chunks
INSERT INTO `ai_knowledge_base` (
  `tenant_id`, `kb_code`, `kb_name`, `category`, `embedding_model`,
  `chunk_size`, `chunk_overlap`, `doc_count`, `chunk_count`, `status`, `remark`
) VALUES
(
  1, 'corp-compliance-kb', '企业法务合同与风控合规库', 'COMPLIANCE', 'hybrid-bm25-bge',
  480, 80, 2, 4, 'ENABLED',
  '沉淀企业商务合同审核红线、付款帐期上限、违约责任与采购招投标风控标准'
),
(
  1, 'enterprise-ops-kb', '天枢云底座产品与交付制度库', 'OPERATIONS', 'hybrid-bm25-bge',
  480, 80, 1, 2, 'ENABLED',
  '包含多租户隔离规范、敏感数据导出脱敏合规指引及实施交付 SOP'
)
ON DUPLICATE KEY UPDATE `kb_name` = VALUES(`kb_name`);

INSERT INTO `ai_knowledge_doc` (
  `tenant_id`, `kb_id`, `doc_name`, `file_type`, `file_size`, `parse_status`, `chunk_count`, `char_count`, `summary`, `raw_text`
)
SELECT 1, kb.`id`, 'CRM商务合同审查与风控红线手册.md', 'MD', 1680, 'INDEXED', 2, 420,
       '明确企业商务合同付款条款、违约金比例、知识产权归属与数据保密红线',
       '# 商务合同审查红线\n1. 预付款与验收款条款：单笔合同首付款比例不得低于 30%，尾款账期原则上不超过验收后 45 个工作日，严禁出现无条件无限期垫资条款。\n2. 违约赔偿上限：我方承担的累计违约赔偿责任总额不得超过合同已收款总额的 100%，禁止签署连带无限责任赔偿。\n3. 知识产权与保密：定制开发成果知识产权在付清全款后转移，涉密数据严禁未经脱敏通过公共渠道传输。'
FROM `ai_knowledge_base` kb
WHERE kb.`kb_code` = 'corp-compliance-kb' AND kb.`tenant_id` = 1 AND kb.`deleted` = 0
AND NOT EXISTS (
  SELECT 1 FROM `ai_knowledge_doc` d WHERE d.`kb_id` = kb.`id` AND d.`doc_name` = 'CRM商务合同审查与风控红线手册.md' AND d.`deleted` = 0
);

INSERT INTO `ai_knowledge_doc` (
  `tenant_id`, `kb_id`, `doc_name`, `file_type`, `file_size`, `parse_status`, `chunk_count`, `char_count`, `summary`, `raw_text`
)
SELECT 1, kb.`id`, 'ERP采购招投标及供应商准入标准.md', 'MD', 1240, 'INDEXED', 2, 310,
       '规范单笔大额采购三方比价、供应商资质核验及交货延期罚则',
       '# 采购招投标风控规范\n1. 超过 10 万元的采购订单必须经过三方比价并附带合格供应商准入评估单。\n2. 供应商延期交货超过 7 日的，每日按未交货金额 0.5% 计提违约金，且采购方有权单方解除合同。\n3. 所有采购对账导出件需附带操作人轨迹水印。'
FROM `ai_knowledge_base` kb
WHERE kb.`kb_code` = 'corp-compliance-kb' AND kb.`tenant_id` = 1 AND kb.`deleted` = 0
AND NOT EXISTS (
  SELECT 1 FROM `ai_knowledge_doc` d WHERE d.`kb_id` = kb.`id` AND d.`doc_name` = 'ERP采购招投标及供应商准入标准.md' AND d.`deleted` = 0
);

INSERT INTO `ai_knowledge_doc` (
  `tenant_id`, `kb_id`, `doc_name`, `file_type`, `file_size`, `parse_status`, `chunk_count`, `char_count`, `summary`, `raw_text`
)
SELECT 1, kb.`id`, 'SaaS多租户数据安全与导出治理白皮书.md', 'MD', 1420, 'INDEXED', 2, 340,
       '规定租户隔离、PII自动脱敏、万行导出审批与水印追溯机制',
       '# SaaS 多租户数据安全制度\n1. 所有 AI 查数（NL2SQL）与批量导出强制执行 tenant_id 行级租户隔离及角色数据权限过滤。\n2. 手机号、邮箱、身份证等 PII 字段默认执行脱敏处理；关闭脱敏或单次导出超过 10000 行须由管理员审批通过。\n3. 所有导出文件统一加注操作人与 TraceCode 防伪水印并留存审计轨迹。'
FROM `ai_knowledge_base` kb
WHERE kb.`kb_code` = 'enterprise-ops-kb' AND kb.`tenant_id` = 1 AND kb.`deleted` = 0
AND NOT EXISTS (
  SELECT 1 FROM `ai_knowledge_doc` d WHERE d.`kb_id` = kb.`id` AND d.`doc_name` = 'SaaS多租户数据安全与导出治理白皮书.md' AND d.`deleted` = 0
);

INSERT INTO `ai_knowledge_chunk` (`tenant_id`, `kb_id`, `doc_id`, `chunk_index`, `page_no`, `section_title`, `content`, `token_count`, `keywords`)
SELECT 1, d.`kb_id`, d.`id`, 0, 1, '付款账期与违约赔偿上限',
       '1. 预付款与验收款条款：单笔合同首付款比例不得低于 30%，尾款账期原则上不超过验收后 45 个工作日，严禁出现无条件无限期垫资条款。2. 违约赔偿上限：我方承担的累计违约赔偿责任总额不得超过合同已收款总额的 100%，禁止签署连带无限责任赔偿。',
       132, '首付款,账期,违约赔偿,无限责任,合同红线'
FROM `ai_knowledge_doc` d
WHERE d.`doc_name` = 'CRM商务合同审查与风控红线手册.md' AND d.`deleted` = 0
AND NOT EXISTS (SELECT 1 FROM `ai_knowledge_chunk` c WHERE c.`doc_id` = d.`id` AND c.`chunk_index` = 0 AND c.`deleted` = 0);

INSERT INTO `ai_knowledge_chunk` (`tenant_id`, `kb_id`, `doc_id`, `chunk_index`, `page_no`, `section_title`, `content`, `token_count`, `keywords`)
SELECT 1, d.`kb_id`, d.`id`, 1, 2, '知识产权与保密数据传输要求',
       '3. 知识产权与保密：定制开发成果知识产权在付清全款后转移，严禁约定未收款先移交全部源码或永久免费授权；涉密客户数据严禁未经脱敏通过公共渠道传输。',
       86, '知识产权,源码,保密,脱敏'
FROM `ai_knowledge_doc` d
WHERE d.`doc_name` = 'CRM商务合同审查与风控红线手册.md' AND d.`deleted` = 0
AND NOT EXISTS (SELECT 1 FROM `ai_knowledge_chunk` c WHERE c.`doc_id` = d.`id` AND c.`chunk_index` = 1 AND c.`deleted` = 0);

INSERT INTO `ai_knowledge_chunk` (`tenant_id`, `kb_id`, `doc_id`, `chunk_index`, `page_no`, `section_title`, `content`, `token_count`, `keywords`)
SELECT 1, d.`kb_id`, d.`id`, 0, 1, '大额采购比价与供应商罚则',
       '1. 超过 10 万元的采购订单必须经过三方比价并附带合格供应商准入评估单。2. 供应商延期交货超过 7 日的，每日按未交货金额 0.5% 计提违约金，且采购方有权单方解除合同。',
       95, '采购,比价,供应商,延期交货,违约金'
FROM `ai_knowledge_doc` d
WHERE d.`doc_name` = 'ERP采购招投标及供应商准入标准.md' AND d.`deleted` = 0
AND NOT EXISTS (SELECT 1 FROM `ai_knowledge_chunk` c WHERE c.`doc_id` = d.`id` AND c.`chunk_index` = 0 AND c.`deleted` = 0);

INSERT INTO `ai_knowledge_chunk` (`tenant_id`, `kb_id`, `doc_id`, `chunk_index`, `page_no`, `section_title`, `content`, `token_count`, `keywords`)
SELECT 1, d.`kb_id`, d.`id`, 0, 1, '多租户隔离与高危导出审批规则',
       '1. 所有 AI 查数（NL2SQL）与批量导出强制执行 tenant_id 行级租户隔离及角色数据权限过滤。2. 手机号、邮箱等 PII 字段默认执行脱敏处理；关闭脱敏或单次导出超过 10000 行须由管理员审批通过。',
       110, '多租户,NL2SQL,数据权限,脱敏,导出审批,水印'
FROM `ai_knowledge_doc` d
WHERE d.`doc_name` = 'SaaS多租户数据安全与导出治理白皮书.md' AND d.`deleted` = 0
AND NOT EXISTS (SELECT 1 FROM `ai_knowledge_chunk` c WHERE c.`doc_id` = d.`id` AND c.`chunk_index` = 0 AND c.`deleted` = 0);

-- 4. Phase 3: Visual Agent Studio Tables
CREATE TABLE IF NOT EXISTS `ai_agent` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL DEFAULT 1,
  `agent_code` VARCHAR(64) NOT NULL,
  `agent_name` VARCHAR(128) NOT NULL,
  `avatar` VARCHAR(64) NOT NULL DEFAULT '🤖',
  `role_title` VARCHAR(128) NOT NULL,
  `system_prompt` TEXT NOT NULL,
  `model_id` VARCHAR(128) NULL,
  `kb_ids_json` TEXT NULL,
  `tool_codes_json` TEXT NULL,
  `channel_types` VARCHAR(255) NOT NULL DEFAULT 'CONSOLE,COMMAND_PALETTE',
  `temperature` DECIMAL(3,2) NOT NULL DEFAULT 0.30,
  `welcome_message` VARCHAR(500) NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED',
  `run_count` INT NOT NULL DEFAULT 0,
  `remark` VARCHAR(500) NULL,
  `created_by` BIGINT NOT NULL DEFAULT 0,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_by` BIGINT NOT NULL DEFAULT 0,
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_agent_code` (`tenant_id`, `agent_code`, (CASE WHEN `deleted` = 0 THEN 0 ELSE NULL END))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Enterprise Visual AI Agents';

CREATE TABLE IF NOT EXISTS `ai_agent_session` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL DEFAULT 1,
  `agent_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL DEFAULT 1,
  `session_title` VARCHAR(200) NOT NULL,
  `messages_json` LONGTEXT NULL,
  `last_active_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` BIGINT NOT NULL DEFAULT 0,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_by` BIGINT NOT NULL DEFAULT 0,
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  INDEX `idx_ai_agent_sess_agent` (`tenant_id`, `agent_id`, `user_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent Sandbox Conversation Sessions';

INSERT INTO `ai_agent` (
  `tenant_id`, `agent_code`, `agent_name`, `avatar`, `role_title`, `system_prompt`,
  `model_id`, `kb_ids_json`, `tool_codes_json`, `channel_types`, `temperature`, `welcome_message`, `status`, `run_count`, `remark`
) VALUES
(
  1, 'sales-contract-copilot', '商务风控与合同审查专家', '⚖️', '企业法务与商机参谋',
  '你是企业资深法务风控与销售顾问。请结合企业法务知识库中的付款账期、违约金上限及知识产权条款，对客户合同、商机报价和跟进风险给出精准排查建议，并引用制度依据。',
  'deepseek-chat', '[1]', '["crm_query","knowledge_search"]', 'CONSOLE,CRM_DRAWER,COMMAND_PALETTE', 0.25,
  '您好！我是「商务风控与合同审查专家」，已挂载企业法务知识库与 CRM 工具。请发送合同条款或客户跟进问题，我为您实时核查风险。',
  'PUBLISHED', 12, 'CRM 跟进抽屉与知识库合审默认智能体'
),
(
  1, 'ops-data-analyst', '企业经营数据参谋', '📊', '首席 BI 与导出分析师',
  '你是企业经营数据分析专家。擅长将业务问题转为安全只读数据分析结论，识别异常回款、库存预警与高价值客户趋势，并建议可直接导出的分析维度。',
  'deepseek-chat', '[2]', '["nl2sql_query","export_report"]', 'CONSOLE,MOBILE,COMMAND_PALETTE', 0.20,
  '您好！我是「企业经营数据参谋」，可帮您分析回款、库存、采购与经营趋势，并支持生成带水印报表。',
  'PUBLISHED', 8, '已发布至工作台、移动端与全局指令面板'
)
ON DUPLICATE KEY UPDATE `agent_name` = VALUES(`agent_name`);

-- 5. Phase 4: Enterprise AI Fine-Tuning Workbench Tables
CREATE TABLE IF NOT EXISTS `ai_finetune_dataset` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL DEFAULT 1,
  `dataset_code` VARCHAR(64) NOT NULL,
  `dataset_name` VARCHAR(128) NOT NULL,
  `task_type` VARCHAR(32) NOT NULL DEFAULT 'NL2SQL',
  `sample_count` INT NOT NULL DEFAULT 0,
  `approved_count` INT NOT NULL DEFAULT 0,
  `format_type` VARCHAR(32) NOT NULL DEFAULT 'ALPACA',
  `status` VARCHAR(32) NOT NULL DEFAULT 'READY',
  `remark` VARCHAR(500) NULL,
  `created_by` BIGINT NOT NULL DEFAULT 0,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_by` BIGINT NOT NULL DEFAULT 0,
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_ft_ds_code` (`tenant_id`, `dataset_code`, (CASE WHEN `deleted` = 0 THEN 0 ELSE NULL END))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Enterprise AI Fine-tune Datasets';

CREATE TABLE IF NOT EXISTS `ai_finetune_sample` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL DEFAULT 1,
  `dataset_id` BIGINT NOT NULL,
  `instruction` TEXT NOT NULL,
  `input_text` TEXT NULL,
  `output_text` TEXT NOT NULL,
  `source_type` VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
  `quality_score` DECIMAL(3,2) NOT NULL DEFAULT 0.90,
  `review_status` VARCHAR(32) NOT NULL DEFAULT 'APPROVED',
  `desensitized` TINYINT(1) NOT NULL DEFAULT 1,
  `created_by` BIGINT NOT NULL DEFAULT 0,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_by` BIGINT NOT NULL DEFAULT 0,
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  INDEX `idx_ai_ft_sample_ds` (`tenant_id`, `dataset_id`, `review_status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Fine-tune Annotated Samples';

CREATE TABLE IF NOT EXISTS `ai_finetune_job` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL DEFAULT 1,
  `job_code` VARCHAR(64) NOT NULL,
  `job_name` VARCHAR(128) NOT NULL,
  `dataset_id` BIGINT NOT NULL,
  `base_model` VARCHAR(128) NOT NULL DEFAULT 'Qwen2.5-7B-Instruct',
  `tuning_method` VARCHAR(32) NOT NULL DEFAULT 'LORA',
  `epochs` INT NOT NULL DEFAULT 3,
  `learning_rate` VARCHAR(32) NOT NULL DEFAULT '2e-4',
  `lora_rank` INT NOT NULL DEFAULT 16,
  `status` VARCHAR(32) NOT NULL DEFAULT 'COMPLETED',
  `progress_percent` INT NOT NULL DEFAULT 100,
  `train_loss` DECIMAL(6,4) NULL,
  `eval_accuracy` DECIMAL(5,4) NULL,
  `baseline_accuracy` DECIMAL(5,4) NULL,
  `output_model_id` VARCHAR(128) NULL,
  `metrics_json` TEXT NULL,
  `created_by` BIGINT NOT NULL DEFAULT 0,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_by` BIGINT NOT NULL DEFAULT 0,
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_ft_job_code` (`tenant_id`, `job_code`, (CASE WHEN `deleted` = 0 THEN 0 ELSE NULL END))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='LoRA Fine-tuning & A/B Evaluation Jobs';

INSERT INTO `ai_finetune_dataset` (
  `tenant_id`, `dataset_code`, `dataset_name`, `task_type`, `sample_count`, `approved_count`, `format_type`, `status`, `remark`
) VALUES
(
  1, 'nl2sql-sft-v1', '天枢多租户 NL2SQL & 法务问答 SFT 训练集', 'NL2SQL', 3, 3, 'ALPACA', 'READY',
  '从历史高赞 NL2SQL 查询与合规问答自动清洗脱敏沉淀的微调语料包'
)
ON DUPLICATE KEY UPDATE `dataset_name` = VALUES(`dataset_name`);

INSERT INTO `ai_finetune_sample` (
  `tenant_id`, `dataset_id`, `instruction`, `input_text`, `output_text`, `source_type`, `quality_score`, `review_status`, `desensitized`
)
SELECT 1, ds.`id`,
       '将企业经营分析问题转换为多租户安全 SELECT SQL，必须包含 tenant_id 与 deleted = 0',
       '统计各状态财务回款笔数与总金额',
       'SELECT status AS dimension, COUNT(*) AS record_count, COALESCE(SUM(price), 0) AS total_amount FROM crm_receivable WHERE tenant_id = 1 AND deleted = 0 GROUP BY status ORDER BY total_amount DESC LIMIT 50',
       'NL2SQL_HISTORY', 0.98, 'APPROVED', 1
FROM `ai_finetune_dataset` ds
WHERE ds.`dataset_code` = 'nl2sql-sft-v1' AND ds.`tenant_id` = 1 AND ds.`deleted` = 0
AND NOT EXISTS (
  SELECT 1 FROM `ai_finetune_sample` s WHERE s.`dataset_id` = ds.`id` AND s.`input_text` = '统计各状态财务回款笔数与总金额' AND s.`deleted` = 0
);

INSERT INTO `ai_finetune_sample` (
  `tenant_id`, `dataset_id`, `instruction`, `input_text`, `output_text`, `source_type`, `quality_score`, `review_status`, `desensitized`
)
SELECT 1, ds.`id`,
       '将客户跟进与商机阶段分析问题转为只读聚合 SQL',
       '按行业统计 A 级和 B 级客户数量分布',
       'SELECT industry AS dimension, COUNT(*) AS customer_count FROM crm_customer WHERE tenant_id = 1 AND deleted = 0 AND level IN (''A'',''B'') GROUP BY industry ORDER BY customer_count DESC LIMIT 50',
       'NL2SQL_HISTORY', 0.95, 'APPROVED', 1
FROM `ai_finetune_dataset` ds
WHERE ds.`dataset_code` = 'nl2sql-sft-v1' AND ds.`tenant_id` = 1 AND ds.`deleted` = 0
AND NOT EXISTS (
  SELECT 1 FROM `ai_finetune_sample` s WHERE s.`dataset_id` = ds.`id` AND s.`input_text` = '按行业统计 A 级和 B 级客户数量分布' AND s.`deleted` = 0
);

INSERT INTO `ai_finetune_sample` (
  `tenant_id`, `dataset_id`, `instruction`, `input_text`, `output_text`, `source_type`, `quality_score`, `review_status`, `desensitized`
)
SELECT 1, ds.`id`,
       '依据企业商务合同合规手册回答条款风控标准（涉密字段需脱敏）',
       '合同首付款和尾款账期红线分别是多少？',
       '依据《CRM商务合同审查与风控红线手册》，单笔合同首付款比例不得低于 30%，尾款账期原则上不超过验收后 45 个工作日，且严禁签署无条件无限期垫资及无限连带赔偿条款。',
       'RAG_QA', 0.96, 'APPROVED', 1
FROM `ai_finetune_dataset` ds
WHERE ds.`dataset_code` = 'nl2sql-sft-v1' AND ds.`tenant_id` = 1 AND ds.`deleted` = 0
AND NOT EXISTS (
  SELECT 1 FROM `ai_finetune_sample` s WHERE s.`dataset_id` = ds.`id` AND s.`input_text` = '合同首付款和尾款账期红线分别是多少？' AND s.`deleted` = 0
);

INSERT INTO `ai_finetune_job` (
  `tenant_id`, `job_code`, `job_name`, `dataset_id`, `base_model`, `tuning_method`,
  `epochs`, `learning_rate`, `lora_rank`, `status`, `progress_percent`,
  `train_loss`, `eval_accuracy`, `baseline_accuracy`, `output_model_id`, `metrics_json`
)
SELECT 1, 'ft-nl2sql-lora-v1', '天枢领域 NL2SQL LoRA 微调实验 v1', ds.`id`,
       'Qwen2.5-7B-Instruct', 'LORA', 3, '2e-4', 16, 'COMPLETED', 100,
       0.2140, 0.9420, 0.8150, 'ft:qwen2.5-7b:tianshu-nl2sql-v1',
       '{"lossCurve":[0.89,0.54,0.35,0.26,0.214],"baselineSqlValidRate":0.815,"tunedSqlValidRate":0.942,"avgLatencyDeltaMs":-180}'
FROM `ai_finetune_dataset` ds
WHERE ds.`dataset_code` = 'nl2sql-sft-v1' AND ds.`tenant_id` = 1 AND ds.`deleted` = 0
AND NOT EXISTS (
  SELECT 1 FROM `ai_finetune_job` j WHERE j.`job_code` = 'ft-nl2sql-lora-v1' AND j.`tenant_id` = 1 AND j.`deleted` = 0
);

-- 6. Standalone Top-Level Menu Promotion: ai_center ('AI 智能中心')
INSERT INTO `iam_menu` (
  `tenant_id`, `parent_id`, `menu_type`, `menu_code`, `app_code`, `menu_name`,
  `route_path`, `component_path`, `permission_code`, `icon`, `sort_no`,
  `status`, `visible`, `keep_alive`
)
SELECT
  1, 0, 'DIRECTORY', 'ai_center', 'system', 'AI 智能中心',
  '/ai', '', 'system:config:query', 'Cpu', 7,
  'ENABLED', 1, 0
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM `iam_menu` WHERE `menu_code` = 'ai_center' AND `tenant_id` = 1 AND `deleted` = 0
);

-- Move existing system_ai_copilot and system_ai_models under ai_center
UPDATE `iam_menu`
SET `parent_id` = (SELECT p.`id` FROM (SELECT `id` FROM `iam_menu` WHERE `menu_code` = 'ai_center' AND `tenant_id` = 1 AND `deleted` = 0 LIMIT 1) p),
    `app_code` = 'system',
    `sort_no` = 10
WHERE `menu_code` = 'system_ai_copilot' AND `tenant_id` = 1 AND `deleted` = 0;

UPDATE `iam_menu`
SET `parent_id` = (SELECT p.`id` FROM (SELECT `id` FROM `iam_menu` WHERE `menu_code` = 'ai_center' AND `tenant_id` = 1 AND `deleted` = 0 LIMIT 1) p),
    `app_code` = 'system',
    `sort_no` = 40
WHERE `menu_code` = 'system_ai_models' AND `tenant_id` = 1 AND `deleted` = 0;

-- Insert new AI Center Child Menus: Knowledge Base, Agent Studio, Fine-tune Studio
INSERT INTO `iam_menu` (
  `tenant_id`, `parent_id`, `menu_type`, `menu_code`, `app_code`, `menu_name`,
  `route_path`, `component_path`, `permission_code`, `icon`, `sort_no`,
  `status`, `visible`, `keep_alive`
)
SELECT
  1,
  (SELECT p.`id` FROM (SELECT `id` FROM `iam_menu` WHERE `menu_code` = 'ai_center' AND `tenant_id` = 1 AND `deleted` = 0 LIMIT 1) p),
  'MENU', 'ai_knowledge_base', 'system', 'AI 企业知识库',
  '/ai/knowledge', '@/views/ai/KnowledgeBaseView.vue', 'system:config:query', 'Collection', 20,
  'ENABLED', 1, 1
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM `iam_menu` WHERE `menu_code` = 'ai_knowledge_base' AND `tenant_id` = 1 AND `deleted` = 0
);

INSERT INTO `iam_menu` (
  `tenant_id`, `parent_id`, `menu_type`, `menu_code`, `app_code`, `menu_name`,
  `route_path`, `component_path`, `permission_code`, `icon`, `sort_no`,
  `status`, `visible`, `keep_alive`
)
SELECT
  1,
  (SELECT p.`id` FROM (SELECT `id` FROM `iam_menu` WHERE `menu_code` = 'ai_center' AND `tenant_id` = 1 AND `deleted` = 0 LIMIT 1) p),
  'MENU', 'ai_agent_studio', 'system', '智能体工坊',
  '/ai/agents', '@/views/ai/AgentStudioView.vue', 'system:config:query', 'Service', 30,
  'ENABLED', 1, 1
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM `iam_menu` WHERE `menu_code` = 'ai_agent_studio' AND `tenant_id` = 1 AND `deleted` = 0
);

INSERT INTO `iam_menu` (
  `tenant_id`, `parent_id`, `menu_type`, `menu_code`, `app_code`, `menu_name`,
  `route_path`, `component_path`, `permission_code`, `icon`, `sort_no`,
  `status`, `visible`, `keep_alive`
)
SELECT
  1,
  (SELECT p.`id` FROM (SELECT `id` FROM `iam_menu` WHERE `menu_code` = 'ai_center' AND `tenant_id` = 1 AND `deleted` = 0 LIMIT 1) p),
  'MENU', 'ai_finetune_studio', 'system', '模型微调与评测',
  '/ai/finetune', '@/views/ai/FinetuneStudioView.vue', 'system:config:query', 'DataLine', 50,
  'ENABLED', 1, 1
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM `iam_menu` WHERE `menu_code` = 'ai_finetune_studio' AND `tenant_id` = 1 AND `deleted` = 0
);

-- Grant role_id = 1 all new AI Center menus
INSERT IGNORE INTO `iam_role_menu` (`tenant_id`, `role_id`, `menu_id`)
SELECT 1, 1, `id` FROM `iam_menu`
WHERE `menu_code` IN ('ai_center', 'system_ai_copilot', 'ai_knowledge_base', 'ai_agent_studio', 'system_ai_models', 'ai_finetune_studio')
  AND `tenant_id` = 1 AND `deleted` = 0;
