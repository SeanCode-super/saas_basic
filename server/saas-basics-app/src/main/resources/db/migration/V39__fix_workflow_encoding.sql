-- ------------------------------------------------------------------------------
-- V39: Fix Chinese encoding/descriptions for workflow definitions and sample data
-- ------------------------------------------------------------------------------

UPDATE `wf_process_definition`
SET `description` = '支持年假、事假、病假及调休申请，3天以内直属主管审批，超过3天自动加签HR总监特批。'
WHERE `process_code` = 'LEAVE_REQUEST';

UPDATE `wf_process_definition`
SET `description` = '企业日常差旅、招待、耗材等费用报销，5000元以上自动加签总经理特批，财务审核打款。'
WHERE `process_code` = 'EXPENSE_REIMBURSE';

UPDATE `wf_process_definition`
SET `description` = '用于企业内部各部门日常事务申报、行政物资申请、跨部门协作呈批。'
WHERE `process_code` = 'GENERAL_APPROVAL';

UPDATE `wf_process_definition`
SET `description` = '员工因跨部门项目协作需临时调阅非本部门数据时发起，经数据合规主管审批后生效。'
WHERE `process_code` = 'DATA_PERMISSION_APPLY';

UPDATE `wf_process_instance`
SET `process_name` = '员工请假与调休申请',
    `initiator_name` = '超级管理员',
    `initiator_dept_name` = '集团总经办',
    `form_data_json` = '{"leaveType":"调休假","days":2,"startDate":"2026-10-18","endDate":"2026-10-19","reason":"因上周周末加班保障系统发布上线，特申请调休2天处理个人事务，工作已交接妥当。"}'
WHERE `id` = 1;

UPDATE `wf_task`
SET `assignee_name` = '超级管理员'
WHERE `id` = 1;

UPDATE `wf_task_log`
SET `operator_name` = '超级管理员',
    `node_name` = '发起申请',
    `comment` = '员工发起了请假申请，进入审批流转。'
WHERE `id` = 1;

UPDATE `iam_menu`
SET `remark` = '企业级可视化工作流与审批中心'
WHERE `menu_code` = 'workflow';
