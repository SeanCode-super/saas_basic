UPDATE `iam_menu`
SET `component_path` = '@/views/iam/IamFoundationView.vue'
WHERE `id` IN (3, 100);

UPDATE `iam_menu`
SET `component_path` = '@/views/iam/IamOrganizationView.vue'
WHERE `id` = 101;

UPDATE `iam_menu`
SET `component_path` = '@/views/iam/IamAccountsView.vue'
WHERE `id` = 102;

UPDATE `iam_menu`
SET `component_path` = '@/views/iam/IamPermissionsView.vue'
WHERE `id` = 103;

UPDATE `iam_menu`
SET `component_path` = '@/views/iam/IamMenuView.vue'
WHERE `id` = 4;

UPDATE `iam_menu`
SET `component_path` = '@/views/iam/IamDataScopeView.vue'
WHERE `id` = 5;

UPDATE `iam_menu`
SET `component_path` = '@/views/iam/IamLoginPolicyView.vue'
WHERE `id` = 6;

UPDATE `iam_menu`
SET `component_path` = '@/views/iam/IamPasswordPolicyView.vue'
WHERE `id` = 7;

UPDATE `iam_menu`
SET `component_path` = '@/views/iam/IamAssignmentView.vue'
WHERE `id` = 104;
