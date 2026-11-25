-- ==============================================================================
-- V83__platform_qa_regression_fixes.sql
-- 平台质量回归问题修复与标准元数据治理
-- ==============================================================================

-- 1. QA-03: sec_blacklist 补齐 BaseEntity 中的 remark 字段，防止 ORM 查询 500
ALTER TABLE `sec_blacklist` ADD COLUMN `remark` VARCHAR(500) NULL AFTER `reason`;

-- 2. QA-04: pay_order 扩大二维码字段为 TEXT，支持 SVG Data URI 与离线二维码
ALTER TABLE `pay_order` MODIFY COLUMN `qr_code_url` TEXT NULL COMMENT '支付二维码URL或Base64/SVG数据';

-- 3. QA-06: 规范平台租户菜单拓扑结构（父级目录必须为 DIRECTORY，禁止 MENU 嵌套子 MENU）
UPDATE `iam_menu`
SET `menu_type` = 'DIRECTORY',
    `component_path` = NULL,
    `route_path` = '/tenant'
WHERE `menu_code` = 'tenant' AND `menu_type` <> 'DIRECTORY';

-- 清理挂载在 DIRECTORY 目录层级上的非法细粒度权限关联
DELETE p FROM `iam_menu_permission` p
INNER JOIN `iam_menu` m ON m.id = p.menu_id
WHERE m.menu_type = 'DIRECTORY';
