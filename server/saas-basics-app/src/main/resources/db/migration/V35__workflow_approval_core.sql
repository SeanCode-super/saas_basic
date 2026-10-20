-- ==============================================================================
-- V35__workflow_approval_core.sql
-- 企业级可视化工作流与审批中心核心表结构、权限与开箱即用流程模板
-- ==============================================================================

CREATE TABLE IF NOT EXISTS `wf_process_definition` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '流程定义主键',
  `tenant_id` BIGINT NOT NULL COMMENT '租户ID',
  `process_code` VARCHAR(64) NOT NULL COMMENT '流程编码',
  `process_name` VARCHAR(128) NOT NULL COMMENT '流程名称',
  `category` VARCHAR(64) NOT NULL DEFAULT 'GENERAL' COMMENT '业务分类(HR/FINANCE/GENERAL/IT)',
  `icon` VARCHAR(64) NOT NULL DEFAULT 'Document' COMMENT '流程图标',
  `color` VARCHAR(32) NOT NULL DEFAULT '#2563eb' COMMENT '主题颜色',
  `description` VARCHAR(512) DEFAULT NULL COMMENT '业务说明',
  `form_schema_json` LONGTEXT COMMENT '零代码表单字段配置JSON',
  `flow_nodes_json` LONGTEXT COMMENT '仿飞书可视化节点树配置JSON',
  `version` INT NOT NULL DEFAULT 1 COMMENT '版本号',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED' COMMENT '状态(DRAFT/PUBLISHED/DISABLED)',
  `sort_no` INT NOT NULL DEFAULT 10 COMMENT '排序号',
  `created_by` BIGINT NOT NULL DEFAULT 0,
  `updated_by` BIGINT NOT NULL DEFAULT 0,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wf_def_tenant_code` (`tenant_id`, `process_code`, `deleted`),
  KEY `idx_wf_def_category` (`tenant_id`, `category`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流流程定义表';

CREATE TABLE IF NOT EXISTS `wf_process_instance` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '流程实例主键',
  `tenant_id` BIGINT NOT NULL COMMENT '租户ID',
  `instance_no` VARCHAR(64) NOT NULL COMMENT '单据流水号(如 WF202610170001)',
  `definition_id` BIGINT NOT NULL COMMENT '所属流程定义ID',
  `process_code` VARCHAR(64) NOT NULL COMMENT '流程编码',
  `process_name` VARCHAR(128) NOT NULL COMMENT '流程名称',
  `title` VARCHAR(256) NOT NULL COMMENT '审批单标题',
  `category` VARCHAR(64) NOT NULL DEFAULT 'GENERAL' COMMENT '业务分类',
  `initiator_id` BIGINT NOT NULL COMMENT '发起人用户ID',
  `initiator_name` VARCHAR(128) NOT NULL COMMENT '发起人姓名',
  `initiator_dept_id` BIGINT DEFAULT NULL COMMENT '发起人归属部门ID',
  `initiator_dept_name` VARCHAR(128) DEFAULT NULL COMMENT '发起人归属部门名称',
  `form_data_json` LONGTEXT COMMENT '用户填写的表单业务数据JSON',
  `current_node_id` VARCHAR(64) DEFAULT NULL COMMENT '当前处于的节点ID',
  `current_node_name` VARCHAR(128) DEFAULT NULL COMMENT '当前处于的节点名称',
  `status` VARCHAR(32) NOT NULL DEFAULT 'RUNNING' COMMENT '审批状态(RUNNING/APPROVED/REJECTED/CANCELLED)',
  `start_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发起时间',
  `end_time` DATETIME(3) DEFAULT NULL COMMENT '结束时间',
  `created_by` BIGINT NOT NULL DEFAULT 0,
  `updated_by` BIGINT NOT NULL DEFAULT 0,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wf_inst_no` (`tenant_id`, `instance_no`, `deleted`),
  KEY `idx_wf_inst_initiator` (`tenant_id`, `initiator_id`, `status`),
  KEY `idx_wf_inst_status` (`tenant_id`, `status`, `start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流审批实例单据表';

