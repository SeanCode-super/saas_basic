-- =====================================================================
-- V77__enterprise_isolation_hardening_and_dept_scope.sql
-- 企业级多租户共享池三维隔离加固：
--   1. CRM / ERP 业务表补齐 dept_id 与 (tenant_id, dept_id) 索引并回填归属部门
--   2. 文件存储默认路径升级为 t_{tenantId}/... 租户目录前缀
--   3. system_notify_template 补齐 tenant_id 与多租户联合唯一索引
--   4. 全库排查补齐业务编码多租户联合唯一约束（严禁改动门户默认与令牌全局索引）
-- =====================================================================
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- ---------------------------------------------------------------------
-- 1. CRM & ERP 业务表幂等新增 dept_id / created_by 与 (tenant_id, dept_id) 索引
-- ---------------------------------------------------------------------
SET @v77_sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'crm_clue' AND column_name = 'dept_id'),
    'SELECT 1',
    'ALTER TABLE `crm_clue` ADD COLUMN `dept_id` BIGINT DEFAULT NULL COMMENT ''归属部门ID'' AFTER `owner_user_id`'
));
PREPARE stmt FROM @v77_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @v77_sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'crm_clue' AND index_name = 'idx_crm_clue_tenant_dept'),
    'SELECT 1',
    'ALTER TABLE `crm_clue` ADD INDEX `idx_crm_clue_tenant_dept` (`tenant_id`, `dept_id`)'
));
PREPARE stmt FROM @v77_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @v77_sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'crm_customer' AND column_name = 'dept_id'),
    'SELECT 1',
    'ALTER TABLE `crm_customer` ADD COLUMN `dept_id` BIGINT DEFAULT NULL COMMENT ''归属部门ID'' AFTER `owner_user_id`'
));
PREPARE stmt FROM @v77_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @v77_sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'crm_customer' AND index_name = 'idx_crm_cust_tenant_dept'),
    'SELECT 1',
    'ALTER TABLE `crm_customer` ADD INDEX `idx_crm_cust_tenant_dept` (`tenant_id`, `dept_id`)'
));
PREPARE stmt FROM @v77_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @v77_sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'crm_business' AND column_name = 'dept_id'),
    'SELECT 1',
    'ALTER TABLE `crm_business` ADD COLUMN `dept_id` BIGINT DEFAULT NULL COMMENT ''归属部门ID'' AFTER `owner_user_id`'
));
PREPARE stmt FROM @v77_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @v77_sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'crm_business' AND index_name = 'idx_crm_biz_tenant_dept'),
    'SELECT 1',
    'ALTER TABLE `crm_business` ADD INDEX `idx_crm_biz_tenant_dept` (`tenant_id`, `dept_id`)'
));
PREPARE stmt FROM @v77_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @v77_sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'crm_contract' AND column_name = 'dept_id'),
    'SELECT 1',
    'ALTER TABLE `crm_contract` ADD COLUMN `dept_id` BIGINT DEFAULT NULL COMMENT ''归属部门ID'' AFTER `signer_user_name`'
));
PREPARE stmt FROM @v77_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @v77_sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'crm_contract' AND index_name = 'idx_crm_ct_tenant_dept'),
    'SELECT 1',
    'ALTER TABLE `crm_contract` ADD INDEX `idx_crm_ct_tenant_dept` (`tenant_id`, `dept_id`)'
));
PREPARE stmt FROM @v77_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @v77_sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'crm_receivable' AND column_name = 'dept_id'),
    'SELECT 1',
    'ALTER TABLE `crm_receivable` ADD COLUMN `dept_id` BIGINT DEFAULT NULL COMMENT ''归属部门ID'' AFTER `status`'
));
PREPARE stmt FROM @v77_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @v77_sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'crm_receivable' AND index_name = 'idx_crm_rec_tenant_dept'),
    'SELECT 1',
    'ALTER TABLE `crm_receivable` ADD INDEX `idx_crm_rec_tenant_dept` (`tenant_id`, `dept_id`)'
));
PREPARE stmt FROM @v77_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @v77_sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'crm_follow_up_record' AND column_name = 'dept_id'),
    'SELECT 1',
    'ALTER TABLE `crm_follow_up_record` ADD COLUMN `dept_id` BIGINT DEFAULT NULL COMMENT ''归属部门ID'' AFTER `contact_user_name`'
));
PREPARE stmt FROM @v77_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @v77_sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'crm_follow_up_record' AND index_name = 'idx_crm_fur_tenant_dept'),
    'SELECT 1',
    'ALTER TABLE `crm_follow_up_record` ADD INDEX `idx_crm_fur_tenant_dept` (`tenant_id`, `dept_id`)'
));
PREPARE stmt FROM @v77_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @v77_sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'erp_product' AND column_name = 'dept_id'),
    'SELECT 1',
    'ALTER TABLE `erp_product` ADD COLUMN `dept_id` BIGINT DEFAULT NULL COMMENT ''归属部门ID'' AFTER `status`'
));
PREPARE stmt FROM @v77_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @v77_sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'erp_product' AND index_name = 'idx_erp_prod_tenant_dept'),
    'SELECT 1',
    'ALTER TABLE `erp_product` ADD INDEX `idx_erp_prod_tenant_dept` (`tenant_id`, `dept_id`)'
));
PREPARE stmt FROM @v77_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @v77_sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'erp_purchase_order' AND column_name = 'dept_id'),
    'SELECT 1',
    'ALTER TABLE `erp_purchase_order` ADD COLUMN `dept_id` BIGINT DEFAULT NULL COMMENT ''归属部门ID'' AFTER `status`'
));
PREPARE stmt FROM @v77_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @v77_sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'erp_purchase_order' AND index_name = 'idx_erp_po_tenant_dept'),
    'SELECT 1',
    'ALTER TABLE `erp_purchase_order` ADD INDEX `idx_erp_po_tenant_dept` (`tenant_id`, `dept_id`)'
));
PREPARE stmt FROM @v77_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @v77_sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'erp_sale_order' AND column_name = 'dept_id'),
    'SELECT 1',
    'ALTER TABLE `erp_sale_order` ADD COLUMN `dept_id` BIGINT DEFAULT NULL COMMENT ''归属部门ID'' AFTER `status`'
));
PREPARE stmt FROM @v77_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @v77_sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'erp_sale_order' AND index_name = 'idx_erp_so_tenant_dept'),
    'SELECT 1',
    'ALTER TABLE `erp_sale_order` ADD INDEX `idx_erp_so_tenant_dept` (`tenant_id`, `dept_id`)'
));
PREPARE stmt FROM @v77_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @v77_sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'erp_inventory_record' AND column_name = 'created_by'),
    'SELECT 1',
    'ALTER TABLE `erp_inventory_record` ADD COLUMN `created_by` BIGINT NOT NULL DEFAULT 1 COMMENT ''创建人ID'' AFTER `remark`'
));
PREPARE stmt FROM @v77_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @v77_sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'erp_inventory_record' AND column_name = 'dept_id'),
    'SELECT 1',
    'ALTER TABLE `erp_inventory_record` ADD COLUMN `dept_id` BIGINT DEFAULT NULL COMMENT ''归属部门ID'' AFTER `created_by`'
));
PREPARE stmt FROM @v77_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @v77_sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'erp_inventory_record' AND index_name = 'idx_erp_rec_tenant_dept'),
    'SELECT 1',
    'ALTER TABLE `erp_inventory_record` ADD INDEX `idx_erp_rec_tenant_dept` (`tenant_id`, `dept_id`)'
));
PREPARE stmt FROM @v77_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 存量已生成的零代码基线物理表补齐 dept_id（如已在运行期初始化过表结构）
SET @v77_sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'biz_dyn_meeting_booking')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'biz_dyn_meeting_booking' AND column_name = 'dept_id'),
    'ALTER TABLE `biz_dyn_meeting_booking` ADD COLUMN `dept_id` BIGINT UNSIGNED DEFAULT NULL COMMENT ''归属部门ID'' AFTER `created_by`, ADD INDEX `idx_tenant_dept` (`tenant_id`, `dept_id`)',
    'SELECT 1'
));
PREPARE stmt FROM @v77_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @v77_sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'biz_dyn_expense_claim')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'biz_dyn_expense_claim' AND column_name = 'dept_id'),
    'ALTER TABLE `biz_dyn_expense_claim` ADD COLUMN `dept_id` BIGINT UNSIGNED DEFAULT NULL COMMENT ''归属部门ID'' AFTER `created_by`, ADD INDEX `idx_tenant_dept` (`tenant_id`, `dept_id`)',
    'SELECT 1'
));
PREPARE stmt FROM @v77_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @v77_sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'biz_dyn_repair_ticket')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'biz_dyn_repair_ticket' AND column_name = 'dept_id'),
    'ALTER TABLE `biz_dyn_repair_ticket` ADD COLUMN `dept_id` BIGINT UNSIGNED DEFAULT NULL COMMENT ''归属部门ID'' AFTER `created_by`, ADD INDEX `idx_tenant_dept` (`tenant_id`, `dept_id`)',
    'SELECT 1'
));
PREPARE stmt FROM @v77_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @v77_sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'biz_dyn_client_visit')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'biz_dyn_client_visit' AND column_name = 'dept_id'),
    'ALTER TABLE `biz_dyn_client_visit` ADD COLUMN `dept_id` BIGINT UNSIGNED DEFAULT NULL COMMENT ''归属部门ID'' AFTER `created_by`, ADD INDEX `idx_tenant_dept` (`tenant_id`, `dept_id`)',
    'SELECT 1'
));
PREPARE stmt FROM @v77_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @v77_sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'biz_dyn_asset_inventory')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'biz_dyn_asset_inventory' AND column_name = 'dept_id'),
    'ALTER TABLE `biz_dyn_asset_inventory` ADD COLUMN `dept_id` BIGINT UNSIGNED DEFAULT NULL COMMENT ''归属部门ID'' AFTER `created_by`, ADD INDEX `idx_tenant_dept` (`tenant_id`, `dept_id`)',
    'SELECT 1'
));
PREPARE stmt FROM @v77_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @v77_sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'biz_dyn_supply_req')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'biz_dyn_supply_req' AND column_name = 'dept_id'),
    'ALTER TABLE `biz_dyn_supply_req` ADD COLUMN `dept_id` BIGINT UNSIGNED DEFAULT NULL COMMENT ''归属部门ID'' AFTER `created_by`, ADD INDEX `idx_tenant_dept` (`tenant_id`, `dept_id`)',
    'SELECT 1'
));
PREPARE stmt FROM @v77_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @v77_sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'biz_dyn_equipment_inspection')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'biz_dyn_equipment_inspection' AND column_name = 'dept_id'),
    'ALTER TABLE `biz_dyn_equipment_inspection` ADD COLUMN `dept_id` BIGINT UNSIGNED DEFAULT NULL COMMENT ''归属部门ID'' AFTER `created_by`, ADD INDEX `idx_tenant_dept` (`tenant_id`, `dept_id`)',
    'SELECT 1'
));
PREPARE stmt FROM @v77_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 2. 回填存量 CRM / ERP 业务记录的 dept_id（公海客户保持归属与部门为空）
-- ---------------------------------------------------------------------
UPDATE `crm_clue` c
LEFT JOIN `iam_user` u ON u.`tenant_id` = c.`tenant_id` AND u.`id` = COALESCE(c.`owner_user_id`, c.`created_by`) AND u.`deleted` = 0
LEFT JOIN `iam_employee` e ON e.`tenant_id` = u.`tenant_id` AND e.`id` = u.`employee_id` AND e.`deleted` = 0
SET c.`dept_id` = COALESCE(
    NULLIF(e.`dept_id`, 0),
    (SELECT MIN(d.`id`) FROM `iam_department` d WHERE d.`tenant_id` = c.`tenant_id` AND d.`deleted` = 0)
)
WHERE c.`dept_id` IS NULL;

