DELETE FROM iam_menu
WHERE id IN (101, 102, 103, 104)
  AND menu_code IN ('iam_organization', 'iam_accounts', 'iam_permissions', 'iam_assignment');
