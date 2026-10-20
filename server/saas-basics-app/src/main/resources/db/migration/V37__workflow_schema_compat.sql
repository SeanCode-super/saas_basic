-- ------------------------------------------------------------------------------
-- V37: Align workflow tables with BaseTenantEntity and BaseEntity model
-- ------------------------------------------------------------------------------

ALTER TABLE `wf_process_definition`
  ADD COLUMN `deleted_at` DATETIME(3) DEFAULT NULL AFTER `deleted`,
  ADD COLUMN `remark` VARCHAR(255) DEFAULT NULL AFTER `deleted_at`;

ALTER TABLE `wf_process_instance`
  ADD COLUMN `version` INT NOT NULL DEFAULT 1 AFTER `deleted`,
  ADD COLUMN `deleted_at` DATETIME(3) DEFAULT NULL AFTER `version`,
  ADD COLUMN `remark` VARCHAR(255) DEFAULT NULL AFTER `deleted_at`;

ALTER TABLE `wf_task`
  ADD COLUMN `version` INT NOT NULL DEFAULT 1 AFTER `deleted`,
  ADD COLUMN `deleted_at` DATETIME(3) DEFAULT NULL AFTER `version`,
  ADD COLUMN `remark` VARCHAR(255) DEFAULT NULL AFTER `deleted_at`;