UPDATE `crm_customer` c
LEFT JOIN `iam_user` u ON u.`tenant_id` = c.`tenant_id` AND u.`id` = COALESCE(c.`owner_user_id`, c.`created_by`) AND u.`deleted` = 0
LEFT JOIN `iam_employee` e ON e.`tenant_id` = u.`tenant_id` AND e.`id` = u.`employee_id` AND e.`deleted` = 0
SET c.`dept_id` = COALESCE(
    NULLIF(e.`dept_id`, 0),
    (SELECT MIN(d.`id`) FROM `iam_department` d WHERE d.`tenant_id` = c.`tenant_id` AND d.`deleted` = 0)
)
WHERE c.`is_pool` = 0 AND c.`dept_id` IS NULL;

UPDATE `crm_customer`
SET `owner_user_id` = NULL,
    `dept_id` = NULL
WHERE `is_pool` = 1;

UPDATE `crm_business` b
LEFT JOIN `iam_user` u ON u.`tenant_id` = b.`tenant_id` AND u.`id` = COALESCE(b.`owner_user_id`, b.`created_by`) AND u.`deleted` = 0
LEFT JOIN `iam_employee` e ON e.`tenant_id` = u.`tenant_id` AND e.`id` = u.`employee_id` AND e.`deleted` = 0
SET b.`dept_id` = COALESCE(
    NULLIF(e.`dept_id`, 0),
    (SELECT MIN(d.`id`) FROM `iam_department` d WHERE d.`tenant_id` = b.`tenant_id` AND d.`deleted` = 0)
)
WHERE b.`dept_id` IS NULL;

