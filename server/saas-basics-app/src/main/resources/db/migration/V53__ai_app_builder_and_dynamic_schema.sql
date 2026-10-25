-- V53__ai_app_builder_and_dynamic_schema.sql
-- Enterprise SaaS AI App Builder: Natural Language to Application & Dynamic Schema Engine

CREATE TABLE IF NOT EXISTS `gen_dynamic_model` (
    `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '租户ID',
    `model_code` VARCHAR(64) NOT NULL COMMENT '模型标识(如 meeting_room_booking)',
    `model_name` VARCHAR(128) NOT NULL COMMENT '应用/业务模型名称(如 会议室预约系统)',
    `table_name` VARCHAR(64) NOT NULL COMMENT '物理数据表名称',
    `category` VARCHAR(64) NOT NULL DEFAULT 'BUSINESS' COMMENT '应用分类(COLLABORATION/BUSINESS/OFFICE/SERVICE)',
    `icon` VARCHAR(64) NOT NULL DEFAULT 'Document' COMMENT '应用图标',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '功能描述与需求意图',
    `menu_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '挂载的菜单ID',
    `status` VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED' COMMENT '状态(PUBLISHED/DISABLED)',
    `config_json` TEXT DEFAULT NULL COMMENT 'UI配置与扩展JSON',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注说明',
    `created_by` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_by` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    `deleted_at` DATETIME(3) DEFAULT NULL,
    `version` INT UNSIGNED NOT NULL DEFAULT 0,
    UNIQUE KEY `uk_dyn_model_code` (`tenant_id`, `model_code`, (CASE WHEN `deleted` = 0 THEN 0 ELSE NULL END))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI动态构建应用主模型表';

CREATE TABLE IF NOT EXISTS `gen_dynamic_field` (
    `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '租户ID',
    `model_id` BIGINT UNSIGNED NOT NULL COMMENT '所属动态模型ID',
    `field_code` VARCHAR(64) NOT NULL COMMENT '字段列名(如 room_name)',
    `field_name` VARCHAR(128) NOT NULL COMMENT '字段中文名(如 会议室名称)',
    `column_type` VARCHAR(64) NOT NULL DEFAULT 'VARCHAR(255)' COMMENT '数据库列定义',
    `java_type` VARCHAR(32) NOT NULL DEFAULT 'String' COMMENT 'Java对应类型',
    `component_type` VARCHAR(64) NOT NULL DEFAULT 'input' COMMENT '组件类型(input/textarea/select/date/datetime/number)',
    `is_required` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否必填',
    `is_query` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否作为检索条件',
    `query_type` VARCHAR(32) NOT NULL DEFAULT 'LIKE' COMMENT '检索匹配规则(LIKE/EQ/BETWEEN)',
    `dict_code` VARCHAR(64) DEFAULT NULL COMMENT '绑定字典编码',
    `options_json` TEXT DEFAULT NULL COMMENT '静态枚举下拉列表JSON',
    `sort_no` INT NOT NULL DEFAULT 10 COMMENT '展示排序',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    INDEX `idx_dyn_model_field` (`model_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI动态模型字段定义表';

-- 预置 AI 智能应用工坊菜单项到代码生成中心
INSERT INTO `iam_menu` (
  `tenant_id`, `parent_id`, `menu_type`, `menu_code`, `app_code`, `menu_name`,
  `route_path`, `component_path`, `permission_code`, `icon`, `sort_no`,
  `status`, `visible`, `keep_alive`
)
SELECT
  1, 12, 'MENU', 'codegen_ai_builder', 'codegen', 'AI 智能应用工坊',
  '/codegen/ai-builder', '@/views/codegen/CodegenView.vue', 'codegen:project:query', 'MagicStick', 5,
  'ENABLED', 1, 1
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM `iam_menu` WHERE `menu_code` = 'codegen_ai_builder' AND `tenant_id` = 1 AND `deleted` = 0
);

INSERT IGNORE INTO `iam_role_menu` (`tenant_id`, `role_id`, `menu_id`)
SELECT 1, 1, `id` FROM `iam_menu` WHERE `menu_code` = 'codegen_ai_builder' AND `tenant_id` = 1 AND `deleted` = 0;
