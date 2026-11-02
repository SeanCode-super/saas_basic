-- ==============================================================================
-- V61__erp_inventory_unique_index.sql
-- ERP 库存记录唯一性索引加固与历史重复项清洗
-- ==============================================================================

-- 1. 清理历史可能存在的重复库存记录，仅保留最大 ID 记录
DELETE i1 FROM `erp_inventory` i1
INNER JOIN `erp_inventory` i2
WHERE i1.tenant_id = i2.tenant_id
  AND i1.product_id = i2.product_id
  AND i1.warehouse_id = i2.warehouse_id
  AND i1.id < i2.id;

-- 2. 建立针对 (tenant_id, product_id, warehouse_id) 的唯一性防线
-- 先安全移除原有非唯一索引（若存在）
SET @drop_idx = (
    SELECT IF(
        EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'erp_inventory' AND index_name = 'idx_erp_inv_lookup'),
        'ALTER TABLE `erp_inventory` DROP INDEX `idx_erp_inv_lookup`;',
        'SELECT 1;'
    )
);
PREPARE stmt FROM @drop_idx;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加唯一键（若不存在）
SET @add_uk = (
    SELECT IF(
        NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'erp_inventory' AND index_name = 'uk_erp_inv_product_wh'),
        'ALTER TABLE `erp_inventory` ADD UNIQUE KEY `uk_erp_inv_product_wh` (`tenant_id`, `product_id`, `warehouse_id`);',
        'SELECT 1;'
    )
);
PREPARE stmt FROM @add_uk;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