UPDATE `crm_contract` c
LEFT JOIN `iam_user` u ON u.`tenant_id` = c.`tenant_id` AND u.`id` = c.`created_by` AND u.`deleted` = 0
LEFT JOIN `iam_employee` e ON e.`tenant_id` = u.`tenant_id` AND e.`id` = u.`employee_id` AND e.`deleted` = 0
SET c.`dept_id` = COALESCE(
    NULLIF(e.`dept_id`, 0),
    (SELECT MIN(d.`id`) FROM `iam_department` d WHERE d.`tenant_id` = c.`tenant_id` AND d.`deleted` = 0)
)
WHERE c.`dept_id` IS NULL;

UPDATE `crm_receivable` r
LEFT JOIN `iam_user` u ON u.`tenant_id` = r.`tenant_id` AND u.`id` = r.`created_by` AND u.`deleted` = 0
LEFT JOIN `iam_employee` e ON e.`tenant_id` = u.`tenant_id` AND e.`id` = u.`employee_id` AND e.`deleted` = 0
SET r.`dept_id` = COALESCE(
    NULLIF(e.`dept_id`, 0),
    (SELECT MIN(d.`id`) FROM `iam_department` d WHERE d.`tenant_id` = r.`tenant_id` AND d.`deleted` = 0)
)
WHERE r.`dept_id` IS NULL;

