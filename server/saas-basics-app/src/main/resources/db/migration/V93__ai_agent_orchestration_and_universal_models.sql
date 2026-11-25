-- ===================================================================
-- V93: Visual Agent DAG Orchestration & Multi-Protocol AI Model Seeds
-- ===================================================================

CREATE TABLE IF NOT EXISTS `ai_agent_workflow` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `public_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `workflow_code` varchar(64) NOT NULL,
  `workflow_name` varchar(128) NOT NULL,
  `agent_id` bigint unsigned DEFAULT NULL,
  `trigger_mode` varchar(32) NOT NULL DEFAULT 'MANUAL',
  `graph_json` longtext NOT NULL,
  `variables_schema_json` text DEFAULT NULL,
  `default_model_id` varchar(128) DEFAULT NULL,
  `fallback_model_ids_json` varchar(500) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT',
  `published_version` int NOT NULL DEFAULT 0,
  `run_count` int NOT NULL DEFAULT 0,
  `created_by` bigint unsigned NOT NULL DEFAULT 0,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_by` bigint unsigned NOT NULL DEFAULT 0,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  `deleted_at` datetime(3) DEFAULT NULL,
  `version` int unsigned NOT NULL DEFAULT 0,
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_agent_wf_public_id` (`tenant_id`, `public_id`, `deleted`),
  UNIQUE KEY `uk_ai_agent_wf_code` (`tenant_id`, `workflow_code`, (CASE WHEN `deleted` = 0 THEN 0 ELSE NULL END))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Visual AI Agent DAG Orchestration Workflows';

CREATE TABLE IF NOT EXISTS `ai_agent_workflow_run` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `public_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `run_no` varchar(64) NOT NULL,
  `workflow_id` bigint unsigned NOT NULL,
  `workflow_code` varchar(64) NOT NULL,
  `trigger_source` varchar(32) NOT NULL DEFAULT 'STUDIO',
  `input_json` longtext DEFAULT NULL,
  `output_json` longtext DEFAULT NULL,
  `context_vars_json` longtext DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'RUNNING',
  `total_tokens` int NOT NULL DEFAULT 0,
  `total_latency_ms` bigint NOT NULL DEFAULT 0,
  `executed_nodes` int NOT NULL DEFAULT 0,
  `error_message` varchar(1000) DEFAULT NULL,
  `started_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `finished_at` datetime(3) DEFAULT NULL,
  `created_by` bigint unsigned NOT NULL DEFAULT 0,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_by` bigint unsigned NOT NULL DEFAULT 0,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  `deleted_at` datetime(3) DEFAULT NULL,
  `version` int unsigned NOT NULL DEFAULT 0,
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_wf_run_no` (`tenant_id`, `run_no`, `deleted`),
  KEY `idx_ai_wf_run_wf` (`tenant_id`, `workflow_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Visual AI Agent Workflow Execution Runs';