CREATE TABLE IF NOT EXISTS `wf_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务主键',
  `tenant_id` BIGINT NOT NULL COMMENT '租户ID',
  `instance_id` BIGINT NOT NULL COMMENT '所属流程实例ID',
  `node_id` VARCHAR(64) NOT NULL COMMENT '节点ID',
  `node_name` VARCHAR(128) NOT NULL COMMENT '节点名称',
  `node_type` VARCHAR(32) NOT NULL DEFAULT 'APPROVER' COMMENT '节点类型(APPROVER/CC)',
  `assignee_id` BIGINT NOT NULL COMMENT '处理人用户ID',
  `assignee_name` VARCHAR(128) NOT NULL COMMENT '处理人姓名',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '任务状态(PENDING/COMPLETED/REJECTED/TRANSFERRED/CANCELLED)',
  `action` VARCHAR(32) DEFAULT NULL COMMENT '审批动作(AGREE/REJECT/TRANSFER/READ)',
  `comment` VARCHAR(1024) DEFAULT NULL COMMENT '审批处理意见',
  `claim_time` DATETIME(3) DEFAULT NULL COMMENT '签收/初次查看时间',
  `finish_time` DATETIME(3) DEFAULT NULL COMMENT '办结时间',
  `created_by` BIGINT NOT NULL DEFAULT 0,
  `updated_by` BIGINT NOT NULL DEFAULT 0,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_wf_task_assignee` (`tenant_id`, `assignee_id`, `status`),
  KEY `idx_wf_task_instance` (`tenant_id`, `instance_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流任务待办表';

CREATE TABLE IF NOT EXISTS `wf_task_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '流转日志主键',
  `tenant_id` BIGINT NOT NULL COMMENT '租户ID',
  `instance_id` BIGINT NOT NULL COMMENT '所属流程实例ID',
  `task_id` BIGINT DEFAULT NULL COMMENT '关联任务ID',
  `node_name` VARCHAR(128) NOT NULL COMMENT '节点名称',
  `operator_id` BIGINT NOT NULL COMMENT '操作人ID',
  `operator_name` VARCHAR(128) NOT NULL COMMENT '操作人姓名',
  `action` VARCHAR(32) NOT NULL COMMENT '动作(SUBMIT/AGREE/REJECT/TRANSFER/CANCEL/READ)',
  `action_label` VARCHAR(64) NOT NULL COMMENT '动作显示名称(提交申请/同意/驳回/转交/撤回/已阅)',
  `comment` VARCHAR(1024) DEFAULT NULL COMMENT '处理附言或审批理由',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_wf_log_inst` (`tenant_id`, `instance_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流审批流转时间轴日志表';

-- ------------------------------------------------------------------------------
-- 注册工作流接口权限资源
-- ------------------------------------------------------------------------------
INSERT INTO `iam_api_resource` (`tenant_id`, `resource_code`, `resource_name`, `http_method`, `url_pattern`, `status`, `created_by`, `updated_by`)
VALUES
  (1, 'workflow:definition:query', '查询流程定义', 'GET', '/api/workflow/definitions/**', 'ENABLED', 0, 0),
  (1, 'workflow:definition:write', '设计与维护流程定义', 'POST', '/api/workflow/definitions/**', 'ENABLED', 0, 0),
  (1, 'workflow:instance:query', '查询审批单据', 'GET', '/api/workflow/instances/**', 'ENABLED', 0, 0),
  (1, 'workflow:instance:write', '发起与撤回审批单', 'POST', '/api/workflow/instances/**', 'ENABLED', 0, 0),
  (1, 'workflow:task:query', '查询审批待办与经办', 'GET', '/api/workflow/tasks/**', 'ENABLED', 0, 0),
  (1, 'workflow:task:write', '办理审批任务(同意/驳回)', 'POST', '/api/workflow/tasks/**', 'ENABLED', 0, 0)
ON DUPLICATE KEY UPDATE `resource_name` = VALUES(`resource_name`), `url_pattern` = VALUES(`url_pattern`);

-- ------------------------------------------------------------------------------
-- 注册工作流中心菜单导航 (iam_menu)
-- ------------------------------------------------------------------------------
INSERT INTO `iam_menu` (
  `tenant_id`, `parent_id`, `menu_type`, `menu_code`, `menu_name`,
  `route_path`, `component_path`, `permission_code`, `icon`, `visible`,
  `keep_alive`, `sort_no`, `status`, `meta_json`, `remark`, `created_by`, `updated_by`
)
VALUES
  (1, 0, 'MENU', 'workflow', '工作流中心', '/workflow', '@/views/workflow/WorkflowView.vue', 'workflow:definition:query', 'Share', 1, 1, 35, 'ENABLED', JSON_OBJECT('domain', 'workflow'), '企业级可视化工作流与审批中心', 0, 0)
ON DUPLICATE KEY UPDATE
  `menu_name` = VALUES(`menu_name`),
  `route_path` = VALUES(`route_path`),
  `component_path` = VALUES(`component_path`),
  `icon` = VALUES(`icon`),
  `sort_no` = VALUES(`sort_no`),
  `status` = 'ENABLED';

-- ------------------------------------------------------------------------------
-- 预设企业开箱即用 4 套标准审批流定义
-- ------------------------------------------------------------------------------

-- 1. 假勤管理：员工请假与调休申请
INSERT INTO `wf_process_definition` (
  `id`, `tenant_id`, `process_code`, `process_name`, `category`, `icon`, `color`,
  `description`, `form_schema_json`, `flow_nodes_json`, `version`, `status`, `sort_no`
) VALUES (
  1, 1, 'LEAVE_REQUEST', '员工请假与调休申请', 'HR', 'Calendar', '#10b981',
  '支持年假、事假、病假及调休申请，3天以内直属主管审批，超过3天自动加签HR总监特批。',
  '[{"fieldCode":"leaveType","fieldName":"请假类型","fieldType":"SELECT","required":true,"options":["年假","事假","病假","调休假","婚假/产假"]},{"fieldCode":"days","fieldName":"请假天数","fieldType":"NUMBER","required":true,"placeholder":"请输入天数(如 1 或 3.5)"},{"fieldCode":"startDate","fieldName":"开始日期","fieldType":"DATE","required":true},{"fieldCode":"endDate","fieldName":"结束日期","fieldType":"DATE","required":true},{"fieldCode":"reason","fieldName":"请假事由","fieldType":"TEXTAREA","required":true,"placeholder":"请详细说明请假原因及工作交接情况"}]',
  '{"id":"root","type":"START","name":"发起人(在岗成员)","next":{"id":"node_1","type":"APPROVER","name":"直属部门负责人审批","assigneeType":"DEPT_LEADER","assigneeTitle":"申请人直属部门主管","next":{"id":"branch_1","type":"CONDITION_BRANCH","name":"请假天数条件分支","branches":[{"name":"天数 <= 3天 (常规流转)","condition":{"field":"days","operator":"<=","value":3},"next":{"id":"cc_1","type":"CC","name":"抄送考勤人事","assigneeType":"ROLE","assigneeValue":"3","assigneeTitle":"人事行政专员","next":{"id":"end_1","type":"END","name":"流程办结"}}},{"name":"天数 > 3天 (需加签)","condition":{"field":"days","operator":">","value":3},"next":{"id":"node_2","type":"APPROVER","name":"HR人事总监加签特批","assigneeType":"ROLE","assigneeValue":"2","assigneeTitle":"企业人事总监","next":{"id":"cc_2","type":"CC","name":"抄送考勤人事","assigneeType":"ROLE","assigneeValue":"3","assigneeTitle":"人事行政专员","next":{"id":"end_2","type":"END","name":"流程办结"}}}}]}}}',
  1, 'PUBLISHED', 10
) ON DUPLICATE KEY UPDATE `process_name` = VALUES(`process_name`), `form_schema_json` = VALUES(`form_schema_json`), `flow_nodes_json` = VALUES(`flow_nodes_json`);

-- 2. 财务管理：费用报销与付款申请
INSERT INTO `wf_process_definition` (
  `id`, `tenant_id`, `process_code`, `process_name`, `category`, `icon`, `color`,
  `description`, `form_schema_json`, `flow_nodes_json`, `version`, `status`, `sort_no`
) VALUES (
  2, 1, 'EXPENSE_REIMBURSE', '费用报销与付款申请', 'FINANCE', 'Money', '#f59e0b',
  '企业日常差旅、招待、耗材等费用报销，5000元以上自动加签总经理特批，财务审核打款。',
  '[{"fieldCode":"expenseType","fieldName":"报销类型","fieldType":"SELECT","required":true,"options":["差旅交通费","业务招待费","日常办公耗材","软硬件采购","培训团建费"]},{"fieldCode":"amount","fieldName":"报销总金额(元)","fieldType":"NUMBER","required":true,"placeholder":"请输入报销总金额"},{"fieldCode":"occurDate","fieldName":"发生日期","fieldType":"DATE","required":true},{"fieldCode":"invoiceCount","fieldName":"附发票张数","fieldType":"NUMBER","required":true,"placeholder":"附带纸质或电子发票总数"},{"fieldCode":"remark","fieldName":"费用明细说明","fieldType":"TEXTAREA","required":true,"placeholder":"详细列明各笔支出的发生背景、参与人员及开销明细"}]',
  '{"id":"root","type":"START","name":"发起人(费用经办人)","next":{"id":"node_1","type":"APPROVER","name":"部门负责人初审","assigneeType":"DEPT_LEADER","assigneeTitle":"经办人所属部门主管","next":{"id":"branch_1","type":"CONDITION_BRANCH","name":"金额分级审批分支","branches":[{"name":"金额 < 5000元","condition":{"field":"amount","operator":"<","value":5000},"next":{"id":"node_fin_1","type":"APPROVER","name":"财务主管审核","assigneeType":"ROLE","assigneeValue":"2","assigneeTitle":"财务审计主管","next":{"id":"end_fin_1","type":"END","name":"打款办结"}}},{"name":"金额 >= 5000元 (重大开支)","condition":{"field":"amount","operator":">=","value":5000},"next":{"id":"node_ceo","type":"APPROVER","name":"总经理特批审核","assigneeType":"ROLE","assigneeValue":"1","assigneeTitle":"企业总经理/高管","next":{"id":"node_fin_2","type":"APPROVER","name":"财务出纳复核打款","assigneeType":"ROLE","assigneeValue":"2","assigneeTitle":"财务出纳","next":{"id":"end_fin_2","type":"END","name":"打款办结"}}}}]}}}',
  1, 'PUBLISHED', 20
) ON DUPLICATE KEY UPDATE `process_name` = VALUES(`process_name`), `form_schema_json` = VALUES(`form_schema_json`), `flow_nodes_json` = VALUES(`flow_nodes_json`);

-- 3. 行政办公：通用事项申请与呈批
INSERT INTO `wf_process_definition` (
  `id`, `tenant_id`, `process_code`, `process_name`, `category`, `icon`, `color`,
  `description`, `form_schema_json`, `flow_nodes_json`, `version`, `status`, `sort_no`
) VALUES (
  3, 1, 'GENERAL_APPROVAL', '通用业务事项呈批单', 'GENERAL', 'Promotion', '#3b82f6',
  '用于企业内部各部门日常事务申报、行政物资申请、跨部门协作呈批。',
  '[{"fieldCode":"subject","fieldName":"事项呈批主题","fieldType":"TEXT","required":true,"placeholder":"简要概括事项(如：关于市场部申请新增活动物料的请示)"},{"fieldCode":"urgency","fieldName":"紧急程度","fieldType":"SELECT","required":true,"options":["普通","紧急","特急"]},{"fieldCode":"expectedDate","fieldName":"期望生效/完成日期","fieldType":"DATE","required":true},{"fieldCode":"content","fieldName":"事项详情与理由","fieldType":"TEXTAREA","required":true,"placeholder":"详细陈述事项申报理由、执行计划与预期成果"}]',
  '{"id":"root","type":"START","name":"发起人","next":{"id":"node_1","type":"APPROVER","name":"部门负责人审批","assigneeType":"DEPT_LEADER","assigneeTitle":"所在部门负责人","next":{"id":"cc_admin","type":"CC","name":"抄送行政综合部","assigneeType":"ROLE","assigneeValue":"2","assigneeTitle":"行政综合办","next":{"id":"end_1","type":"END","name":"办结存档"}}}}',
  1, 'PUBLISHED', 30
) ON DUPLICATE KEY UPDATE `process_name` = VALUES(`process_name`), `form_schema_json` = VALUES(`form_schema_json`), `flow_nodes_json` = VALUES(`flow_nodes_json`);

-- 4. IT权限：跨部门数据访问特批
INSERT INTO `wf_process_definition` (
  `id`, `tenant_id`, `process_code`, `process_name`, `category`, `icon`, `color`,
  `description`, `form_schema_json`, `flow_nodes_json`, `version`, `status`, `sort_no`
) VALUES (
  4, 1, 'DATA_PERMISSION_APPLY', '业务数据权限例外特批', 'IT', 'Lock', '#8b5cf6',
  '员工因跨部门项目协作需临时调阅非本部门数据时发起，经数据合规主管审批后生效。',
  '[{"fieldCode":"targetResource","fieldName":"申请数据范围","fieldType":"SELECT","required":true,"options":["全部门客户台账","全企业财务报销汇总","跨部门研发工单明细","敏感操作审计日志"]},{"fieldCode":"duration","fieldName":"申请访问时效","fieldType":"SELECT","required":true,"options":["7天临时授权","30天项目周期","季度授权"]},{"fieldCode":"justification","fieldName":"特批业务理由与背景","fieldType":"TEXTAREA","required":true,"placeholder":"请详细说明跨部门调阅数据的具体项目背景和合法用途"}]',
  '{"id":"root","type":"START","name":"申请人","next":{"id":"node_1","type":"APPROVER","name":"业务主管审核","assigneeType":"DEPT_LEADER","assigneeTitle":"申请人部门主管","next":{"id":"node_sec","type":"APPROVER","name":"安全与数据合规官特批","assigneeType":"ROLE","assigneeValue":"1","assigneeTitle":"数据安全管理员","next":{"id":"end_1","type":"END","name":"特批生效"}}}}',
  1, 'PUBLISHED', 40
) ON DUPLICATE KEY UPDATE `process_name` = VALUES(`process_name`), `form_schema_json` = VALUES(`form_schema_json`), `flow_nodes_json` = VALUES(`flow_nodes_json`);

-- ------------------------------------------------------------------------------
-- 预置一条已发起的真实审批示例单据，供工作台立即可见、直接办理
-- ------------------------------------------------------------------------------
INSERT INTO `wf_process_instance` (
  `id`, `tenant_id`, `instance_no`, `definition_id`, `process_code`, `process_name`,
  `title`, `category`, `initiator_id`, `initiator_name`, `initiator_dept_id`,
  `initiator_dept_name`, `form_data_json`, `current_node_id`, `current_node_name`,
  `status`, `start_time`
) VALUES (
  1, 1, 'WF202610170001', 1, 'LEAVE_REQUEST', '员工请假与调休申请',
  '张伟提交的请假调休申请 (共 2 天)', 'HR', 1, '超级管理员', 1,
  '集团总经办',
  '{"leaveType":"调休假","days":2,"startDate":"2026-10-18","endDate":"2026-10-19","reason":"因上周周末加班保障系统发布上线，特申请调休2天处理个人事务，工作已交接妥当。"}',
  'node_1', '直属部门负责人审批', 'RUNNING', CURRENT_TIMESTAMP(3)
) ON DUPLICATE KEY UPDATE `title` = VALUES(`title`);

INSERT INTO `wf_task` (
  `id`, `tenant_id`, `instance_id`, `node_id`, `node_name`, `node_type`,
  `assignee_id`, `assignee_name`, `status`, `created_at`
) VALUES (
  1, 1, 1, 'node_1', '直属部门负责人审批', 'APPROVER', 1, '超级管理员', 'PENDING', CURRENT_TIMESTAMP(3)
) ON DUPLICATE KEY UPDATE `node_name` = VALUES(`node_name`);

INSERT INTO `wf_task_log` (
  `id`, `tenant_id`, `instance_id`, `task_id`, `node_name`, `operator_id`,
  `operator_name`, `action`, `action_label`, `comment`, `created_at`
) VALUES (
  1, 1, 1, NULL, '发起申请', 1, '超级管理员', 'SUBMIT', '提交单据', '员工发起了请假申请，进入审批流转。', CURRENT_TIMESTAMP(3)
) ON DUPLICATE KEY UPDATE `action_label` = VALUES(`action_label`);