UPDATE `crm_follow_up_record` f
LEFT JOIN `iam_user` u ON u.`tenant_id` = f.`tenant_id` AND u.`id` = f.`created_by` AND u.`deleted` = 0
LEFT JOIN `iam_employee` e ON e.`tenant_id` = u.`tenant_id` AND e.`id` = u.`employee_id` AND e.`deleted` = 0
SET f.`dept_id` = COALESCE(
    NULLIF(e.`dept_id`, 0),
    (SELECT MIN(d.`id`) FROM `iam_department` d WHERE d.`tenant_id` = f.`tenant_id` AND d.`deleted` = 0)
)
WHERE f.`dept_id` IS NULL;

UPDATE `erp_product` p
LEFT JOIN `iam_user` u ON u.`tenant_id` = p.`tenant_id` AND u.`id` = p.`created_by` AND u.`deleted` = 0
LEFT JOIN `iam_employee` e ON e.`tenant_id` = u.`tenant_id` AND e.`id` = u.`employee_id` AND e.`deleted` = 0
SET p.`dept_id` = COALESCE(
    NULLIF(e.`dept_id`, 0),
    (SELECT MIN(d.`id`) FROM `iam_department` d WHERE d.`tenant_id` = p.`tenant_id` AND d.`deleted` = 0)
)
WHERE p.`dept_id` IS NULL;

UPDATE `erp_purchase_order` o
LEFT JOIN `iam_user` u ON u.`tenant_id` = o.`tenant_id` AND u.`id` = o.`created_by` AND u.`deleted` = 0
LEFT JOIN `iam_employee` e ON e.`tenant_id` = u.`tenant_id` AND e.`id` = u.`employee_id` AND e.`deleted` = 0
SET o.`dept_id` = COALESCE(
    NULLIF(e.`dept_id`, 0),
    (SELECT MIN(d.`id`) FROM `iam_department` d WHERE d.`tenant_id` = o.`tenant_id` AND d.`deleted` = 0)
)
WHERE o.`dept_id` IS NULL;

