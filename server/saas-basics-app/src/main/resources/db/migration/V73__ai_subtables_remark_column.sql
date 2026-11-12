-- =====================================================================
-- V73: Align AI subtables with BaseTenantEntity standard remark column
-- =====================================================================
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

ALTER TABLE `ai_knowledge_doc`
  ADD COLUMN `remark` VARCHAR(500) NULL AFTER `raw_text`;

ALTER TABLE `ai_knowledge_chunk`
  ADD COLUMN `remark` VARCHAR(500) NULL AFTER `keywords`;

ALTER TABLE `ai_agent_session`
  ADD COLUMN `remark` VARCHAR(500) NULL AFTER `last_active_at`;

ALTER TABLE `ai_finetune_sample`
  ADD COLUMN `remark` VARCHAR(500) NULL AFTER `desensitized`;

ALTER TABLE `ai_finetune_job`
  ADD COLUMN `remark` VARCHAR(500) NULL AFTER `metrics_json`;
