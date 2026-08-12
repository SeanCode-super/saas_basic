ALTER TABLE `iam_user`
  ADD COLUMN `public_id` char(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL AFTER `tenant_id`,
  ADD UNIQUE KEY `uk_iam_user_public_id` (`tenant_id`, `public_id`, `deleted`);

ALTER TABLE `iam_session`
  ADD COLUMN `public_id` char(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL AFTER `tenant_id`,
  ADD COLUMN `subject_binding_public_id` char(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL AFTER `user_id`,
  ADD COLUMN `selected_assignment_public_id` char(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL AFTER `subject_binding_public_id`,
  ADD UNIQUE KEY `uk_iam_session_public_id` (`tenant_id`, `public_id`, `deleted`),
  ADD KEY `idx_iam_session_subject_binding` (`tenant_id`, `subject_binding_public_id`, `status`, `deleted`);

CREATE TRIGGER `trg_iam_user_public_id_bi`
BEFORE INSERT ON `iam_user`
FOR EACH ROW
SET NEW.`public_id` = COALESCE(
  NEW.`public_id`,
  LOWER(CONCAT(
    LEFT(LPAD(HEX(FLOOR(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000)), 12, '0'), 8),
    '-',
    RIGHT(LPAD(HEX(FLOOR(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000)), 12, '0'), 4),
    '-7',
    RIGHT(HEX(RANDOM_BYTES(2)), 3),
    '-',
    LPAD(HEX(128 + (ORD(RANDOM_BYTES(1)) & 63)), 2, '0'),
    HEX(RANDOM_BYTES(1)),
    '-',
    HEX(RANDOM_BYTES(6))
  ))
);

CREATE TRIGGER `trg_iam_session_public_id_bi`
BEFORE INSERT ON `iam_session`
FOR EACH ROW
SET NEW.`public_id` = COALESCE(
  NEW.`public_id`,
  LOWER(CONCAT(
    LEFT(LPAD(HEX(FLOOR(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000)), 12, '0'), 8),
    '-',
    RIGHT(LPAD(HEX(FLOOR(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000)), 12, '0'), 4),
    '-7',
    RIGHT(HEX(RANDOM_BYTES(2)), 3),
    '-',
    LPAD(HEX(128 + (ORD(RANDOM_BYTES(1)) & 63)), 2, '0'),
    HEX(RANDOM_BYTES(1)),
    '-',
    HEX(RANDOM_BYTES(6))
  ))
);

CREATE TABLE IF NOT EXISTS `iam_user_person_binding` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `public_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `user_id` bigint unsigned NOT NULL,
  `person_public_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT',
  `valid_from` datetime(3) NOT NULL,
  `valid_to` datetime(3) DEFAULT NULL,
  `created_by` bigint unsigned NOT NULL DEFAULT 0,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_by` bigint unsigned NOT NULL DEFAULT 0,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  `deleted_at` datetime(3) DEFAULT NULL,
  `version` int unsigned NOT NULL DEFAULT 0,
  `remark` varchar(500) DEFAULT NULL,
  `active_user_id` bigint unsigned GENERATED ALWAYS AS (
    CASE WHEN `status` = 'ACTIVE' AND `deleted` = 0 THEN `user_id` ELSE NULL END
  ) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_iam_user_person_binding_public_id` (`tenant_id`, `public_id`, `deleted`),
  UNIQUE KEY `uk_iam_user_person_binding_active_user` (`tenant_id`, `active_user_id`),
  KEY `idx_iam_user_person_binding_user` (`tenant_id`, `user_id`, `status`, `valid_from`, `valid_to`, `deleted`),
  KEY `idx_iam_user_person_binding_person` (`tenant_id`, `person_public_id`, `status`, `valid_from`, `valid_to`, `deleted`),
  CONSTRAINT `chk_iam_user_person_binding_status` CHECK (
    `status` IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'ARCHIVED')
  ),
  CONSTRAINT `chk_iam_user_person_binding_period` CHECK (
    `valid_to` IS NULL OR `valid_to` > `valid_from`
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Versioned association between an IAM account and an organization person';