CREATE TABLE IF NOT EXISTS `ai_agent_workflow_node_trace` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `public_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `run_id` bigint unsigned NOT NULL,
  `node_id` varchar(64) NOT NULL,
  `node_type` varchar(32) NOT NULL,
  `node_label` varchar(128) NOT NULL,
  `model_id` varchar(128) DEFAULT NULL,
  `provider_code` varchar(64) DEFAULT NULL,
  `attempt_no` int NOT NULL DEFAULT 1,
  `fallback_used` tinyint(1) NOT NULL DEFAULT 0,
  `input_snapshot_json` longtext DEFAULT NULL,
  `output_snapshot_json` longtext DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'SUCCEEDED',
  `prompt_tokens` int NOT NULL DEFAULT 0,
  `completion_tokens` int NOT NULL DEFAULT 0,
  `latency_ms` bigint NOT NULL DEFAULT 0,
  `error_message` varchar(1000) DEFAULT NULL,
  `started_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `finished_at` datetime(3) DEFAULT NULL,
  `created_by` bigint unsigned NOT NULL DEFAULT 0,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_by` bigint unsigned NOT NULL DEFAULT 0,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  `deleted_at` datetime(3) DEFAULT NULL,
  `version` int unsigned NOT NULL DEFAULT 0,
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_wf_trace_pub` (`tenant_id`, `public_id`, `deleted`),
  KEY `idx_ai_wf_trace_run` (`tenant_id`, `run_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Per-Node Execution Trace for AI Agent Workflows';

-- Seed additional major global & Chinese LLM providers cleanly for tenant 1
INSERT INTO `ai_provider` (
  `tenant_id`, `provider_code`, `provider_name`, `provider_type`,
  `base_url`, `api_key`, `status`, `is_default`, `sort_no`, `remark`
)
SELECT 1, 'anthropic', 'Anthropic Claude 官方 API', 'ANTHROPIC',
       'https://api.anthropic.com/v1', '', 'ENABLED', 0, 15,
       'Anthropic 原生 /v1/messages 协议，适合代码工程、复杂推理与 Agent 工具规划'
FROM DUAL WHERE NOT EXISTS (
  SELECT 1 FROM `ai_provider` WHERE `tenant_id` = 1 AND `provider_code` = 'anthropic' AND `deleted` = 0
);

INSERT INTO `ai_provider` (
  `tenant_id`, `provider_code`, `provider_name`, `provider_type`,
  `base_url`, `api_key`, `status`, `is_default`, `sort_no`, `remark`
)
SELECT 1, 'gemini', 'Google Gemini AI Studio', 'GEMINI',
       'https://generativelanguage.googleapis.com/v1beta', '', 'ENABLED', 0, 18,
       'Google Gemini 原生 :generateContent 协议，百万级上下文与多模态理解'
FROM DUAL WHERE NOT EXISTS (
  SELECT 1 FROM `ai_provider` WHERE `tenant_id` = 1 AND `provider_code` = 'gemini' AND `deleted` = 0
);

INSERT INTO `ai_provider` (
  `tenant_id`, `provider_code`, `provider_name`, `provider_type`,
  `base_url`, `api_key`, `status`, `is_default`, `sort_no`, `remark`
)
SELECT 1, 'zhipu', '智谱 AI (GLM 开放平台)', 'OPENAI_COMPATIBLE',
       'https://open.bigmodel.cn/api/paas/v4', '', 'ENABLED', 0, 32,
       '智谱 GLM-4 系列大模型，兼容 OpenAI 协议'
FROM DUAL WHERE NOT EXISTS (
  SELECT 1 FROM `ai_provider` WHERE `tenant_id` = 1 AND `provider_code` = 'zhipu' AND `deleted` = 0
);

INSERT INTO `ai_provider` (
  `tenant_id`, `provider_code`, `provider_name`, `provider_type`,
  `base_url`, `api_key`, `status`, `is_default`, `sort_no`, `remark`
)
SELECT 1, 'moonshot', '月之暗面 Moonshot (Kimi)', 'OPENAI_COMPATIBLE',
       'https://api.moonshot.cn/v1', '', 'ENABLED', 0, 34,
       'Moonshot Kimi 长上下文开放平台'
FROM DUAL WHERE NOT EXISTS (
  SELECT 1 FROM `ai_provider` WHERE `tenant_id` = 1 AND `provider_code` = 'moonshot' AND `deleted` = 0
);

INSERT INTO `ai_provider` (
  `tenant_id`, `provider_code`, `provider_name`, `provider_type`,
  `base_url`, `api_key`, `status`, `is_default`, `sort_no`, `remark`
)
SELECT 1, 'openrouter', 'OpenRouter 全球统一聚合路由', 'OPENAI_COMPATIBLE',
       'https://openrouter.ai/api/v1', '', 'ENABLED', 0, 38,
       '单密钥聚合 OpenAI / Claude / Gemini / Llama / Mistral 开源与闭源全模型'
FROM DUAL WHERE NOT EXISTS (
  SELECT 1 FROM `ai_provider` WHERE `tenant_id` = 1 AND `provider_code` = 'openrouter' AND `deleted` = 0
);

-- Seed flagship models for the new providers
INSERT INTO `ai_model_config` (
  `tenant_id`, `provider_id`, `model_id`, `model_name`, `category`,
  `context_window`, `max_tokens`, `temperature`, `is_default`, `status`, `sort_no`
)
SELECT 1, p.`id`, 'claude-sonnet-4-20250514', 'Claude Sonnet 4 (Anthropic 旗舰)', 'REASONING',
       '200k', 8192, 0.30, 0, 'ENABLED', 60
FROM `ai_provider` p
WHERE p.`tenant_id` = 1 AND p.`provider_code` = 'anthropic' AND p.`deleted` = 0
  AND NOT EXISTS (SELECT 1 FROM `ai_model_config` m WHERE m.`tenant_id` = 1 AND m.`model_id` = 'claude-sonnet-4-20250514' AND m.`deleted` = 0);

INSERT INTO `ai_model_config` (
  `tenant_id`, `provider_id`, `model_id`, `model_name`, `category`,
  `context_window`, `max_tokens`, `temperature`, `is_default`, `status`, `sort_no`
)
SELECT 1, p.`id`, 'gemini-2.5-flash', 'Gemini 2.5 Flash (Google 极速多模态)', 'CHAT',
       '1M', 8192, 0.40, 0, 'ENABLED', 65
FROM `ai_provider` p
WHERE p.`tenant_id` = 1 AND p.`provider_code` = 'gemini' AND p.`deleted` = 0
  AND NOT EXISTS (SELECT 1 FROM `ai_model_config` m WHERE m.`tenant_id` = 1 AND m.`model_id` = 'gemini-2.5-flash' AND m.`deleted` = 0);

INSERT INTO `ai_model_config` (
  `tenant_id`, `provider_id`, `model_id`, `model_name`, `category`,
  `context_window`, `max_tokens`, `temperature`, `is_default`, `status`, `sort_no`
)
SELECT 1, p.`id`, 'glm-4-plus', '智谱 GLM-4 Plus (企业中文旗舰)', 'CHAT',
       '128k', 4096, 0.50, 0, 'ENABLED', 70
FROM `ai_provider` p
WHERE p.`tenant_id` = 1 AND p.`provider_code` = 'zhipu' AND p.`deleted` = 0
  AND NOT EXISTS (SELECT 1 FROM `ai_model_config` m WHERE m.`tenant_id` = 1 AND m.`model_id` = 'glm-4-plus' AND m.`deleted` = 0);

INSERT INTO `ai_model_config` (
  `tenant_id`, `provider_id`, `model_id`, `model_name`, `category`,
  `context_window`, `max_tokens`, `temperature`, `is_default`, `status`, `sort_no`
)
SELECT 1, p.`id`, 'moonshot-v1-128k', 'Kimi Moonshot 128K (超长文本分析)', 'CHAT',
       '128k', 4096, 0.30, 0, 'ENABLED', 75
FROM `ai_provider` p
WHERE p.`tenant_id` = 1 AND p.`provider_code` = 'moonshot' AND p.`deleted` = 0
  AND NOT EXISTS (SELECT 1 FROM `ai_model_config` m WHERE m.`tenant_id` = 1 AND m.`model_id` = 'moonshot-v1-128k' AND m.`deleted` = 0);

-- Seed 2 ready-to-run Visual Agent DAG Orchestration Workflows
INSERT INTO `ai_agent_workflow` (
  `tenant_id`, `public_id`, `workflow_code`, `workflow_name`, `trigger_mode`,
  `graph_json`, `variables_schema_json`, `default_model_id`, `fallback_model_ids_json`,
  `status`, `published_version`, `run_count`, `remark`
)
SELECT 1, '0195a100-0000-7000-8000-000000000093', 'enterprise-rag-router-flow',
       '多模型风控审校与知识库并行工作流', 'MANUAL',
       '{"nodes":[{"id":"start_1","type":"START","label":"输入接收","x":60,"y":180,"config":{"inputs":["query","bizType"]}},{"id":"cond_1","type":"CONDITION","label":"意图分流路由","x":300,"y":180,"config":{"variable":"input.bizType","branches":[{"id":"branch_contract","label":"合同风控","operator":"EQUALS","value":"CONTRACT"},{"id":"default","label":"通用经营分析","operator":"DEFAULT","value":""}]}},{"id":"rag_1","type":"KNOWLEDGE_RAG","label":"法务知识库召回","x":560,"y":90,"config":{"kbIds":[1],"queryTemplate":"{{input.query}}","topK":3}},{"id":"tool_1","type":"TOOL_MCP","label":"平台数据/指标查证","x":560,"y":280,"config":{"toolCode":"nl2sql_query","queryTemplate":"{{input.query}}"}},{"id":"llm_1","type":"LLM","label":"旗舰大模型综合研判","x":840,"y":180,"config":{"modelId":"deepseek-chat","fallbackModelIds":["gemini-2.5-flash","claude-sonnet-4-20250514"],"temperature":0.2,"systemPrompt":"你是企业多模型合规研判专家，请结合知识库或工具数据给出精准结论。","userPromptTemplate":"业务类型: {{input.bizType}}\\n问题: {{input.query}}\\n知识库摘要: {{rag_1.context}}\\n工具检索: {{tool_1.output}}"}},{"id":"end_1","type":"END","label":"输出汇总","x":1100,"y":180,"config":{"outputTemplate":"{{llm_1.text}}"}}],"edges":[{"id":"e1","source":"start_1","target":"cond_1"},{"id":"e2","source":"cond_1","sourceHandle":"branch_contract","target":"rag_1","label":"合同风控"},{"id":"e3","source":"cond_1","sourceHandle":"default","target":"tool_1","label":"通用经营分析"},{"id":"e4","source":"rag_1","target":"llm_1"},{"id":"e5","source":"tool_1","target":"llm_1"},{"id":"e6","source":"llm_1","target":"end_1"}]}',
       '[{"name":"query","type":"STRING","required":true,"defaultValue":"核查单笔合同首付款比例与账期红线"},{"name":"bizType","type":"STRING","required":false,"defaultValue":"CONTRACT"}]',
       'deepseek-chat', '["gemini-2.5-flash","claude-sonnet-4-20250514"]',
       'PUBLISHED', 1, 0,
       '演示条件分流、RAG知识库检索、工具调用与多模型主备自动切换的可视化编排模板'
FROM DUAL WHERE NOT EXISTS (
  SELECT 1 FROM `ai_agent_workflow` WHERE `tenant_id` = 1 AND `workflow_code` = 'enterprise-rag-router-flow' AND `deleted` = 0
);

INSERT INTO `ai_agent_workflow` (
  `tenant_id`, `public_id`, `workflow_code`, `workflow_name`, `trigger_mode`,
  `graph_json`, `variables_schema_json`, `default_model_id`, `fallback_model_ids_json`,
  `status`, `published_version`, `run_count`, `remark`
)
SELECT 1, '0195a100-0000-7000-8000-000000000094', 'multi-model-parallel-arena',
       '多模型并行生成与仲裁融合链', 'API',
       '{"nodes":[{"id":"start_1","type":"START","label":"任务输入","x":60,"y":180,"config":{"inputs":["topic"]}},{"id":"par_1","type":"PARALLEL","label":"并行分发网关","x":280,"y":180,"config":{}},{"id":"llm_a","type":"LLM","label":"DeepSeek/GPT 初稿","x":540,"y":80,"config":{"modelId":"deepseek-chat","fallbackModelIds":["gpt-4o-mini"],"temperature":0.4,"systemPrompt":"输出结构化工程方案草案。","userPromptTemplate":"请围绕以下主题撰写方案要点: {{input.topic}}"}},{"id":"llm_b","type":"LLM","label":"Claude/Gemini 审校","x":540,"y":280,"config":{"modelId":"claude-sonnet-4-20250514","fallbackModelIds":["gemini-2.5-flash","deepseek-chat"],"temperature":0.2,"systemPrompt":"从安全、合规与可落地性视角审校风险。","userPromptTemplate":"请列出主题潜在风险清单: {{input.topic}}"}},{"id":"tpl_1","type":"CODE_TEMPLATE","label":"结果合并格式化","x":820,"y":180,"config":{"template":"【方案草案】\\n{{llm_a.text}}\\n\\n【风控审校】\\n{{llm_b.text}}"}},{"id":"end_1","type":"END","label":"最终输出","x":1060,"y":180,"config":{"outputTemplate":"{{tpl_1.text}}"}}],"edges":[{"id":"e1","source":"start_1","target":"par_1"},{"id":"e2","source":"par_1","target":"llm_a"},{"id":"e3","source":"par_1","target":"llm_b"},{"id":"e4","source":"llm_a","target":"tpl_1"},{"id":"e5","source":"llm_b","target":"tpl_1"},{"id":"e6","source":"tpl_1","target":"end_1"}]}',
       '[{"name":"topic","type":"STRING","required":true,"defaultValue":"企业多云 AI 模型统一网关与成本治理设计"}]',
       'deepseek-chat', '["gemini-2.5-flash"]',
       'PUBLISHED', 1, 0,
       '演示并行分支调用不同厂商模型并自动汇总结果的多模型协同编排模板'
FROM DUAL WHERE NOT EXISTS (
  SELECT 1 FROM `ai_agent_workflow` WHERE `tenant_id` = 1 AND `workflow_code` = 'multi-model-parallel-arena' AND `deleted` = 0
);