UPDATE `erp_sale_order` o
LEFT JOIN `iam_user` u ON u.`tenant_id` = o.`tenant_id` AND u.`id` = o.`created_by` AND u.`deleted` = 0
LEFT JOIN `iam_employee` e ON e.`tenant_id` = u.`tenant_id` AND e.`id` = u.`employee_id` AND e.`deleted` = 0
SET o.`dept_id` = COALESCE(
    NULLIF(e.`dept_id`, 0),
    (SELECT MIN(d.`id`) FROM `iam_department` d WHERE d.`tenant_id` = o.`tenant_id` AND d.`deleted` = 0)
)
WHERE o.`dept_id` IS NULL;

UPDATE `erp_inventory_record` r
LEFT JOIN `iam_user` u ON u.`tenant_id` = r.`tenant_id` AND u.`id` = r.`created_by` AND u.`deleted` = 0
LEFT JOIN `iam_employee` e ON e.`tenant_id` = u.`tenant_id` AND e.`id` = u.`employee_id` AND e.`deleted` = 0
SET r.`dept_id` = COALESCE(
    NULLIF(e.`dept_id`, 0),
    (SELECT MIN(d.`id`) FROM `iam_department` d WHERE d.`tenant_id` = r.`tenant_id` AND d.`deleted` = 0)
)
WHERE r.`dept_id` IS NULL;

-- ---------------------------------------------------------------------
-- 3. 文件存储与路由默认路径策略升级为租户目录隔离前缀 t_{tenantId}/...
-- ---------------------------------------------------------------------
UPDATE `file_storage`
SET `path_pattern` = 't_{tenantId}/{folder}/{yyyy}/{MM}/{uuid}_{filename}'
WHERE `path_pattern` IS NULL
   OR `path_pattern` = ''
   OR `path_pattern` = '{folder}/{yyyy}/{MM}/{uuid}_{filename}';

UPDATE `file_upload_route`
SET `path_pattern` = 't_{tenantId}/{folder}/{yyyy}/{MM}/{uuid}_{filename}'
WHERE `path_pattern` IS NULL
   OR `path_pattern` = ''
   OR `path_pattern` = '{folder}/{yyyy}/{MM}/{uuid}_{filename}';

-- ---------------------------------------------------------------------
-- 4. system_notify_template 补齐租户隔离字段与 (tenant_id, template_code) 联合唯一约束
-- ---------------------------------------------------------------------
SET @v77_sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'system_notify_template' AND column_name = 'tenant_id'),
    'SELECT 1',
    'ALTER TABLE `system_notify_template` ADD COLUMN `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''租户ID'' AFTER `id`'
));
PREPARE stmt FROM @v77_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @v77_sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'system_notify_template' AND column_name = 'deleted'),
    'SELECT 1',
    'ALTER TABLE `system_notify_template` ADD COLUMN `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''逻辑删除标识'' AFTER `remark`'
));
PREPARE stmt FROM @v77_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @v77_sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'system_notify_template' AND index_name = 'uk_tpl_code'),
    'ALTER TABLE `system_notify_template` DROP INDEX `uk_tpl_code`',
    'SELECT 1'
));
PREPARE stmt FROM @v77_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @v77_sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'system_notify_template' AND index_name = 'uk_notify_tpl_tenant_code'),
    'SELECT 1',
    'ALTER TABLE `system_notify_template` ADD UNIQUE KEY `uk_notify_tpl_tenant_code` (`tenant_id`, `template_code`, (CASE WHEN `deleted` = 0 THEN 0 ELSE NULL END))'
));
PREPARE stmt FROM @v77_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO `system_notify_template` (
    `tenant_id`, `template_code`, `template_name`, `template_type`,
    `title_template`, `content_template`, `status`, `remark`, `deleted`
)
SELECT
    t.`id`, base.`template_code`, base.`template_name`, base.`template_type`,
    base.`title_template`, base.`content_template`, base.`status`, base.`remark`, 0
FROM `plat_tenant` t
INNER JOIN `system_notify_template` base
    ON base.`tenant_id` = 1 AND base.`deleted` = 0
WHERE t.`id` > 1
  AND t.`deleted` = 0
  AND NOT EXISTS (
      SELECT 1 FROM `system_notify_template` ex
      WHERE ex.`tenant_id` = t.`id`
        AND ex.`template_code` = base.`template_code`
        AND ex.`deleted` = 0
  );

