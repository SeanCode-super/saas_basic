-- ===================================================================
-- V63: 天枢云基 · AI 智能模型网关与多供应商动态配置中心
-- 包含：模型服务商表、动态大模型目录表、预置初始主流模型(DeepSeek/OpenAI/通义千问/Ollama)、系统菜单与超管赋权
-- ===================================================================
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- 1. AI 供应商连接配置表
CREATE TABLE IF NOT EXISTS `ai_provider` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID (1为平台全局默认, 租户ID>1为租户BYOK)',
  `provider_code` VARCHAR(64) NOT NULL COMMENT '服务商唯一代号: deepseek, openai, qwen, ollama, custom',
  `provider_name` VARCHAR(128) NOT NULL COMMENT '服务商中文名称',
  `provider_type` VARCHAR(32) NOT NULL DEFAULT 'OPENAI_COMPATIBLE' COMMENT '协议类型: OPENAI_COMPATIBLE, OLLAMA, CUSTOM',
  `base_url` VARCHAR(255) NOT NULL COMMENT 'API 端点 Base URL',
  `api_key` VARCHAR(255) NULL COMMENT 'API Key 凭证 (支持加密脱敏)',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态: ENABLED, DISABLED',
  `is_default` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否平台首选默认服务商: 0否, 1是',
  `sort_no` INT NOT NULL DEFAULT 10 COMMENT '排序号',
  `remark` VARCHAR(500) NULL COMMENT '备注与使用说明',
  `created_by` BIGINT NOT NULL DEFAULT 0 COMMENT '创建人ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` BIGINT NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `deleted_at` DATETIME NULL COMMENT '删除时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_provider_code` (`tenant_id`, `provider_code`, (CASE WHEN `deleted` = 0 THEN 0 ELSE NULL END))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 大模型供应商配置表';

-- 2. AI 动态模型配置表
CREATE TABLE IF NOT EXISTS `ai_model_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
  `provider_id` BIGINT NOT NULL COMMENT '关联供应商ID',
  `model_id` VARCHAR(128) NOT NULL COMMENT '模型实际标识符，如 deepseek-chat, gpt-4o, qwen-plus',
  `model_name` VARCHAR(128) NOT NULL COMMENT '模型前端展示名称',
  `category` VARCHAR(64) NOT NULL DEFAULT 'CHAT' COMMENT '模型分类: CHAT(通用对话), REASONING(深度思考), CODER(代码生成)',
  `context_window` VARCHAR(32) NOT NULL DEFAULT '64k' COMMENT '上下文窗口规格',
  `max_tokens` INT NOT NULL DEFAULT 4096 COMMENT '单次最大输出 Token',
  `temperature` DECIMAL(3,2) NOT NULL DEFAULT 0.70 COMMENT '采样温度 (0.0~2.0)',
  `is_default` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否前端对话默认勾选模型: 0否, 1是',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态: ENABLED, DISABLED',
  `sort_no` INT NOT NULL DEFAULT 10 COMMENT '排序号',
  `created_by` BIGINT NOT NULL DEFAULT 0 COMMENT '创建人ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` BIGINT NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `deleted_at` DATETIME NULL COMMENT '删除时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_model_id` (`tenant_id`, `model_id`, (CASE WHEN `deleted` = 0 THEN 0 ELSE NULL END)),
  INDEX `idx_ai_model_provider` (`provider_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 大模型字典与参数编排表';

