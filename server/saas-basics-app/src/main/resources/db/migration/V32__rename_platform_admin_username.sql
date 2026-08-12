UPDATE `iam_user`
SET `username` = 'admin',
    `updated_by` = 0
WHERE `tenant_id` = 1
  AND `user_code` = 'PLATFORM_ADMIN'
  AND `deleted` = 0
  AND `username` <> 'admin';
