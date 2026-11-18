-- =====================================================================
-- V79__crm_soft_delete_and_clean_data.sql
-- 1. 补齐 CRM 模块各实体 deleted_at 软删除时间戳字段，与全局回收站与审计规范深度对齐
-- 2. 幂等修复历史字符集残留的 CRM 商机客户关联乱码
-- =====================================================================
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- 1. 为 CRM 各业务表补齐 deleted_at 字段
ALTER TABLE `crm_business` ADD COLUMN `deleted_at` DATETIME NULL DEFAULT NULL AFTER `deleted`;
ALTER TABLE `crm_clue` ADD COLUMN `deleted_at` DATETIME NULL DEFAULT NULL AFTER `deleted`;
ALTER TABLE `crm_customer` ADD COLUMN `deleted_at` DATETIME NULL DEFAULT NULL AFTER `deleted`;
ALTER TABLE `crm_contract` ADD COLUMN `deleted_at` DATETIME NULL DEFAULT NULL AFTER `deleted`;
ALTER TABLE `crm_receivable` ADD COLUMN `deleted_at` DATETIME NULL DEFAULT NULL AFTER `deleted`;
ALTER TABLE `crm_follow_up_record` ADD COLUMN `deleted_at` DATETIME NULL DEFAULT NULL AFTER `deleted`;

-- 2. 幂等修复商机客户关联乱码数据
UPDATE `crm_business` 
SET `customer_name` = '深圳创越数智供应链管理集团' 
WHERE `id` = 3 AND (`customer_name` LIKE '%Ã%' OR `customer_name` LIKE '%æ·±%' OR `customer_name` IS NULL);

UPDATE `crm_business` 
SET `customer_name` = '苏州汇能新能源动力科技有限公司' 
WHERE `id` = 4 AND (`customer_name` LIKE '%Ã%' OR `customer_name` LIKE '%è‹%' OR `customer_name` IS NULL);