-- 3. 预置主流主流大模型服务商模版
INSERT INTO `ai_provider` (
  `tenant_id`, `provider_code`, `provider_name`, `provider_type`,
  `base_url`, `api_key`, `status`, `is_default`, `sort_no`, `remark`
) VALUES
(
  1, 'deepseek', 'DeepSeek 官方开放平台', 'OPENAI_COMPATIBLE',
  'https://api.deepseek.com', '', 'ENABLED', 1, 10,
  '国内顶尖推理与通用大模型，兼容 OpenAI 协议，性价比极高'
),
(
  1, 'openai', 'OpenAI 官方 API', 'OPENAI_COMPATIBLE',
  'https://api.openai.com/v1', '', 'ENABLED', 0, 20,
  '全球标杆大模型，支持 GPT-4o、GPT-4o Mini 等'
),
(
  1, 'qwen', '阿里云百炼 / 通义千问', 'OPENAI_COMPATIBLE',
  'https://dashscope.aliyuncs.com/compatible-mode/v1', '', 'ENABLED', 0, 30,
  '阿里通义百炼 OpenAI 兼容协议模式，企业中文理解表现优异'
),
(
  1, 'ollama', '本地私有化 Ollama 网关', 'OLLAMA',
  'http://localhost:11434/v1', '', 'ENABLED', 0, 40,
  '支持企业本地私有化 GPU 部署（Llama 3、DeepSeek 本地量化版），零网络出境风险'
)
ON DUPLICATE KEY UPDATE
  `provider_name` = VALUES(`provider_name`),
  `base_url` = VALUES(`base_url`);

-- 4. 预置主流大模型目录
INSERT INTO `ai_model_config` (
  `tenant_id`, `provider_id`, `model_id`, `model_name`, `category`,
  `context_window`, `max_tokens`, `temperature`, `is_default`, `status`, `sort_no`
) VALUES
(
  1,
  (SELECT `id` FROM `ai_provider` WHERE `provider_code` = 'deepseek' LIMIT 1),
  'deepseek-chat', 'DeepSeek-V3 (通用旗舰大模型)', 'CHAT',
  '64k', 4096, 0.70, 1, 'ENABLED', 10
),
(
  1,
  (SELECT `id` FROM `ai_provider` WHERE `provider_code` = 'deepseek' LIMIT 1),
  'deepseek-reasoner', 'DeepSeek-R1 (深度思维链推理)', 'REASONING',
  '64k', 8192, 0.60, 0, 'ENABLED', 20
),
(
  1,
  (SELECT `id` FROM `ai_provider` WHERE `provider_code` = 'openai' LIMIT 1),
  'gpt-4o-mini', 'OpenAI GPT-4o Mini (极速轻量)', 'CHAT',
  '128k', 4096, 0.70, 0, 'ENABLED', 30
),
(
  1,
  (SELECT `id` FROM `ai_provider` WHERE `provider_code` = 'qwen' LIMIT 1),
  'qwen-plus', '通义千问 Qwen-Plus (工业级增强)', 'CHAT',
  '32k', 4096, 0.70, 0, 'ENABLED', 40
),
(
  1,
  (SELECT `id` FROM `ai_provider` WHERE `provider_code` = 'ollama' LIMIT 1),
  'llama3:8b', 'Llama 3 8B (本地私有化部署)', 'CHAT',
  '8k', 2048, 0.70, 0, 'ENABLED', 50
)
ON DUPLICATE KEY UPDATE
  `model_name` = VALUES(`model_name`),
  `category` = VALUES(`category`),
  `context_window` = VALUES(`context_window`);

-- 5. 插入「AI 智能模型网关」功能菜单至「系统中心」(parent_id = 8)
INSERT INTO `iam_menu` (
  `tenant_id`, `parent_id`, `menu_type`, `menu_code`, `menu_name`,
  `route_path`, `component_path`, `permission_code`, `icon`, `sort_no`,
  `status`, `visible`, `keep_alive`
)
SELECT
  1, 8, 'MENU', 'system_ai_models', 'AI 智能模型网关',
  '/system/ai-models', '@/views/system/AiModelConfigView.vue', 'system:config:query', 'Cpu', 68,
  'ENABLED', 1, 1
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM `iam_menu` WHERE `menu_code` = 'system_ai_models' AND `tenant_id` = 1 AND `deleted` = 0
);

-- 赋权给超级管理员角色 (role_id = 1)
INSERT IGNORE INTO `iam_role_menu` (`tenant_id`, `role_id`, `menu_id`)
SELECT 1, 1, `id` FROM `iam_menu` WHERE `menu_code` = 'system_ai_models' AND `tenant_id` = 1 AND `deleted` = 0;
