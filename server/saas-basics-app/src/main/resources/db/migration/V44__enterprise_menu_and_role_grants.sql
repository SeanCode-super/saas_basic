-- V44__enterprise_menu_and_role_grants.sql
-- 企业级菜单体系升级：路由核心元数据扩充与原子化角色-菜单授权模型

-- 1. 扩充 iam_menu 核心路由与组件元数据
ALTER TABLE iam_menu
    ADD COLUMN component_name VARCHAR(128) DEFAULT NULL COMMENT 'Vue组件内部name(供KeepAlive精准缓存)' AFTER component_path,
    ADD COLUMN active_menu VARCHAR(255) DEFAULT NULL COMMENT '高亮激活菜单路径(子页面/详情页高亮父路由)' AFTER route_path,
    ADD COLUMN is_frame TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否外链或内嵌iframe' AFTER keep_alive,
    ADD COLUMN frame_src VARCHAR(512) DEFAULT NULL COMMENT '内嵌网页外链地址' AFTER is_frame,
    ADD COLUMN is_affix TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否固定在标签页导航栏' AFTER frame_src,
    ADD COLUMN redirect VARCHAR(255) DEFAULT NULL COMMENT '目录重定向路径' AFTER active_menu;

-- 2. 创建原子化角色与菜单/按钮权限关联表 iam_role_menu
CREATE TABLE IF NOT EXISTS iam_role_menu (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '租户ID',
    role_id BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
    menu_id BIGINT UNSIGNED NOT NULL COMMENT '菜单或按钮ID(原子资源)',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_role_menu_tenant_role (tenant_id, role_id),
    INDEX idx_role_menu_menu (menu_id),
    UNIQUE KEY uk_role_menu_unique (tenant_id, role_id, menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色与菜单按钮原子权限关联表';

-- 3. 数据平滑迁移：将现有 iam_menu_permission 中的有效页面记录迁移至 iam_role_menu
INSERT IGNORE INTO iam_role_menu (tenant_id, role_id, menu_id)
SELECT tenant_id, CAST(subject_value AS UNSIGNED), menu_id
FROM iam_menu_permission
WHERE subject_type = 'ROLE' AND deleted = 0 AND status = 'ENABLED';

-- 4. 数据平滑迁移：将 button_codes_json 中的按钮原子权限转换为实际 menu_id 存入 iam_role_menu
INSERT IGNORE INTO iam_role_menu (tenant_id, role_id, menu_id)
SELECT mp.tenant_id, CAST(mp.subject_value AS UNSIGNED), m.id
FROM iam_menu_permission mp
JOIN iam_menu m ON m.tenant_id = mp.tenant_id AND m.parent_id = mp.menu_id AND m.menu_type = 'BUTTON' AND m.deleted = 0
WHERE mp.subject_type = 'ROLE' AND mp.deleted = 0 AND mp.status = 'ENABLED'
  AND mp.button_codes_json IS NOT NULL
  AND JSON_SEARCH(mp.button_codes_json, 'one', m.menu_code) IS NOT NULL;

-- 5. 祖先目录自动补全：确保角色关联的父级目录和祖先目录均包含在权限链路上
INSERT IGNORE INTO iam_role_menu (tenant_id, role_id, menu_id)
SELECT rm.tenant_id, rm.role_id, m.parent_id
FROM iam_role_menu rm
JOIN iam_menu m ON m.id = rm.menu_id
WHERE m.parent_id > 0;

INSERT IGNORE INTO iam_role_menu (tenant_id, role_id, menu_id)
SELECT rm.tenant_id, rm.role_id, m.parent_id
FROM iam_role_menu rm
JOIN iam_menu m ON m.id = rm.menu_id
WHERE m.parent_id > 0;

-- 6. 回填核心页面的 component_name 与 is_affix
UPDATE iam_menu SET component_name = 'DashboardView', is_affix = 1 WHERE menu_code = 'dashboard';
UPDATE iam_menu SET component_name = 'TenantView' WHERE menu_code = 'tenant';
UPDATE iam_menu SET component_name = 'TenantOverviewView' WHERE menu_code = 'tenant_overview';
UPDATE iam_menu SET component_name = 'TenantOnboardingView' WHERE menu_code = 'tenant_onboarding';
UPDATE iam_menu SET component_name = 'TenantPackageView' WHERE menu_code = 'tenant_package';
UPDATE iam_menu SET component_name = 'TenantIsolationView' WHERE menu_code = 'tenant_isolation';
UPDATE iam_menu SET component_name = 'TenantLedgerView' WHERE menu_code = 'tenant_ledger';

UPDATE iam_menu SET component_name = 'IamOverviewView' WHERE menu_code = 'iam_overview';
UPDATE iam_menu SET component_name = 'IamDepartmentView' WHERE menu_code = 'iam_department';
UPDATE iam_menu SET component_name = 'IamPositionView' WHERE menu_code = 'iam_position';
UPDATE iam_menu SET component_name = 'IamEmployeeView' WHERE menu_code = 'iam_employee';
UPDATE iam_menu SET component_name = 'IamUserView' WHERE menu_code = 'iam_user';
UPDATE iam_menu SET component_name = 'IamRoleView' WHERE menu_code = 'iam_role';
UPDATE iam_menu SET component_name = 'IamMenuView' WHERE menu_code = 'iam_menu_center';
UPDATE iam_menu SET component_name = 'IamDataScopeView' WHERE menu_code = 'iam_data_scope_center';
UPDATE iam_menu SET component_name = 'IamLoginPolicyView' WHERE menu_code = 'iam_login_policy_center';
UPDATE iam_menu SET component_name = 'IamPasswordPolicyView' WHERE menu_code = 'iam_password_policy_center';

UPDATE iam_menu SET component_name = 'SystemOverviewView' WHERE menu_code = 'system_overview';
UPDATE iam_menu SET component_name = 'SystemSecurityView' WHERE menu_code = 'system_security';
UPDATE iam_menu SET component_name = 'SystemLocaleView' WHERE menu_code = 'system_locale';
UPDATE iam_menu SET component_name = 'SystemSequenceView' WHERE menu_code = 'system_sequence';
UPDATE iam_menu SET component_name = 'SystemRegistryView' WHERE menu_code = 'system_registry';

UPDATE iam_menu SET component_name = 'IntegrationOverviewView' WHERE menu_code = 'integration_overview';
UPDATE iam_menu SET component_name = 'IntegrationConnectionView' WHERE menu_code = 'integration_connection';
UPDATE iam_menu SET component_name = 'IntegrationCallbackView' WHERE menu_code = 'integration_callback';
UPDATE iam_menu SET component_name = 'IntegrationHealthView' WHERE menu_code = 'integration_health';
UPDATE iam_menu SET component_name = 'IntegrationRegistryView' WHERE menu_code = 'integration_registry';

UPDATE iam_menu SET component_name = 'FileOverviewView' WHERE menu_code = 'file_overview';
UPDATE iam_menu SET component_name = 'FileStorageView' WHERE menu_code = 'file_storage';
UPDATE iam_menu SET component_name = 'FileUploadView' WHERE menu_code = 'file_upload';
UPDATE iam_menu SET component_name = 'FileLifecycleView' WHERE menu_code = 'file_lifecycle';
UPDATE iam_menu SET component_name = 'FileAuditView' WHERE menu_code = 'file_audit';

UPDATE iam_menu SET component_name = 'SchedulerOverviewView' WHERE menu_code = 'scheduler_overview';
UPDATE iam_menu SET component_name = 'SchedulerStrategyView' WHERE menu_code = 'scheduler_strategy';
UPDATE iam_menu SET component_name = 'SchedulerPipelineView' WHERE menu_code = 'scheduler_pipeline';
UPDATE iam_menu SET component_name = 'SchedulerAlarmView' WHERE menu_code = 'scheduler_alarm';
UPDATE iam_menu SET component_name = 'SchedulerLogView' WHERE menu_code = 'scheduler_log';

UPDATE iam_menu SET component_name = 'CodegenOverviewView' WHERE menu_code = 'codegen_overview';
UPDATE iam_menu SET component_name = 'CodegenMetadataView' WHERE menu_code = 'codegen_metadata';
UPDATE iam_menu SET component_name = 'CodegenTemplateView' WHERE menu_code = 'codegen_template';
UPDATE iam_menu SET component_name = 'CodegenGateView' WHERE menu_code = 'codegen_gate';
UPDATE iam_menu SET component_name = 'CodegenArtifactView' WHERE menu_code = 'codegen_artifact';

UPDATE iam_menu SET component_name = 'AuditOverviewView' WHERE menu_code = 'audit_overview';
UPDATE iam_menu SET component_name = 'AuditTrailView' WHERE menu_code = 'audit_trail';
UPDATE iam_menu SET component_name = 'AuditRiskView' WHERE menu_code = 'audit_risk';
UPDATE iam_menu SET component_name = 'AuditBlacklistView' WHERE menu_code = 'audit_blacklist';
UPDATE iam_menu SET component_name = 'AuditCatalogView' WHERE menu_code = 'audit_catalog';

UPDATE iam_menu SET component_name = 'WorkflowWorkbenchView' WHERE menu_code = 'workflow_workbench';
UPDATE iam_menu SET component_name = 'WorkflowDefinitionView' WHERE menu_code = 'workflow_definition';
UPDATE iam_menu SET component_name = 'WorkflowInstanceView' WHERE menu_code = 'workflow_instance';
