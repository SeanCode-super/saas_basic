-- =====================================================================
-- V74: Extended Personal Profile & Cloud Preferences,
--      Configurable File Upload Modes, Folder Routing & Source Guard
-- =====================================================================
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- 1. Personal User Profile & Cloud Workspace Preferences on iam_user
ALTER TABLE `iam_user`
  ADD COLUMN `avatar_url` VARCHAR(500) NULL AFTER `avatar_file_id`,
  ADD COLUMN `gender` VARCHAR(16) NULL COMMENT 'UNKNOWN/MALE/FEMALE/OTHER' AFTER `email`,
  ADD COLUMN `bio` VARCHAR(500) NULL AFTER `gender`,
  ADD COLUMN `work_location` VARCHAR(128) NULL AFTER `bio`,
  ADD COLUMN `ext_contact` VARCHAR(128) NULL AFTER `work_location`,
  ADD COLUMN `locale` VARCHAR(32) NULL AFTER `ext_contact`,
  ADD COLUMN `timezone` VARCHAR(64) NULL AFTER `locale`,
  ADD COLUMN `preferences_json` TEXT NULL AFTER `timezone`;

-- 2. Storage Source Upload Mode & Default Folder Policy on file_storage
ALTER TABLE `file_storage`
  ADD COLUMN `is_default` TINYINT(1) NOT NULL DEFAULT 0 AFTER `status`,
  ADD COLUMN `upload_mode` VARCHAR(32) NOT NULL DEFAULT 'DIRECT_SERVER' AFTER `is_default`,
  ADD COLUMN `default_folder` VARCHAR(255) NOT NULL DEFAULT 'uploads' AFTER `base_path`,
  ADD COLUMN `path_pattern` VARCHAR(255) NOT NULL DEFAULT '{folder}/{yyyy}/{MM}/{uuid}_{filename}' AFTER `default_folder`,
  ADD COLUMN `max_file_mb` INT NOT NULL DEFAULT 100 AFTER `path_pattern`,
  ADD COLUMN `allowed_exts` VARCHAR(500) NULL AFTER `max_file_mb`;

-- 3. Track exact storage source, upload mode, and resolved folder on file_object
ALTER TABLE `file_object`
  ADD COLUMN `storage_id` BIGINT UNSIGNED NULL AFTER `bucket_id`,
  ADD COLUMN `upload_mode` VARCHAR(32) NULL AFTER `storage_path`,
  ADD COLUMN `target_dir` VARCHAR(255) NULL AFTER `upload_mode`;

