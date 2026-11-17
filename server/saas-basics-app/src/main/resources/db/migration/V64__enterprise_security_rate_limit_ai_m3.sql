-- ===================================================================
-- V64: 企业级 AI 核心模块 (M3) 治理闭环：凭证引用、Prompt 版本、MCP 服务器与工具白名单、
--      Run/Step/ToolCall 审批状态机、租户 Token 用量与预算配额
-- ===================================================================
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- 1. AI 凭据引用与密钥轮换审计表 (绝不回显或存储明文私钥)
CREATE TABLE IF NOT EXISTS `ai_credential_ref` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
  `credential_code` VARCHAR(64) NOT NULL COMMENT '凭据引用唯一编码',
  `provider_id` BIGINT NOT NULL COMMENT '关联供应商ID',
  `key_alias` VARCHAR(128) NOT NULL COMMENT '凭据别名',
  `masked_hint` VARCHAR(64) NULL COMMENT '脱敏摘要如 sk-****abcd',
  `encryption_algo` VARCHAR(32) NOT NULL DEFAULT 'AES-256-GCM' COMMENT '加密算法',
  `key_version` VARCHAR(32) NOT NULL DEFAULT 'v1' COMMENT '激活密钥版本',
  `cipher_digest` VARCHAR(64) NULL COMMENT '密文SHA-256指纹摘要',
  `rotation_count` INT NOT NULL DEFAULT 0 COMMENT '累计轮换次数',
  `last_rotated_at` DATETIME(3) NULL COMMENT '最近加密/轮换时间',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE, LEGACY_PLAINTEXT, ROTATED, REVOKED',
  `remark` VARCHAR(500) NULL COMMENT '备注',
  `created_by` BIGINT NOT NULL DEFAULT 0,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_by` BIGINT NOT NULL DEFAULT 0,
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_cred_code` (`tenant_id`, `credential_code`, (CASE WHEN `deleted` = 0 THEN 0 ELSE NULL END)),
  INDEX `idx_ai_cred_provider` (`provider_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 凭据引用与轮换审计表';

-- 2. AI Prompt 元数据与不可变版本表
CREATE TABLE IF NOT EXISTS `ai_prompt` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL DEFAULT 1,
  `prompt_code` VARCHAR(64) NOT NULL COMMENT 'Prompt 唯一编码',
  `prompt_name` VARCHAR(128) NOT NULL COMMENT 'Prompt 名称',
  `category` VARCHAR(64) NOT NULL DEFAULT 'GENERAL' COMMENT '分类: GENERAL, ARCHITECT, WORKFLOW, CODEGEN',
  `active_version` INT NOT NULL DEFAULT 1 COMMENT '当前生效发布版本号',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED, DISABLED',
  `remark` VARCHAR(500) NULL,
  `created_by` BIGINT NOT NULL DEFAULT 0,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_by` BIGINT NOT NULL DEFAULT 0,
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_prompt_code` (`tenant_id`, `prompt_code`, (CASE WHEN `deleted` = 0 THEN 0 ELSE NULL END))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Prompt 定义主表';

CREATE TABLE IF NOT EXISTS `ai_prompt_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL DEFAULT 1,
  `prompt_id` BIGINT NOT NULL COMMENT '关联 ai_prompt.id',
  `version_no` INT NOT NULL COMMENT '不可变语义版本号',
  `system_prompt` TEXT NOT NULL COMMENT '系统角色指令',
  `user_template` TEXT NULL COMMENT '用户输入模板',
  `variables_json` VARCHAR(1000) NULL COMMENT '模板变量声明 JSON',
  `model_hint` VARCHAR(128) NULL COMMENT '推荐模型标识',
  `changelog` VARCHAR(500) NULL COMMENT '变更说明',
  `is_published` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已发布: 0否, 1是',
  `remark` VARCHAR(500) NULL,
  `created_by` BIGINT NOT NULL DEFAULT 0,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_by` BIGINT NOT NULL DEFAULT 0,
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_prompt_ver` (`tenant_id`, `prompt_id`, `version_no`, (CASE WHEN `deleted` = 0 THEN 0 ELSE NULL END))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Prompt 不可变版本表';

-- 3. MCP 服务器连接与显式工具白名单表 (默认拒绝全部工具，需白名单放行)
CREATE TABLE IF NOT EXISTS `ai_mcp_server` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL DEFAULT 1,
  `server_code` VARCHAR(64) NOT NULL COMMENT 'MCP 服务器唯一编码',
  `server_name` VARCHAR(128) NOT NULL COMMENT 'MCP 服务名称',
  `transport_type` VARCHAR(32) NOT NULL DEFAULT 'STREAMABLE_HTTP' COMMENT '传输协议: BUILTIN_LOOPBACK, STREAMABLE_HTTP, SSE',
  `endpoint_url` VARCHAR(512) NOT NULL COMMENT 'MCP JSON-RPC 端点地址',
  `auth_header_name` VARCHAR(64) NULL DEFAULT 'Authorization' COMMENT '鉴权请求头',
  `encrypted_auth_token` VARCHAR(512) NULL COMMENT 'AES-256-GCM 加密认证凭据',
  `allowed_tools_json` TEXT NULL COMMENT '显式允许的工具编码白名单 JSON 数组',
  `health_status` VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN' COMMENT 'ONLINE, DEGRADED, OFFLINE, UNKNOWN',
  `last_checked_at` DATETIME(3) NULL,
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
  UNIQUE KEY `uk_ai_mcp_code` (`tenant_id`, `server_code`, (CASE WHEN `deleted` = 0 THEN 0 ELSE NULL END))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP 协议服务器与白名单配置表';

-- 4. AI 工具注册表 (含权限码、风险等级、幂等标记)
CREATE TABLE IF NOT EXISTS `ai_tool` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL DEFAULT 1,
  `tool_code` VARCHAR(128) NOT NULL COMMENT '全局唯一工具编码',
  `tool_name` VARCHAR(128) NOT NULL COMMENT '工具展示名称',
  `semantic_version` VARCHAR(32) NOT NULL DEFAULT '1.0.0',
  `description` VARCHAR(500) NULL,
  `input_schema_json` TEXT NULL COMMENT 'JSON Schema 输入契约',
  `output_schema_json` TEXT NULL COMMENT 'JSON Schema 输出契约',
  `permission_code` VARCHAR(128) NOT NULL DEFAULT 'system:config:query' COMMENT '对应业务 API 权限码',
  `risk_level` VARCHAR(32) NOT NULL DEFAULT 'READ_ONLY' COMMENT 'READ_ONLY, LOW, HIGH',
  `adapter_type` VARCHAR(32) NOT NULL DEFAULT 'INTERNAL_FACADE' COMMENT 'INTERNAL_FACADE, MCP',
  `mcp_server_id` BIGINT NULL COMMENT 'MCP 模式归属服务器ID',
  `whitelisted` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否加入平台允许白名单',
  `idempotent` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否支持幂等执行',
  `timeout_ms` INT NOT NULL DEFAULT 5000,
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
  UNIQUE KEY `uk_ai_tool_code` (`tenant_id`, `tool_code`, (CASE WHEN `deleted` = 0 THEN 0 ELSE NULL END))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 工具注册与风控等级表';

-- 5. AI Run / Step / ToolCall 运行态与审批状态机
CREATE TABLE IF NOT EXISTS `ai_run` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL DEFAULT 1,
  `run_no` VARCHAR(64) NOT NULL COMMENT 'Run 唯一流水号',
  `user_id` BIGINT NOT NULL DEFAULT 0,
  `username` VARCHAR(64) NULL,
  `model_id` VARCHAR(128) NOT NULL,
  `provider_code` VARCHAR(64) NULL,
  `prompt_code` VARCHAR(64) NULL,
  `prompt_version` INT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'CREATED' COMMENT 'CREATED, RUNNING, WAITING_APPROVAL, REJECTED, SUCCEEDED, FAILED, CANCELLED, TIMED_OUT',
  `prompt_tokens` INT NOT NULL DEFAULT 0,
  `completion_tokens` INT NOT NULL DEFAULT 0,
  `total_tokens` INT NOT NULL DEFAULT 0,
  `estimated_cost` DECIMAL(12,6) NOT NULL DEFAULT 0.000000,
  `latency_ms` BIGINT NOT NULL DEFAULT 0,
  `error_type` VARCHAR(64) NULL,
  `error_message` VARCHAR(1000) NULL,
  `trace_id` VARCHAR(64) NULL,
  `input_summary` VARCHAR(1000) NULL,
  `output_summary` TEXT NULL,
  `started_at` DATETIME(3) NULL,
  `finished_at` DATETIME(3) NULL,
  `remark` VARCHAR(500) NULL,
  `created_by` BIGINT NOT NULL DEFAULT 0,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_by` BIGINT NOT NULL DEFAULT 0,
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_run_no` (`tenant_id`, `run_no`, (CASE WHEN `deleted` = 0 THEN 0 ELSE NULL END)),
  INDEX `idx_ai_run_status` (`tenant_id`, `status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 执行 Run 状态机表';

CREATE TABLE IF NOT EXISTS `ai_run_step` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL DEFAULT 1,
  `run_id` BIGINT NOT NULL,
  `step_no` INT NOT NULL,
  `step_type` VARCHAR(32) NOT NULL COMMENT 'MODEL_CHAT, TOOL_DISPATCH, APPROVAL_GATE, MCP_CALL',
  `model_id` VARCHAR(128) NULL,
  `tool_code` VARCHAR(128) NULL,
  `status` VARCHAR(32) NOT NULL,
  `prompt_tokens` INT NOT NULL DEFAULT 0,
  `completion_tokens` INT NOT NULL DEFAULT 0,
  `latency_ms` BIGINT NOT NULL DEFAULT 0,
  `error_type` VARCHAR(64) NULL,
  `detail_json` TEXT NULL,
  `remark` VARCHAR(500) NULL,
  `created_by` BIGINT NOT NULL DEFAULT 0,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_by` BIGINT NOT NULL DEFAULT 0,
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  INDEX `idx_ai_step_run` (`run_id`, `step_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Run 执行步骤明细表';

CREATE TABLE IF NOT EXISTS `ai_tool_call` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL DEFAULT 1,
  `run_id` BIGINT NOT NULL,
  `call_no` VARCHAR(64) NOT NULL,
  `tool_code` VARCHAR(128) NOT NULL,
  `adapter_type` VARCHAR(32) NOT NULL DEFAULT 'INTERNAL_FACADE',
  `risk_level` VARCHAR(32) NOT NULL DEFAULT 'READ_ONLY',
  `permission_code` VARCHAR(128) NULL,
  `idempotency_key` VARCHAR(128) NULL,
  `arguments_summary` TEXT NULL,
  `result_summary` TEXT NULL,
  `permission_granted` TINYINT(1) NOT NULL DEFAULT 1,
  `approval_status` VARCHAR(32) NOT NULL DEFAULT 'AUTO_APPROVED' COMMENT 'AUTO_APPROVED, PENDING_APPROVAL, APPROVED, REJECTED',
  `approved_by` BIGINT NULL,
  `approved_at` DATETIME(3) NULL,
  `reject_reason` VARCHAR(500) NULL,
  `execution_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, RUNNING, SUCCEEDED, FAILED, SKIPPED',
  `latency_ms` BIGINT NOT NULL DEFAULT 0,
  `remark` VARCHAR(500) NULL,
  `created_by` BIGINT NOT NULL DEFAULT 0,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_by` BIGINT NOT NULL DEFAULT 0,
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_tool_call_no` (`tenant_id`, `call_no`, (CASE WHEN `deleted` = 0 THEN 0 ELSE NULL END)),
  INDEX `idx_ai_tc_run_approval` (`run_id`, `approval_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 工具调用审批与执行审计表';

-- 6. 租户 AI Token 用量预算与速率配额表
CREATE TABLE IF NOT EXISTS `ai_tenant_quota` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL DEFAULT 1,
  `monthly_token_limit` BIGINT NOT NULL DEFAULT 5000000 COMMENT '月度 Token 总预算上限',
  `used_tokens_month` BIGINT NOT NULL DEFAULT 0 COMMENT '本月已消耗 Token 总数',
  `prompt_tokens_month` BIGINT NOT NULL DEFAULT 0 COMMENT '本月输入 Prompt Token 数',
  `completion_tokens_month` BIGINT NOT NULL DEFAULT 0 COMMENT '本月生成 Completion Token 数',
  `estimated_cost_month` DECIMAL(12,6) NOT NULL DEFAULT 0.000000 COMMENT '本月累计预估费用',
  `billing_month` VARCHAR(7) NOT NULL DEFAULT '2026-11' COMMENT '账期月份 YYYY-MM',
  `rpm_limit` INT NOT NULL DEFAULT 60 COMMENT 'AI 网关每分钟并发上限',
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
  UNIQUE KEY `uk_ai_quota_tenant` (`tenant_id`, (CASE WHEN `deleted` = 0 THEN 0 ELSE NULL END))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 租户 Token 用量与配额治理表';

-- 7. 初始化凭据引用元数据 (同步 V63 供应商)
INSERT INTO `ai_credential_ref` (
  `tenant_id`, `credential_code`, `provider_id`, `key_alias`, `masked_hint`,
  `encryption_algo`, `key_version`, `rotation_count`, `status`, `remark`
)
SELECT
  p.`tenant_id`,
  CONCAT('cred_', p.`provider_code`, '_', p.`id`),
  p.`id`,
  CONCAT(p.`provider_name`, ' API Key'),
  '未配置',
  'AES-256-GCM',
  'v1',
  0,
  'ACTIVE',
  'M3 Vault 自动托管凭据引用'
FROM `ai_provider` p
WHERE p.`deleted` = 0
ON DUPLICATE KEY UPDATE `key_alias` = VALUES(`key_alias`);

-- 8. 初始化内建 Loopback MCP 服务器与标准平台工具
INSERT INTO `ai_mcp_server` (
  `tenant_id`, `server_code`, `server_name`, `transport_type`, `endpoint_url`,
  `auth_header_name`, `allowed_tools_json`, `health_status`, `status`, `remark`
) VALUES (
  1,
  'builtin-platform-mcp',
  '天枢底座内建 MCP 服务端 (Loopback)',
  'BUILTIN_LOOPBACK',
  'loopback://tianshu-platform/mcp/v1',
  'Authorization',
  '["platform.metrics.summary","platform.tenant.quota","platform.audit.recent"]',
  'ONLINE',
  'ENABLED',
  '遵循 JSON-RPC 2.0 MCP 规范，默认拒绝未显式列入白名单的工具'
) ON DUPLICATE KEY UPDATE `server_name` = VALUES(`server_name`);

INSERT INTO `ai_tool` (
  `tenant_id`, `tool_code`, `tool_name`, `semantic_version`, `description`,
  `input_schema_json`, `output_schema_json`, `permission_code`, `risk_level`,
  `adapter_type`, `mcp_server_id`, `whitelisted`, `idempotent`, `timeout_ms`, `status`
) VALUES
(
  1, 'platform.metrics.summary', '平台运行指标与健康概览', '1.0.0',
  '只读检索当前底座 JVM 堆内存、限流统计与 OpenTelemetry 遥测摘要',
  '{"type":"object","properties":{"detail":{"type":"boolean"}},"additionalProperties":false}',
  '{"type":"object","properties":{"status":{"type":"string"},"httpRequests":{"type":"integer"}}}',
  'system:config:query', 'READ_ONLY', 'INTERNAL_FACADE', NULL, 1, 1, 3000, 'ENABLED'
),
(
  1, 'platform.tenant.quota', '查询租户 AI Token 配额账单', '1.0.0',
  '只读获取当前登录租户的 AI 月度 Token 额度、消耗进度与 RPM 上限',
  '{"type":"object","properties":{},"additionalProperties":false}',
  '{"type":"object","properties":{"monthlyLimit":{"type":"integer"},"usedTokens":{"type":"integer"}}}',
  'system:config:query', 'READ_ONLY', 'MCP',
  (SELECT `id` FROM `ai_mcp_server` WHERE `server_code` = 'builtin-platform-mcp' LIMIT 1),
  1, 1, 3000, 'ENABLED'
),
(
  1, 'platform.audit.recent', '检索近期平台审计追踪摘要', '1.0.0',
  '按当前安全上下文查询最近链路追踪 Trace 摘要（脱敏输出）',
  '{"type":"object","properties":{"limit":{"type":"integer","maximum":20}},"additionalProperties":false}',
  '{"type":"object","properties":{"traces":{"type":"array"}}}',
  'system:config:query', 'READ_ONLY', 'MCP',
  (SELECT `id` FROM `ai_mcp_server` WHERE `server_code` = 'builtin-platform-mcp' LIMIT 1),
  1, 1, 3000, 'ENABLED'
),
(
  1, 'platform.cache.evict', '清理租户平台级运行缓存 (高危写操作)', '1.0.0',
  '高风险运维写入工具：清理当前租户动态配置与限流本地窗口缓存，必须经过人工审批及幂等键校验方可执行',
  '{"type":"object","properties":{"scope":{"type":"string","enum":["rate_limit","all"]},"reason":{"type":"string"}},"required":["scope"]}',
  '{"type":"object","properties":{"evicted":{"type":"boolean"},"timestamp":{"type":"string"}}}',
  'system:config:query', 'HIGH', 'INTERNAL_FACADE', NULL, 1, 1, 5000, 'ENABLED'
)
ON DUPLICATE KEY UPDATE
  `tool_name` = VALUES(`tool_name`),
  `risk_level` = VALUES(`risk_level`);

-- 9. 初始化版本化 Prompt 资产与默认租户 AI 配额
INSERT INTO `ai_prompt` (
  `tenant_id`, `prompt_code`, `prompt_name`, `category`, `active_version`, `status`, `remark`
) VALUES
(
  1, 'tianshu-assistant', '天枢云基全栈架构助手', 'GENERAL', 1, 'ENABLED',
  '全局 AI 悬浮窗默认系统提示词'
),
(
  1, 'sql-architect', 'MySQL 多租户数据库设计顾问', 'ARCHITECT', 1, 'ENABLED',
  '内置规范检查：必须含 tenant_id、软删除条件唯一索引、审计字段'
)
ON DUPLICATE KEY UPDATE `prompt_name` = VALUES(`prompt_name`);

INSERT INTO `ai_prompt_version` (
  `tenant_id`, `prompt_id`, `version_no`, `system_prompt`, `user_template`,
  `variables_json`, `model_hint`, `changelog`, `is_published`
)
SELECT
  1, p.`id`, 1,
  '你是天枢云基 (Tianshu PaaS) 企业级多租户 SaaS 平台的首席架构师兼技术助手。回答必须遵循多租户安全隔离、Flyway 版本演进、ArchUnit 分层边界以及高并发限流与可观测性最佳实践。',
  '{{question}}',
  '["question"]',
  'deepseek-chat',
  '初始发布版本 v1',
  1
FROM `ai_prompt` p
WHERE p.`prompt_code` = 'tianshu-assistant' AND p.`deleted` = 0
AND NOT EXISTS (
  SELECT 1 FROM `ai_prompt_version` v WHERE v.`prompt_id` = p.`id` AND v.`version_no` = 1 AND v.`deleted` = 0
);

INSERT INTO `ai_prompt_version` (
  `tenant_id`, `prompt_id`, `version_no`, `system_prompt`, `user_template`,
  `variables_json`, `model_hint`, `changelog`, `is_published`
)
SELECT
  1, p.`id`, 1,
  '你是精通 MySQL 8/9 与 MyBatis-Plus 多租户拦截器的数据库专家。所有业务表 DDL 必须包含 tenant_id、created_by、created_at、updated_by、updated_at、deleted、deleted_at、version，并使用函数唯一索引 (CASE WHEN deleted = 0 THEN 0 ELSE NULL END)。',
  '请针对以下需求设计表结构：{{requirement}}',
  '["requirement"]',
  'deepseek-reasoner',
  '初始发布版本 v1',
  1
FROM `ai_prompt` p
WHERE p.`prompt_code` = 'sql-architect' AND p.`deleted` = 0
AND NOT EXISTS (
  SELECT 1 FROM `ai_prompt_version` v WHERE v.`prompt_id` = p.`id` AND v.`version_no` = 1 AND v.`deleted` = 0
);

INSERT INTO `ai_tenant_quota` (
  `tenant_id`, `monthly_token_limit`, `used_tokens_month`, `billing_month`, `rpm_limit`, `status`, `remark`
) VALUES (
  1, 5000000, 0, '2026-11', 60, 'ENABLED', '平台默认旗舰租户 AI 预算配额'
) ON DUPLICATE KEY UPDATE `monthly_token_limit` = VALUES(`monthly_token_limit`);
