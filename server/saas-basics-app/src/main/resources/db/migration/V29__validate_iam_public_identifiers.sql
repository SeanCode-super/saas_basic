ALTER TABLE `iam_user`
  ADD CONSTRAINT `chk_iam_user_public_id_uuidv7` CHECK (
    `public_id` IS NULL OR REGEXP_LIKE(
      `public_id`,
      '^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-7[0-9A-Fa-f]{3}-[89ABab][0-9A-Fa-f]{3}-[0-9A-Fa-f]{12}$',
      'c'
    )
  );

ALTER TABLE `iam_session`
  ADD CONSTRAINT `chk_iam_session_public_id_uuidv7` CHECK (
    `public_id` IS NULL OR REGEXP_LIKE(
      `public_id`,
      '^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-7[0-9A-Fa-f]{3}-[89ABab][0-9A-Fa-f]{3}-[0-9A-Fa-f]{12}$',
      'c'
    )
  );
