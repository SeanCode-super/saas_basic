-- =====================================================================
-- V78__tenant_baseline_provisioning_and_dict_overlay.sql
-- 为存量非平台租户（id > 1）全量幂等补齐租户级公共基础元数据与组织基线：
--   1. sys_dict_type / sys_dict_item（租户可独立维护）
--   2. sys_config（仅非敏感配置）、sys_i18n_message、iam_data_scope
--   3. file_storage（默认本地盘）、file_bucket、file_upload_route
--   4. erp_warehouse、sched_executor
--   5. ai_provider（密钥留空）、ai_model_config、ai_prompt、ai_prompt_version、ai_tool
--   6. 默认根部门、岗位与管理员员工绑定，以及删除历史错误授予 DIRECTORY 的菜单授权行
-- =====================================================================
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- 1. 清理历史可能存在的 DIRECTORY 直接菜单授权，防止破坏菜单资源层次规范
UPDATE `iam_menu_permission` mp
INNER JOIN `iam_menu` m ON m.`id` = mp.`menu_id` AND m.`deleted` = 0
SET mp.`deleted` = 1,
    mp.`deleted_at` = NOW(3)
WHERE mp.`tenant_id` > 1
  AND mp.`deleted` = 0
  AND m.`menu_type` = 'DIRECTORY';

-- 2. 数据字典（sys_dict_type + sys_dict_item）
INSERT INTO `sys_dict_type` (
  `tenant_id`, `dict_code`, `dict_name`, `dict_scope`, `status`, `cacheable`, `ext_json`, `remark`,
  `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `version`
)
SELECT t.`id`, s.`dict_code`, s.`dict_name`, 'TENANT', s.`status`, s.`cacheable`, s.`ext_json`, s.`remark`,
       1, NOW(3), 1, NOW(3), 0, 0
FROM `plat_tenant` t
INNER JOIN `sys_dict_type` s ON s.`tenant_id` = 1 AND s.`deleted` = 0
WHERE t.`id` > 1 AND t.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `sys_dict_type` ex
    WHERE ex.`tenant_id` = t.`id` AND ex.`dict_code` = s.`dict_code` AND ex.`deleted` = 0
  );

INSERT INTO `sys_dict_item` (
  `tenant_id`, `dict_type_id`, `item_value`, `item_label`, `item_color`, `item_tag`, `parent_id`, `sort_no`,
  `status`, `is_default`, `ext_json`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `version`
)
SELECT t.`id`, tgt_type.`id`, si.`item_value`, si.`item_label`, si.`item_color`, si.`item_tag`, 0, si.`sort_no`,
       si.`status`, si.`is_default`, si.`ext_json`, si.`remark`, 1, NOW(3), 1, NOW(3), 0, 0
FROM `plat_tenant` t
INNER JOIN `sys_dict_item` si ON si.`tenant_id` = 1 AND si.`deleted` = 0
INNER JOIN `sys_dict_type` src_type ON src_type.`id` = si.`dict_type_id` AND src_type.`tenant_id` = 1 AND src_type.`deleted` = 0
INNER JOIN `sys_dict_type` tgt_type ON tgt_type.`tenant_id` = t.`id` AND tgt_type.`dict_code` = src_type.`dict_code` AND tgt_type.`deleted` = 0
WHERE t.`id` > 1 AND t.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `sys_dict_item` ex
    WHERE ex.`tenant_id` = t.`id` AND ex.`dict_type_id` = tgt_type.`id` AND ex.`item_value` = si.`item_value` AND ex.`deleted` = 0
  );

-- 3. 系统参数（仅非加密参数）、国际化文案、数据范围定义
INSERT INTO `sys_config` (
  `tenant_id`, `config_group`, `config_key`, `config_name`, `config_value`, `value_type`, `encrypted`, `status`, `remark`,
  `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `version`
)
SELECT t.`id`, s.`config_group`, s.`config_key`, s.`config_name`, s.`config_value`, s.`value_type`, 0, s.`status`, s.`remark`,
       1, NOW(3), 1, NOW(3), 0, 0
