-- ------------------------------------------------------------------------------
-- V57: Industrial Flowable 7 and OMG BPMN 2.0 Integration Support
-- ------------------------------------------------------------------------------

ALTER TABLE `wf_process_definition`
  ADD COLUMN `bpmn_xml` LONGTEXT DEFAULT NULL COMMENT 'OMG BPMN 2.0 国际规范标准 XML' AFTER `flow_nodes_json`,
  ADD COLUMN `flowable_deployment_id` VARCHAR(64) DEFAULT NULL COMMENT 'Flowable 7 部署主键' AFTER `bpmn_xml`;

ALTER TABLE `wf_process_instance`
  ADD COLUMN `flowable_proc_inst_id` VARCHAR(64) DEFAULT NULL COMMENT 'Flowable 7 运行时流程实例 ID' AFTER `current_node_name`;

ALTER TABLE `wf_task`
  ADD COLUMN `flowable_task_id` VARCHAR(64) DEFAULT NULL COMMENT 'Flowable 7 运行时任务 ID' AFTER `node_type`,
  ADD COLUMN `activity_id` VARCHAR(64) DEFAULT NULL COMMENT 'BPMN 2.0 节点活动 ID' AFTER `flowable_task_id`;
