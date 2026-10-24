-- V52__tenant_app_entitlements.sql
-- Enterprise SaaS Two-Tier Authorization: Tenant App Entitlements & Subscription Engine

-- 1. Extend iam_menu with app_code column and index
ALTER TABLE `iam_menu`
    ADD COLUMN `app_code` VARCHAR(64) DEFAULT NULL COMMENT '所属应用模块编码' AFTER `menu_code`,
    ADD INDEX `idx_menu_app_code` (`app_code`);

-- 2. Extend plat_tenant with dynamic extra/disabled feature overrides
ALTER TABLE `plat_tenant`
    ADD COLUMN `extra_features_json` TEXT DEFAULT NULL COMMENT '租户独立增购特性包JSON' AFTER `package_id`,
    ADD COLUMN `disabled_features_json` TEXT DEFAULT NULL COMMENT '租户专属禁用特性包JSON' AFTER `extra_features_json`;

-- 3. Populate app_code for existing root-level domains
UPDATE `iam_menu` SET `app_code` = 'dashboard' WHERE `menu_code` = 'dashboard';
UPDATE `iam_menu` SET `app_code` = 'tenant' WHERE `menu_code` = 'tenant';
UPDATE `iam_menu` SET `app_code` = 'iam' WHERE `menu_code` = 'iam';
UPDATE `iam_menu` SET `app_code` = 'workflow' WHERE `menu_code` = 'workflow';
UPDATE `iam_menu` SET `app_code` = 'system' WHERE `menu_code` = 'system';
UPDATE `iam_menu` SET `app_code` = 'integration' WHERE `menu_code` = 'integration';
UPDATE `iam_menu` SET `app_code` = 'file' WHERE `menu_code` = 'file';
UPDATE `iam_menu` SET `app_code` = 'scheduler' WHERE `menu_code` = 'scheduler';
UPDATE `iam_menu` SET `app_code` = 'codegen' WHERE `menu_code` = 'codegen';
UPDATE `iam_menu` SET `app_code` = 'audit' WHERE `menu_code` = 'audit';
UPDATE `iam_menu` SET `app_code` = 'erp' WHERE `menu_code` = 'erp';
UPDATE `iam_menu` SET `app_code` = 'crm' WHERE `menu_code` = 'crm';

-- 4. Cascade app_code from parent menus down to sub-menus and buttons (3 levels)
UPDATE `iam_menu` child
JOIN `iam_menu` parent ON child.`parent_id` = parent.`id`
SET child.`app_code` = parent.`app_code`
WHERE child.`app_code` IS NULL AND parent.`app_code` IS NOT NULL;

UPDATE `iam_menu` child
JOIN `iam_menu` parent ON child.`parent_id` = parent.`id`
SET child.`app_code` = parent.`app_code`
WHERE child.`app_code` IS NULL AND parent.`app_code` IS NOT NULL;

UPDATE `iam_menu` child
JOIN `iam_menu` parent ON child.`parent_id` = parent.`id`
SET child.`app_code` = parent.`app_code`
WHERE child.`app_code` IS NULL AND parent.`app_code` IS NOT NULL;

-- 5. Fallback heuristics for any remaining menus
UPDATE `iam_menu` SET `app_code` = 'crm' WHERE `app_code` IS NULL AND `menu_code` LIKE 'crm%';
UPDATE `iam_menu` SET `app_code` = 'erp' WHERE `app_code` IS NULL AND `menu_code` LIKE 'erp%';
UPDATE `iam_menu` SET `app_code` = 'file' WHERE `app_code` IS NULL AND `menu_code` LIKE 'file%';
UPDATE `iam_menu` SET `app_code` = 'scheduler' WHERE `app_code` IS NULL AND (`menu_code` LIKE 'sched%' OR `menu_code` LIKE 'scheduler%');
UPDATE `iam_menu` SET `app_code` = 'workflow' WHERE `app_code` IS NULL AND (`menu_code` LIKE 'wf%' OR `menu_code` LIKE 'workflow%');
UPDATE `iam_menu` SET `app_code` = 'tenant' WHERE `app_code` IS NULL AND `menu_code` LIKE 'tenant%';
UPDATE `iam_menu` SET `app_code` = 'system' WHERE `app_code` IS NULL AND (`menu_code` LIKE 'sys%' OR `menu_code` LIKE 'system%' OR `menu_code` = 'server_monitor');
UPDATE `iam_menu` SET `app_code` = 'audit' WHERE `app_code` IS NULL AND `menu_code` LIKE 'audit%';
UPDATE `iam_menu` SET `app_code` = 'codegen' WHERE `app_code` IS NULL AND `menu_code` LIKE 'codegen%';
UPDATE `iam_menu` SET `app_code` = 'integration' WHERE `app_code` IS NULL AND `menu_code` LIKE 'integration%';
UPDATE `iam_menu` SET `app_code` = 'iam' WHERE `app_code` IS NULL AND (`menu_code` LIKE 'iam%' OR `menu_code` LIKE 'org%' OR `menu_code` LIKE 'dept%' OR `menu_code` LIKE 'user%' OR `menu_code` LIKE 'role%');
UPDATE `iam_menu` SET `app_code` = 'iam' WHERE `app_code` IS NULL;

-- 6. Synchronize standard multi-tier packages with full app entitlement definitions
UPDATE `plat_tenant_package`
SET `feature_flags_json` = '["saas.dashboard.basic", "saas.iam.basic", "saas.org.basic", "saas.tenant.basic"]'
WHERE `package_code` = 'BASIC';

UPDATE `plat_tenant_package`
SET `feature_flags_json` = '["saas.dashboard.basic", "saas.iam.basic", "saas.org.basic", "saas.tenant.basic", "saas.file.basic", "saas.workflow.basic"]'
WHERE `package_code` = 'PROFESSIONAL';

UPDATE `plat_tenant_package`
SET `feature_flags_json` = '["saas.dashboard.all", "saas.iam.all", "saas.org.all", "saas.tenant.all", "saas.file.all", "saas.workflow.all", "saas.crm.all", "saas.erp.all", "saas.scheduler.all", "saas.audit.all", "saas.system.all"]'
WHERE `package_code` = 'ENTERPRISE';

UPDATE `plat_tenant_package`
SET `feature_flags_json` = '["*"]'
WHERE `package_code` = 'SUPREME';