FROM `plat_tenant` t
INNER JOIN `sys_config` s ON s.`tenant_id` = 1 AND s.`deleted` = 0 AND s.`encrypted` = 0
WHERE t.`id` > 1 AND t.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `sys_config` ex
    WHERE ex.`tenant_id` = t.`id` AND ex.`config_group` = s.`config_group` AND ex.`config_key` = s.`config_key` AND ex.`deleted` = 0
  );

INSERT INTO `sys_i18n_message` (
  `tenant_id`, `locale`, `message_group`, `message_key`, `message_value`, `remark`,
  `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `version`
)
SELECT t.`id`, s.`locale`, s.`message_group`, s.`message_key`, s.`message_value`, s.`remark`,
       1, NOW(3), 1, NOW(3), 0, 0
FROM `plat_tenant` t
INNER JOIN `sys_i18n_message` s ON s.`tenant_id` = 1 AND s.`deleted` = 0
WHERE t.`id` > 1 AND t.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `sys_i18n_message` ex
    WHERE ex.`tenant_id` = t.`id` AND ex.`locale` = s.`locale` AND ex.`message_group` = s.`message_group`
      AND ex.`message_key` = s.`message_key` AND ex.`deleted` = 0
  );

INSERT INTO `iam_data_scope` (
  `tenant_id`, `scope_code`, `scope_name`, `scope_type`, `scope_rule_json`, `status`, `remark`,
  `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `version`
)
SELECT t.`id`, s.`scope_code`, s.`scope_name`, s.`scope_type`, s.`scope_rule_json`, s.`status`, s.`remark`,
       1, NOW(3), 1, NOW(3), 0, 0
FROM `plat_tenant` t
INNER JOIN `iam_data_scope` s ON s.`tenant_id` = 1 AND s.`deleted` = 0
WHERE t.`id` > 1 AND t.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `iam_data_scope` ex
    WHERE ex.`tenant_id` = t.`id` AND ex.`scope_code` = s.`scope_code` AND ex.`deleted` = 0
  );

INSERT INTO `sys_sequence_rule` (
  `tenant_id`, `rule_code`, `rule_name`, `prefix`, `date_pattern`, `seq_length`, `step`, `current_value`,
  `reset_cycle`, `status`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `version`
)
SELECT t.`id`, s.`rule_code`, s.`rule_name`, s.`prefix`, s.`date_pattern`, s.`seq_length`, s.`step`, 0,
       s.`reset_cycle`, s.`status`, s.`remark`, 1, NOW(3), 1, NOW(3), 0, 0
FROM `plat_tenant` t
INNER JOIN `sys_sequence_rule` s ON s.`tenant_id` = 1 AND s.`deleted` = 0
WHERE t.`id` > 1 AND t.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `sys_sequence_rule` ex
    WHERE ex.`tenant_id` = t.`id` AND ex.`rule_code` = s.`rule_code` AND ex.`deleted` = 0
  );

