-- V42__seed_system_file_scheduler_buttons.sql
-- Seed granular BUTTON action resources for System, Integration, File, Scheduler, Codegen, and Audit modules

INSERT INTO iam_menu (
  tenant_id, parent_id, menu_type, menu_code, menu_name, route_path, component_path,
  permission_code, icon, visible, keep_alive, sort_no, status, meta_json, remark,
  created_by, updated_by
)
SELECT
  parent_menu.tenant_id,
  parent_menu.id,
  'BUTTON',
  action_catalog.action_code,
  action_catalog.action_name,
  NULL,
  NULL,
  NULL,
  action_catalog.icon,
  0,
  0,
  action_catalog.sort_no,
  'ENABLED',
  JSON_OBJECT('resourceType', 'ACTION'),
  '标准页面动作资源',
  0,
  0
FROM iam_menu parent_menu
INNER JOIN (
  -- 1. System Module Actions
  SELECT 'system_overview' AS parent_code, 'system_overview:refresh' AS action_code, '刷新指标' AS action_name, 'Refresh' AS icon, 10 AS sort_no
  UNION ALL SELECT 'system_overview', 'system_overview:export', '导出报告', 'Download', 20
  UNION ALL SELECT 'system_security', 'system_security:save', '保存安全策略', 'Check', 10
  UNION ALL SELECT 'system_locale', 'system_locale:save', '保存语言策略', 'Check', 10
  UNION ALL SELECT 'system_locale', 'system_locale:dict_type_create', '新增字典类型', 'Plus', 20
  UNION ALL SELECT 'system_locale', 'system_locale:dict_type_edit', '编辑字典类型', 'Edit', 30
  UNION ALL SELECT 'system_locale', 'system_locale:dict_type_toggle', '启停字典类型', 'Switch', 40
  UNION ALL SELECT 'system_locale', 'system_locale:dict_item_create', '新增字典项', 'Plus', 50
  UNION ALL SELECT 'system_locale', 'system_locale:dict_item_edit', '编辑字典项', 'Edit', 60
  UNION ALL SELECT 'system_locale', 'system_locale:dict_item_toggle', '启停字典项', 'Switch', 70
  UNION ALL SELECT 'system_sequence', 'system_sequence:save', '保存序列规则', 'Check', 10
  UNION ALL SELECT 'system_registry', 'system_registry:query', '查询配置', 'Search', 10
  UNION ALL SELECT 'system_registry', 'system_registry:create', '新增配置', 'Plus', 20
  UNION ALL SELECT 'system_registry', 'system_registry:edit', '编辑配置', 'Edit', 30
  UNION ALL SELECT 'system_registry', 'system_registry:toggle', '启停配置', 'Switch', 40
  UNION ALL SELECT 'system_registry', 'system_registry:import', '批量导入', 'Upload', 50
  UNION ALL SELECT 'system_registry', 'system_registry:client_create', '新增Client', 'Plus', 60
  UNION ALL SELECT 'system_registry', 'system_registry:terminal_create', '新增终端', 'Plus', 70

  -- 2. Integration Module Actions
  UNION ALL SELECT 'integration_overview', 'integration_overview:refresh', '刷新总览', 'Refresh', 10
  UNION ALL SELECT 'integration_connection', 'integration_connection:save', '保存连接策略', 'Check', 10
  UNION ALL SELECT 'integration_connection', 'integration_connection:test', '连通测试', 'VideoPlay', 20
  UNION ALL SELECT 'integration_callback', 'integration_callback:save', '保存回调策略', 'Check', 10
  UNION ALL SELECT 'integration_callback', 'integration_callback:retry', '手动重试', 'RefreshRight', 20
  UNION ALL SELECT 'integration_health', 'integration_health:check', '立即检测', 'Check', 10
  UNION ALL SELECT 'integration_health', 'integration_health:report', '生成报告', 'Document', 20
  UNION ALL SELECT 'integration_registry', 'integration_registry:query', '查询数据源', 'Search', 10
  UNION ALL SELECT 'integration_registry', 'integration_registry:create', '新增数据源', 'Plus', 20
  UNION ALL SELECT 'integration_registry', 'integration_registry:edit', '编辑数据源', 'Edit', 30
  UNION ALL SELECT 'integration_registry', 'integration_registry:toggle', '启停数据源', 'Switch', 40
  UNION ALL SELECT 'integration_registry', 'integration_registry:import', '批量导入', 'Upload', 50

  -- 3. File Module Actions
  UNION ALL SELECT 'file_overview', 'file_overview:refresh', '刷新指标', 'Refresh', 10
  UNION ALL SELECT 'file_storage', 'file_storage:save', '保存存储器', 'Check', 10
  UNION ALL SELECT 'file_storage', 'file_storage:edit', '编辑存储器', 'Edit', 20
  UNION ALL SELECT 'file_storage', 'file_storage:toggle', '启停存储器', 'Switch', 30
  UNION ALL SELECT 'file_upload', 'file_upload:create', '创建上传会话', 'Plus', 10
  UNION ALL SELECT 'file_upload', 'file_upload:cancel', '取消上传', 'Close', 20
  UNION ALL SELECT 'file_upload', 'file_upload:download', '下载文件', 'Download', 30
  UNION ALL SELECT 'file_lifecycle', 'file_lifecycle:save', '保存生命周期策略', 'Check', 10
  UNION ALL SELECT 'file_lifecycle', 'file_lifecycle:edit', '编辑策略', 'Edit', 20
  UNION ALL SELECT 'file_lifecycle', 'file_lifecycle:toggle', '启停策略', 'Switch', 30
  UNION ALL SELECT 'file_audit', 'file_audit:create', '登记访问日志', 'Plus', 10
  UNION ALL SELECT 'file_audit', 'file_audit:export', '导出审计日志', 'Download', 20

  -- 4. Scheduler Module Actions
  UNION ALL SELECT 'scheduler_overview', 'scheduler_overview:refresh', '刷新指标', 'Refresh', 10
  UNION ALL SELECT 'scheduler_strategy', 'scheduler_strategy:save', '保存执行策略', 'Check', 10
  UNION ALL SELECT 'scheduler_strategy', 'scheduler_strategy:job_create', '新建调度任务', 'Plus', 20
  UNION ALL SELECT 'scheduler_strategy', 'scheduler_strategy:job_edit', '编辑调度任务', 'Edit', 30
  UNION ALL SELECT 'scheduler_strategy', 'scheduler_strategy:job_trigger', '手动触发', 'VideoPlay', 40
  UNION ALL SELECT 'scheduler_strategy', 'scheduler_strategy:job_toggle', '启停任务', 'Switch', 50
  UNION ALL SELECT 'scheduler_pipeline', 'scheduler_pipeline:save', '保存链路编排', 'Check', 10
  UNION ALL SELECT 'scheduler_pipeline', 'scheduler_pipeline:trigger', '触发流水线', 'VideoPlay', 20
  UNION ALL SELECT 'scheduler_alarm', 'scheduler_alarm:save', '保存告警策略', 'Check', 10
  UNION ALL SELECT 'scheduler_alarm', 'scheduler_alarm:edit', '编辑告警规则', 'Edit', 20
  UNION ALL SELECT 'scheduler_alarm', 'scheduler_alarm:toggle', '启停告警规则', 'Switch', 30
  UNION ALL SELECT 'scheduler_log', 'scheduler_log:save', '保存日志策略', 'Check', 10
  UNION ALL SELECT 'scheduler_log', 'scheduler_log:retry', '重新执行', 'RefreshRight', 20
  UNION ALL SELECT 'scheduler_log', 'scheduler_log:clean', '清理历史日志', 'Delete', 30

  -- 5. Codegen Module Actions
  UNION ALL SELECT 'codegen_overview', 'codegen_overview:refresh', '刷新大盘', 'Refresh', 10
  UNION ALL SELECT 'codegen_metadata', 'codegen_metadata:import', '导入元数据', 'Upload', 10
  UNION ALL SELECT 'codegen_metadata', 'codegen_metadata:sync', '同步数据字典', 'Refresh', 20
  UNION ALL SELECT 'codegen_template', 'codegen_template:save_baseline', '保存模板基线', 'Check', 10
  UNION ALL SELECT 'codegen_template', 'codegen_template:save_style', '保存风格规范', 'Check', 20
  UNION ALL SELECT 'codegen_template', 'codegen_template:edit', '编辑模板', 'Edit', 30
  UNION ALL SELECT 'codegen_gate', 'codegen_gate:save', '保存门禁策略', 'Check', 10
  UNION ALL SELECT 'codegen_gate', 'codegen_gate:verify', '即时验证', 'Check', 20
  UNION ALL SELECT 'codegen_artifact', 'codegen_artifact:generate', '执行生成', 'VideoPlay', 10
  UNION ALL SELECT 'codegen_artifact', 'codegen_artifact:download', '打包下载', 'Download', 20

  -- 6. Audit Module Actions
  UNION ALL SELECT 'audit_overview', 'audit_overview:refresh', '刷新大盘', 'Refresh', 10
  UNION ALL SELECT 'audit_trail', 'audit_trail:save', '保存留痕策略', 'Check', 10
  UNION ALL SELECT 'audit_trail', 'audit_trail:export', '导出全量审计', 'Download', 20
  UNION ALL SELECT 'audit_risk', 'audit_risk:save', '保存风险策略', 'Check', 10
  UNION ALL SELECT 'audit_risk', 'audit_risk:resolve', '处置风险事件', 'Check', 20
  UNION ALL SELECT 'audit_blacklist', 'audit_blacklist:save', '保存黑名单策略', 'Check', 10
  UNION ALL SELECT 'audit_blacklist', 'audit_blacklist:create', '新增黑名单', 'Plus', 20
  UNION ALL SELECT 'audit_blacklist', 'audit_blacklist:remove', '移除黑名单', 'Delete', 30
  UNION ALL SELECT 'audit_catalog', 'audit_catalog:save', '保存高危目录', 'Check', 10
  UNION ALL SELECT 'audit_catalog', 'audit_catalog:scan', '扫描高危目录', 'Search', 20
) action_catalog ON action_catalog.parent_code = parent_menu.menu_code
WHERE parent_menu.deleted = 0
  AND parent_menu.menu_type <> 'BUTTON'
  AND NOT EXISTS (
    SELECT 1
    FROM iam_menu existing_action
    WHERE existing_action.tenant_id = parent_menu.tenant_id
      AND existing_action.menu_code = action_catalog.action_code
      AND existing_action.deleted = 0
  );

-- Update Super Admin (role 1) default grant with all available button actions for each menu
UPDATE iam_menu_permission p
JOIN iam_menu m ON m.id = p.menu_id
SET p.button_codes_json = (
  SELECT JSON_ARRAYAGG(b.menu_code)
  FROM iam_menu b
  WHERE b.parent_id = m.id
    AND b.tenant_id = m.tenant_id
    AND b.menu_type = 'BUTTON'
    AND b.deleted = 0
)
WHERE p.subject_type = 'ROLE'
  AND p.subject_value = '1'
  AND EXISTS (
    SELECT 1
    FROM iam_menu b2
    WHERE b2.parent_id = m.id
      AND b2.tenant_id = m.tenant_id
      AND b2.menu_type = 'BUTTON'
      AND b2.deleted = 0
  );
