-- V40: Hide redundant legacy IAM assignment menus (user-role and role-api)
-- These are now fully superseded by the 3-in-1 Role Drawer and Permission Inspector in Role & User management.
UPDATE iam_menu SET visible = 0 WHERE id IN (116, 117);