INSERT INTO `sec_risk_rule` (
  `tenant_id`, `rule_code`, `rule_name`, `risk_type`, `rule_expr`, `action_json`, `status`, `remark`,
  `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `version`
)
SELECT t.`id`, s.`rule_code`, s.`rule_name`, s.`risk_type`, s.`rule_expr`, s.`action_json`, s.`status`, s.`remark`,
       1, NOW(3), 1, NOW(3), 0, 0
FROM `plat_tenant` t
INNER JOIN `sec_risk_rule` s ON s.`tenant_id` = 1 AND s.`deleted` = 0
WHERE t.`id` > 1 AND t.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `sec_risk_rule` ex
    WHERE ex.`tenant_id` = t.`id` AND ex.`rule_code` = s.`rule_code` AND ex.`deleted` = 0
  );

-- 4. 默认本地存储源、默认存储桶与上传目录路由（仅对尚无存储源的存量非平台租户初始化）
INSERT INTO `file_storage` (
  `tenant_id`, `storage_code`, `storage_name`, `storage_type`, `endpoint`, `bucket_default`, `base_path`,
  `default_folder`, `path_pattern`, `max_file_mb`, `public_base_url`, `status`, `is_default`, `upload_mode`,
  `remark`, `created_by`, `updated_by`, `deleted`
)
SELECT t.`id`, 'local_default', '本地高可用文件存储', 'LOCAL', './data/storage', 'local-disk', './data/storage',
       'uploads', 't_{tenantId}/{folder}/{yyyy}/{MM}/{uuid}_{filename}', 100, '/api/file/raw-download',
       'ENABLED', 1, 'DIRECT_SERVER', '租户默认本地存储源', 1, 1, 0
FROM `plat_tenant` t
WHERE t.`id` > 1 AND t.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `file_storage` ex WHERE ex.`tenant_id` = t.`id` AND ex.`deleted` = 0
  );

INSERT INTO `file_bucket` (
  `tenant_id`, `storage_id`, `bucket_code`, `bucket_name`, `bucket_type`, `bucket_path`, `status`, `remark`,
  `created_by`, `updated_by`, `deleted`
)
SELECT t.`id`,
       (SELECT fs.`id` FROM `file_storage` fs WHERE fs.`tenant_id` = t.`id` AND fs.`deleted` = 0 ORDER BY fs.`is_default` DESC, fs.`id` ASC LIMIT 1),
       'default_bucket', '默认文件桶', 'PRIVATE', CONCAT('/t_', t.`id`, '/default'), 'ENABLED', '租户默认文件桶', 1, 1, 0
FROM `plat_tenant` t
WHERE t.`id` > 1 AND t.`deleted` = 0
  AND EXISTS (SELECT 1 FROM `file_storage` fs WHERE fs.`tenant_id` = t.`id` AND fs.`deleted` = 0)
  AND NOT EXISTS (
    SELECT 1 FROM `file_bucket` ex WHERE ex.`tenant_id` = t.`id` AND ex.`bucket_code` = 'default_bucket' AND ex.`deleted` = 0
  );

INSERT INTO `file_upload_route` (
  `tenant_id`, `storage_id`, `biz_type`, `route_name`, `upload_mode`, `target_dir`, `path_pattern`,
  `max_file_mb`, `allowed_exts`, `priority`, `status`, `remark`, `created_by`, `updated_by`, `deleted`
)
SELECT t.`id`, NULL, s.`biz_type`, s.`route_name`, s.`upload_mode`, s.`target_dir`,
       't_{tenantId}/{folder}/{yyyy}/{MM}/{uuid}_{filename}',
       s.`max_file_mb`, s.`allowed_exts`, s.`priority`, s.`status`, s.`remark`, 1, 1, 0
FROM `plat_tenant` t
INNER JOIN `file_upload_route` s ON s.`tenant_id` = 1 AND s.`deleted` = 0
WHERE t.`id` > 1 AND t.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `file_upload_route` ex WHERE ex.`tenant_id` = t.`id` AND ex.`biz_type` = s.`biz_type` AND ex.`deleted` = 0
  );

-- 5. ERP 默认仓库 与 调度默认本地执行器
INSERT INTO `erp_warehouse` (
  `tenant_id`, `warehouse_code`, `warehouse_name`, `status`, `created_by`, `updated_by`, `deleted`
)
SELECT t.`id`, 'WH-DEFAULT', CONCAT(t.`tenant_name`, '默认中心总仓'), 'ENABLED', 1, 1, 0
FROM `plat_tenant` t
WHERE t.`id` > 1 AND t.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `erp_warehouse` ex WHERE ex.`tenant_id` = t.`id` AND ex.`deleted` = 0
  );

INSERT INTO `sched_executor` (
  `tenant_id`, `executor_code`, `executor_name`, `executor_type`, `address`, `status`, `heartbeat_at`,
  `remark`, `created_by`, `updated_by`, `deleted`
)
SELECT t.`id`, 'local-default', '本地执行器', 'LOCAL', '127.0.0.1', 'ENABLED', NOW(3),
       '租户默认本地任务执行器', 1, 1, 0
FROM `plat_tenant` t
WHERE t.`id` > 1 AND t.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `sched_executor` ex WHERE ex.`tenant_id` = t.`id` AND ex.`executor_code` = 'local-default' AND ex.`deleted` = 0
  );

-- 6. AI 供应商目录（密钥严格置空）、大模型目录、Prompt 与工具白名单基线
INSERT INTO `ai_provider` (
  `tenant_id`, `provider_code`, `provider_name`, `provider_type`, `base_url`, `api_key`,
  `status`, `is_default`, `sort_no`, `remark`, `created_by`, `updated_by`, `deleted`
)
SELECT t.`id`, s.`provider_code`, s.`provider_name`, s.`provider_type`, s.`base_url`, '',
       s.`status`, s.`is_default`, s.`sort_no`, s.`remark`, 1, 1, 0
FROM `plat_tenant` t
INNER JOIN `ai_provider` s ON s.`tenant_id` = 1 AND s.`deleted` = 0
WHERE t.`id` > 1 AND t.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `ai_provider` ex WHERE ex.`tenant_id` = t.`id` AND ex.`provider_code` = s.`provider_code` AND ex.`deleted` = 0
  );

INSERT INTO `ai_model_config` (
  `tenant_id`, `provider_id`, `model_id`, `model_name`, `category`, `context_window`, `max_tokens`,
  `temperature`, `is_default`, `status`, `sort_no`, `created_by`, `updated_by`, `deleted`
)
SELECT t.`id`, tgt_p.`id`, s.`model_id`, s.`model_name`, s.`category`, s.`context_window`, s.`max_tokens`,
       s.`temperature`, s.`is_default`, s.`status`, s.`sort_no`, 1, 1, 0
FROM `plat_tenant` t
INNER JOIN `ai_model_config` s ON s.`tenant_id` = 1 AND s.`deleted` = 0
INNER JOIN `ai_provider` src_p ON src_p.`id` = s.`provider_id` AND src_p.`tenant_id` = 1 AND src_p.`deleted` = 0
INNER JOIN `ai_provider` tgt_p ON tgt_p.`tenant_id` = t.`id` AND tgt_p.`provider_code` = src_p.`provider_code` AND tgt_p.`deleted` = 0
WHERE t.`id` > 1 AND t.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `ai_model_config` ex WHERE ex.`tenant_id` = t.`id` AND ex.`model_id` = s.`model_id` AND ex.`deleted` = 0
  );

INSERT INTO `ai_prompt` (
  `tenant_id`, `prompt_code`, `prompt_name`, `category`, `active_version`, `status`, `remark`,
  `created_by`, `updated_by`, `deleted`
)
SELECT t.`id`, s.`prompt_code`, s.`prompt_name`, s.`category`, s.`active_version`, s.`status`, s.`remark`, 1, 1, 0
FROM `plat_tenant` t
INNER JOIN `ai_prompt` s ON s.`tenant_id` = 1 AND s.`deleted` = 0
WHERE t.`id` > 1 AND t.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `ai_prompt` ex WHERE ex.`tenant_id` = t.`id` AND ex.`prompt_code` = s.`prompt_code` AND ex.`deleted` = 0
  );

INSERT INTO `ai_prompt_version` (
  `tenant_id`, `prompt_id`, `version_no`, `system_prompt`, `user_template`, `variables_json`,
  `model_hint`, `changelog`, `is_published`, `remark`, `created_by`, `updated_by`, `deleted`
)
SELECT t.`id`, tgt_pr.`id`, s.`version_no`, s.`system_prompt`, s.`user_template`, s.`variables_json`,
       s.`model_hint`, s.`changelog`, s.`is_published`, s.`remark`, 1, 1, 0
FROM `plat_tenant` t
INNER JOIN `ai_prompt_version` s ON s.`tenant_id` = 1 AND s.`deleted` = 0
INNER JOIN `ai_prompt` src_pr ON src_pr.`id` = s.`prompt_id` AND src_pr.`tenant_id` = 1 AND src_pr.`deleted` = 0
INNER JOIN `ai_prompt` tgt_pr ON tgt_pr.`tenant_id` = t.`id` AND tgt_pr.`prompt_code` = src_pr.`prompt_code` AND tgt_pr.`deleted` = 0
WHERE t.`id` > 1 AND t.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `ai_prompt_version` ex
    WHERE ex.`tenant_id` = t.`id` AND ex.`prompt_id` = tgt_pr.`id` AND ex.`version_no` = s.`version_no` AND ex.`deleted` = 0
  );

INSERT INTO `ai_tool` (
  `tenant_id`, `tool_code`, `tool_name`, `semantic_version`, `description`, `input_schema_json`,
  `output_schema_json`, `permission_code`, `risk_level`, `adapter_type`, `mcp_server_id`,
  `whitelisted`, `idempotent`, `timeout_ms`, `status`, `remark`, `created_by`, `updated_by`, `deleted`
)
SELECT t.`id`, s.`tool_code`, s.`tool_name`, s.`semantic_version`, s.`description`, s.`input_schema_json`,
       s.`output_schema_json`, s.`permission_code`, s.`risk_level`, s.`adapter_type`, NULL,
       s.`whitelisted`, s.`idempotent`, s.`timeout_ms`, s.`status`, s.`remark`, 1, 1, 0
FROM `plat_tenant` t
INNER JOIN `ai_tool` s ON s.`tenant_id` = 1 AND s.`deleted` = 0
WHERE t.`id` > 1 AND t.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `ai_tool` ex WHERE ex.`tenant_id` = t.`id` AND ex.`tool_code` = s.`tool_code` AND ex.`deleted` = 0
  );

-- 7. 存量非平台租户默认组织架构与员工绑定补齐
INSERT INTO `iam_department` (
  `tenant_id`, `parent_id`, `dept_code`, `dept_name`, `dept_full_name`, `leader_user_id`, `status`,
  `sort_no`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `version`, `remark`
)
SELECT t.`id`, 0, CONCAT(t.`tenant_code`, '_hq'), CONCAT(t.`tenant_name`, '总部'), CONCAT(t.`tenant_name`, '总部'),
       0, 'ENABLED', 1, 1, NOW(3), 1, NOW(3), 0, 0, 'V78 tenant baseline dept'
FROM `plat_tenant` t
WHERE t.`id` > 1 AND t.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `iam_department` d WHERE d.`tenant_id` = t.`id` AND d.`deleted` = 0
  );

UPDATE `iam_department`
SET `tree_path` = CONCAT('/', `id`, '/')
WHERE `tenant_id` > 1 AND `parent_id` = 0 AND `tree_path` IS NULL AND `deleted` = 0;

INSERT INTO `iam_position` (
  `tenant_id`, `position_code`, `position_name`, `position_level`, `status`,
  `sort_no`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `version`, `remark`
)
SELECT t.`id`, CONCAT(t.`tenant_code`, '_admin_pos'), '系统管理员', 'M1', 'ENABLED',
       1, 1, NOW(3), 1, NOW(3), 0, 0, 'V78 tenant baseline position'
FROM `plat_tenant` t
WHERE t.`id` > 1 AND t.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `iam_position` p WHERE p.`tenant_id` = t.`id` AND p.`deleted` = 0
  );

INSERT INTO `iam_employee` (
  `tenant_id`, `employee_no`, `employee_name`, `dept_id`, `position_id`, `employee_status`,
  `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `version`, `remark`
)
SELECT t.`id`, 'EMP-0001', CONCAT(t.`tenant_name`, '管理员'),
       (SELECT MIN(d.`id`) FROM `iam_department` d WHERE d.`tenant_id` = t.`id` AND d.`deleted` = 0),
       (SELECT MIN(p.`id`) FROM `iam_position` p WHERE p.`tenant_id` = t.`id` AND p.`deleted` = 0),
       'ACTIVE', 1, NOW(3), 1, NOW(3), 0, 0, 'V78 tenant baseline employee'
FROM `plat_tenant` t
WHERE t.`id` > 1 AND t.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `iam_employee` e WHERE e.`tenant_id` = t.`id` AND e.`deleted` = 0
  );

UPDATE `iam_user` u
INNER JOIN `iam_employee` e ON e.`tenant_id` = u.`tenant_id` AND e.`employee_no` = 'EMP-0001' AND e.`deleted` = 0
SET u.`employee_id` = e.`id`
WHERE u.`tenant_id` > 1 AND u.`user_code` = 'TENANT_ADMIN' AND u.`employee_id` = 0 AND u.`deleted` = 0;
