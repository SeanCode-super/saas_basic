ALTER TABLE `iam_portal_client`
  ADD COLUMN `is_default` tinyint(1) NOT NULL DEFAULT 0 AFTER `slider_reserved`;

UPDATE `iam_portal_client`
SET `is_default` = CASE
  WHEN `id` = (
    SELECT `candidate`.`id`
    FROM (
      SELECT `id`
      FROM `iam_portal_client`
      WHERE `status` = 'ENABLED'
        AND `deleted` = 0
      ORDER BY `id` ASC
      LIMIT 1
    ) AS `candidate`
  ) THEN 1
  ELSE 0
END
WHERE `deleted` = 0;

ALTER TABLE `iam_portal_client`
  ADD COLUMN `active_default_key` tinyint unsigned GENERATED ALWAYS AS (
    CASE
      WHEN `is_default` = 1 AND `status` = 'ENABLED' AND `deleted` = 0 THEN 1
      ELSE NULL
    END
  ) STORED,
  ADD UNIQUE KEY `uk_portal_client_active_default` (`active_default_key`);