-- 4. Per-Business-Type Upload Mode & Directory Routing Table
CREATE TABLE IF NOT EXISTS `file_upload_route` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 1,
  `storage_id` BIGINT UNSIGNED NULL COMMENT 'Nullable: follow tenant default active storage when null',
  `biz_type` VARCHAR(64) NOT NULL,
  `route_name` VARCHAR(128) NOT NULL,
  `upload_mode` VARCHAR(32) NULL COMMENT 'Optional override: DIRECT_SERVER/PRESIGNED/CHUNKED/LOCAL_DISK',
  `target_dir` VARCHAR(255) NOT NULL COMMENT 'Target folder relative to storage root',
  `path_pattern` VARCHAR(255) NULL COMMENT 'Optional key template overriding storage path_pattern',
  `max_file_mb` INT NULL,
  `allowed_exts` VARCHAR(500) NULL,
  `priority` INT NOT NULL DEFAULT 100,
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
  `remark` VARCHAR(500) NULL,
  `created_by` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_by` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  `version` INT UNSIGNED NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_upload_route_lookup` (`tenant_id`, `biz_type`, `status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件上传方式与业务目录路由配置';

ALTER TABLE `file_upload_route`
  ADD UNIQUE KEY `uk_file_upload_route` (`tenant_id`, `biz_type`, (CASE WHEN deleted = 0 THEN 0 ELSE NULL END));

-- 5. Seed usable local-disk default storage for platform tenant 1 while keeping MinIO as optional standby pool
UPDATE `file_storage`
SET `base_path` = 'enterprise-files',
    `default_folder` = 'uploads',
    `upload_mode` = 'PRESIGNED',
    `status` = 'DISABLED',
    `is_default` = 0,
    `remark` = 'MinIO 私有对象存储池（按需配置凭证并启用）'
WHERE `tenant_id` = 1 AND `storage_code` = 'main_minio' AND `deleted` = 0;

INSERT INTO `file_storage` (
  `tenant_id`, `storage_code`, `storage_name`, `storage_type`, `endpoint`, `bucket_default`,
  `base_path`, `default_folder`, `path_pattern`, `max_file_mb`, `allowed_exts`,
  `public_base_url`, `status`, `is_default`, `upload_mode`, `remark`, `created_by`, `updated_by`, `deleted`
)
SELECT 1, 'local_default', '本地高可用文件存储', 'LOCAL', './data/storage', 'local-disk',
       './data/storage', 'uploads', '{folder}/{yyyy}/{MM}/{uuid}_{filename}', 100, NULL,
       '/api/file/raw-download', 'ENABLED', 1, 'DIRECT_SERVER', '系统默认本地落盘存储源（可修改目录或切换至OSS/MinIO/S3）', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM `file_storage` WHERE `tenant_id` = 1 AND `storage_code` = 'local_default' AND `deleted` = 0);

-- 6. Seed default per-business directory routes for tenant 1
INSERT INTO `file_upload_route` (
  `tenant_id`, `storage_id`, `biz_type`, `route_name`, `upload_mode`, `target_dir`,
  `path_pattern`, `max_file_mb`, `allowed_exts`, `priority`, `status`, `remark`, `created_by`, `updated_by`, `deleted`
)
SELECT 1, NULL, 'USER_AVATAR', '用户头像目录', 'DIRECT_SERVER', 'avatars/users',
       '{folder}/{yyyy}/{MM}/{uuid}_{filename}', 5, 'png,jpg,jpeg,webp,gif', 10, 'ENABLED',
       '个人中心头像上传专用目录', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM `file_upload_route` WHERE `tenant_id` = 1 AND `biz_type` = 'USER_AVATAR' AND `deleted` = 0);

INSERT INTO `file_upload_route` (
  `tenant_id`, `storage_id`, `biz_type`, `route_name`, `upload_mode`, `target_dir`,
  `path_pattern`, `max_file_mb`, `allowed_exts`, `priority`, `status`, `remark`, `created_by`, `updated_by`, `deleted`
)
SELECT 1, NULL, 'AI_KNOWLEDGE', 'AI知识库文档目录', 'DIRECT_SERVER', 'knowledge/docs',
       '{folder}/{yyyy}/{MM}/{uuid}_{filename}', 50, 'pdf,docx,doc,md,txt,csv,json', 20, 'ENABLED',
       '企业知识库原始文档归档目录', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM `file_upload_route` WHERE `tenant_id` = 1 AND `biz_type` = 'AI_KNOWLEDGE' AND `deleted` = 0);

INSERT INTO `file_upload_route` (
  `tenant_id`, `storage_id`, `biz_type`, `route_name`, `upload_mode`, `target_dir`,
  `path_pattern`, `max_file_mb`, `allowed_exts`, `priority`, `status`, `remark`, `created_by`, `updated_by`, `deleted`
)
SELECT 1, NULL, 'PORTAL_ASSET', '门户品牌与素材目录', 'DIRECT_SERVER', 'portal/assets',
       '{folder}/{yyyy}/{MM}/{uuid}_{filename}', 20, 'png,jpg,jpeg,webp,svg', 30, 'ENABLED',
       '租户门户 Logo、海报及公共静态资产目录', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM `file_upload_route` WHERE `tenant_id` = 1 AND `biz_type` = 'PORTAL_ASSET' AND `deleted` = 0);
