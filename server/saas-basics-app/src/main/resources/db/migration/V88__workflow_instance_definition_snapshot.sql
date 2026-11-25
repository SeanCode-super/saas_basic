ALTER TABLE wf_process_instance
    ADD COLUMN flow_nodes_snapshot LONGTEXT,
    ADD COLUMN form_schema_snapshot LONGTEXT,
    ADD COLUMN definition_revision INT;
ALTER TABLE wf_task ADD COLUMN return_source_task_id BIGINT;
UPDATE wf_process_instance i
JOIN wf_process_definition d ON d.id = i.definition_id AND d.tenant_id = i.tenant_id
SET i.flow_nodes_snapshot = d.flow_nodes_json, i.form_schema_snapshot = d.form_schema_json, i.definition_revision = d.version
WHERE d.updated_at <= i.start_time;
UPDATE wf_task s
JOIN wf_task r ON r.tenant_id = s.tenant_id AND r.instance_id = s.instance_id AND r.id < s.id
  AND r.action = 'ROLLBACK_INITIATOR'
LEFT JOIN wf_task later ON later.tenant_id = s.tenant_id AND later.instance_id = s.instance_id
  AND later.action = 'ROLLBACK_INITIATOR' AND later.id > r.id AND later.id < s.id
SET s.return_source_task_id = r.id
WHERE s.node_id = 'rollback_starter' AND later.id IS NULL;

-- The V60 demonstration instance used the platform definition ID after copying
-- that definition into tenant 5. Its start time was deliberately backdated.
UPDATE wf_process_instance i
JOIN wf_process_definition d ON d.tenant_id = i.tenant_id AND d.process_code = i.process_code AND d.deleted = 0
SET i.definition_id = d.id, i.definition_revision = d.version,
    i.flow_nodes_snapshot = d.flow_nodes_json, i.form_schema_snapshot = d.form_schema_json
WHERE i.id = 201 AND i.tenant_id = 5 AND i.instance_no = 'WF-CE-2026001'
  AND i.process_code = 'LEAVE_REQUEST' AND i.definition_id = 1 AND i.definition_revision IS NULL
  AND d.created_at <= i.created_at AND d.updated_at <= i.created_at;