-- ---------------------------------------------------------------------
-- 5. 多租户业务表编码联合唯一约束补齐（先清洗同租户历史重码，再加唯一索引）
-- ---------------------------------------------------------------------
UPDATE `crm_customer` c
INNER JOIN (
    SELECT `id` FROM (
        SELECT `id`, ROW_NUMBER() OVER (PARTITION BY `tenant_id`, `customer_code` ORDER BY `id` ASC) AS rn
        FROM `crm_customer`
        WHERE `deleted` = 0
    ) ranked
    WHERE ranked.rn > 1
) dup ON dup.`id` = c.`id`
SET c.`customer_code` = CONCAT(LEFT(c.`customer_code`, 48), '-D', c.`id`);

SET @v77_sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'crm_customer' AND index_name = 'uk_crm_customer_tenant_code'),
    'SELECT 1',
    'ALTER TABLE `crm_customer` ADD UNIQUE KEY `uk_crm_customer_tenant_code` (`tenant_id`, `customer_code`, (CASE WHEN `deleted` = 0 THEN 0 ELSE NULL END))'
));
PREPARE stmt FROM @v77_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `crm_business` b
INNER JOIN (
    SELECT `id` FROM (
        SELECT `id`, ROW_NUMBER() OVER (PARTITION BY `tenant_id`, `business_code` ORDER BY `id` ASC) AS rn
        FROM `crm_business`
        WHERE `deleted` = 0
    ) ranked
    WHERE ranked.rn > 1
) dup ON dup.`id` = b.`id`
SET b.`business_code` = CONCAT(LEFT(b.`business_code`, 48), '-D', b.`id`);

SET @v77_sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'crm_business' AND index_name = 'uk_crm_business_tenant_code'),
    'SELECT 1',
    'ALTER TABLE `crm_business` ADD UNIQUE KEY `uk_crm_business_tenant_code` (`tenant_id`, `business_code`, (CASE WHEN `deleted` = 0 THEN 0 ELSE NULL END))'
));
PREPARE stmt FROM @v77_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `crm_contract` c
INNER JOIN (
    SELECT `id` FROM (
        SELECT `id`, ROW_NUMBER() OVER (PARTITION BY `tenant_id`, `contract_no` ORDER BY `id` ASC) AS rn
        FROM `crm_contract`
        WHERE `deleted` = 0
    ) ranked
    WHERE ranked.rn > 1
) dup ON dup.`id` = c.`id`
SET c.`contract_no` = CONCAT(LEFT(c.`contract_no`, 48), '-D', c.`id`);

SET @v77_sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'crm_contract' AND index_name = 'uk_crm_contract_tenant_no'),
    'SELECT 1',
    'ALTER TABLE `crm_contract` ADD UNIQUE KEY `uk_crm_contract_tenant_no` (`tenant_id`, `contract_no`, (CASE WHEN `deleted` = 0 THEN 0 ELSE NULL END))'
));
PREPARE stmt FROM @v77_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `crm_receivable` r
INNER JOIN (
    SELECT `id` FROM (
        SELECT `id`, ROW_NUMBER() OVER (PARTITION BY `tenant_id`, `receivable_no` ORDER BY `id` ASC) AS rn
        FROM `crm_receivable`
        WHERE `deleted` = 0
    ) ranked
    WHERE ranked.rn > 1
) dup ON dup.`id` = r.`id`
SET r.`receivable_no` = CONCAT(LEFT(r.`receivable_no`, 48), '-D', r.`id`);

SET @v77_sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'crm_receivable' AND index_name = 'uk_crm_receivable_tenant_no'),
    'SELECT 1',
    'ALTER TABLE `crm_receivable` ADD UNIQUE KEY `uk_crm_receivable_tenant_no` (`tenant_id`, `receivable_no`, (CASE WHEN `deleted` = 0 THEN 0 ELSE NULL END))'
));
PREPARE stmt FROM @v77_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `erp_purchase_order` o
INNER JOIN (
    SELECT `id` FROM (
        SELECT `id`, ROW_NUMBER() OVER (PARTITION BY `tenant_id`, `order_no` ORDER BY `id` ASC) AS rn
        FROM `erp_purchase_order`
        WHERE `deleted` = 0
    ) ranked
    WHERE ranked.rn > 1
) dup ON dup.`id` = o.`id`
SET o.`order_no` = CONCAT(LEFT(o.`order_no`, 48), '-D', o.`id`);

SET @v77_sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'erp_purchase_order' AND index_name = 'uk_erp_po_tenant_no'),
    'SELECT 1',
    'ALTER TABLE `erp_purchase_order` ADD UNIQUE KEY `uk_erp_po_tenant_no` (`tenant_id`, `order_no`, (CASE WHEN `deleted` = 0 THEN 0 ELSE NULL END))'
));
PREPARE stmt FROM @v77_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `erp_sale_order` o
INNER JOIN (
    SELECT `id` FROM (
        SELECT `id`, ROW_NUMBER() OVER (PARTITION BY `tenant_id`, `order_no` ORDER BY `id` ASC) AS rn
        FROM `erp_sale_order`
        WHERE `deleted` = 0
    ) ranked
    WHERE ranked.rn > 1
) dup ON dup.`id` = o.`id`
SET o.`order_no` = CONCAT(LEFT(o.`order_no`, 48), '-D', o.`id`);

SET @v77_sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'erp_sale_order' AND index_name = 'uk_erp_so_tenant_no'),
    'SELECT 1',
    'ALTER TABLE `erp_sale_order` ADD UNIQUE KEY `uk_erp_so_tenant_no` (`tenant_id`, `order_no`, (CASE WHEN `deleted` = 0 THEN 0 ELSE NULL END))'
));
PREPARE stmt FROM @v77_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `erp_inventory_record` r
INNER JOIN (
    SELECT `id` FROM (
        SELECT `id`, ROW_NUMBER() OVER (PARTITION BY `tenant_id`, `record_no` ORDER BY `id` ASC) AS rn
        FROM `erp_inventory_record`
    ) ranked
    WHERE ranked.rn > 1
) dup ON dup.`id` = r.`id`
SET r.`record_no` = CONCAT(LEFT(r.`record_no`, 48), '-D', r.`id`);

SET @v77_sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'erp_inventory_record' AND index_name = 'uk_erp_inv_rec_tenant_no'),
    'SELECT 1',
    'ALTER TABLE `erp_inventory_record` ADD UNIQUE KEY `uk_erp_inv_rec_tenant_no` (`tenant_id`, `record_no`)'
));
PREPARE stmt FROM @v77_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `gen_dynamic_field` f
INNER JOIN (
    SELECT `id` FROM (
        SELECT `id`, ROW_NUMBER() OVER (PARTITION BY `tenant_id`, `model_id`, `field_code` ORDER BY `id` ASC) AS rn
        FROM `gen_dynamic_field`
        WHERE `deleted` = 0
    ) ranked
    WHERE ranked.rn > 1
) dup ON dup.`id` = f.`id`
SET f.`field_code` = CONCAT(LEFT(f.`field_code`, 48), '_d', f.`id`);

SET @v77_sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'gen_dynamic_field' AND index_name = 'uk_gen_dyn_field_tenant_code'),
    'SELECT 1',
    'ALTER TABLE `gen_dynamic_field` ADD UNIQUE KEY `uk_gen_dyn_field_tenant_code` (`tenant_id`, `model_id`, `field_code`, (CASE WHEN `deleted` = 0 THEN 0 ELSE NULL END))'
));
PREPARE stmt FROM @v77_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `plat_tenant_bill` b
INNER JOIN (
    SELECT `id` FROM (
        SELECT `id`, ROW_NUMBER() OVER (PARTITION BY `tenant_id`, `bill_no` ORDER BY `id` ASC) AS rn
        FROM `plat_tenant_bill`
        WHERE `deleted` = 0
    ) ranked
    WHERE ranked.rn > 1
) dup ON dup.`id` = b.`id`
SET b.`bill_no` = CONCAT(LEFT(b.`bill_no`, 48), '-D', b.`id`);

SET @v77_sql = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'plat_tenant_bill' AND index_name = 'uk_plat_bill_tenant_no'),
    'SELECT 1',
    'ALTER TABLE `plat_tenant_bill` ADD UNIQUE KEY `uk_plat_bill_tenant_no` (`tenant_id`, `bill_no`, (CASE WHEN `deleted` = 0 THEN 0 ELSE NULL END))'
));
PREPARE stmt FROM @v77_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
