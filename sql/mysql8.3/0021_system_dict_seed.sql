INSERT INTO `sys_dict_type` (
  `tenant_id`, `dict_code`, `dict_name`, `dict_scope`, `status`, `cacheable`, `ext_json`, `remark`
)
SELECT 1, 'tenant_status', '租户状态', 'TENANT', 'ENABLED', 1, JSON_OBJECT('category', 'platform'), '平台租户状态字典'
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_dict_type` WHERE `tenant_id` = 1 AND `dict_code` = 'tenant_status' AND `deleted` = 0
);

INSERT INTO `sys_dict_type` (
  `tenant_id`, `dict_code`, `dict_name`, `dict_scope`, `status`, `cacheable`, `ext_json`, `remark`
)
SELECT 1, 'job_status', '任务状态', 'TENANT', 'ENABLED', 1, JSON_OBJECT('category', 'scheduler'), '调度状态字典'
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_dict_type` WHERE `tenant_id` = 1 AND `dict_code` = 'job_status' AND `deleted` = 0
);

INSERT INTO `sys_dict_type` (
  `tenant_id`, `dict_code`, `dict_name`, `dict_scope`, `status`, `cacheable`, `ext_json`, `remark`
)
SELECT 1, 'datasource_type', '数据源类型', 'TENANT', 'ENABLED', 1, JSON_OBJECT('category', 'integration'), '数据源类型字典'
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_dict_type` WHERE `tenant_id` = 1 AND `dict_code` = 'datasource_type' AND `deleted` = 0
);

INSERT INTO `sys_dict_type` (
  `tenant_id`, `dict_code`, `dict_name`, `dict_scope`, `status`, `cacheable`, `ext_json`, `remark`
)
SELECT 1, 'common_yes_no', '是否', 'TENANT', 'ENABLED', 1, JSON_OBJECT('category', 'common'), '通用是否字典'
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_dict_type` WHERE `tenant_id` = 1 AND `dict_code` = 'common_yes_no' AND `deleted` = 0
);

INSERT INTO `sys_dict_item` (
  `tenant_id`, `dict_type_id`, `item_value`, `item_label`, `item_color`, `item_tag`, `parent_id`,
  `sort_no`, `status`, `is_default`, `ext_json`, `remark`
)
SELECT 1, t.id, 'TRIAL', '试用', 'warning', 'WARNING', 0, 10, 'ENABLED', 0, JSON_OBJECT('scope', 'tenant'), '租户试用状态'
FROM `sys_dict_type` t
WHERE t.`tenant_id` = 1 AND t.`dict_code` = 'tenant_status' AND t.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `sys_dict_item` i
    WHERE i.`tenant_id` = 1 AND i.`dict_type_id` = t.id AND i.`item_value` = 'TRIAL' AND i.`deleted` = 0
  );

INSERT INTO `sys_dict_item` (
  `tenant_id`, `dict_type_id`, `item_value`, `item_label`, `item_color`, `item_tag`, `parent_id`,
  `sort_no`, `status`, `is_default`, `ext_json`, `remark`
)
SELECT 1, t.id, 'ENABLED', '启用', 'success', 'SUCCESS', 0, 20, 'ENABLED', 1, JSON_OBJECT('scope', 'tenant'), '租户启用状态'
FROM `sys_dict_type` t
WHERE t.`tenant_id` = 1 AND t.`dict_code` = 'tenant_status' AND t.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `sys_dict_item` i
    WHERE i.`tenant_id` = 1 AND i.`dict_type_id` = t.id AND i.`item_value` = 'ENABLED' AND i.`deleted` = 0
  );

INSERT INTO `sys_dict_item` (
  `tenant_id`, `dict_type_id`, `item_value`, `item_label`, `item_color`, `item_tag`, `parent_id`,
  `sort_no`, `status`, `is_default`, `ext_json`, `remark`
)
SELECT 1, t.id, 'FROZEN', '冻结', 'danger', 'DANGER', 0, 30, 'ENABLED', 0, JSON_OBJECT('scope', 'tenant'), '租户冻结状态'
FROM `sys_dict_type` t
WHERE t.`tenant_id` = 1 AND t.`dict_code` = 'tenant_status' AND t.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `sys_dict_item` i
    WHERE i.`tenant_id` = 1 AND i.`dict_type_id` = t.id AND i.`item_value` = 'FROZEN' AND i.`deleted` = 0
  );

INSERT INTO `sys_dict_item` (
  `tenant_id`, `dict_type_id`, `item_value`, `item_label`, `item_color`, `item_tag`, `parent_id`,
  `sort_no`, `status`, `is_default`, `ext_json`, `remark`
)
SELECT 1, t.id, 'DISABLED', '停用', 'info', 'INFO', 0, 10, 'ENABLED', 0, JSON_OBJECT('scope', 'scheduler'), '任务停用状态'
FROM `sys_dict_type` t
WHERE t.`tenant_id` = 1 AND t.`dict_code` = 'job_status' AND t.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `sys_dict_item` i
    WHERE i.`tenant_id` = 1 AND i.`dict_type_id` = t.id AND i.`item_value` = 'DISABLED' AND i.`deleted` = 0
  );

INSERT INTO `sys_dict_item` (
  `tenant_id`, `dict_type_id`, `item_value`, `item_label`, `item_color`, `item_tag`, `parent_id`,
  `sort_no`, `status`, `is_default`, `ext_json`, `remark`
)
SELECT 1, t.id, 'ENABLED', '启用', 'success', 'SUCCESS', 0, 20, 'ENABLED', 1, JSON_OBJECT('scope', 'scheduler'), '任务启用状态'
FROM `sys_dict_type` t
WHERE t.`tenant_id` = 1 AND t.`dict_code` = 'job_status' AND t.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `sys_dict_item` i
    WHERE i.`tenant_id` = 1 AND i.`dict_type_id` = t.id AND i.`item_value` = 'ENABLED' AND i.`deleted` = 0
  );

INSERT INTO `sys_dict_item` (
  `tenant_id`, `dict_type_id`, `item_value`, `item_label`, `item_color`, `item_tag`, `parent_id`,
  `sort_no`, `status`, `is_default`, `ext_json`, `remark`
)
SELECT 1, t.id, 'MYSQL', 'MySQL', 'primary', 'DB', 0, 10, 'ENABLED', 1, JSON_OBJECT('driver', 'mysql'), 'MySQL 数据源'
FROM `sys_dict_type` t
WHERE t.`tenant_id` = 1 AND t.`dict_code` = 'datasource_type' AND t.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `sys_dict_item` i
    WHERE i.`tenant_id` = 1 AND i.`dict_type_id` = t.id AND i.`item_value` = 'MYSQL' AND i.`deleted` = 0
  );

INSERT INTO `sys_dict_item` (
  `tenant_id`, `dict_type_id`, `item_value`, `item_label`, `item_color`, `item_tag`, `parent_id`,
  `sort_no`, `status`, `is_default`, `ext_json`, `remark`
)
SELECT 1, t.id, 'POSTGRESQL', 'PostgreSQL', 'success', 'DB', 0, 20, 'ENABLED', 0, JSON_OBJECT('driver', 'postgresql'), 'PostgreSQL 数据源'
FROM `sys_dict_type` t
WHERE t.`tenant_id` = 1 AND t.`dict_code` = 'datasource_type' AND t.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `sys_dict_item` i
    WHERE i.`tenant_id` = 1 AND i.`dict_type_id` = t.id AND i.`item_value` = 'POSTGRESQL' AND i.`deleted` = 0
  );

INSERT INTO `sys_dict_item` (
  `tenant_id`, `dict_type_id`, `item_value`, `item_label`, `item_color`, `item_tag`, `parent_id`,
  `sort_no`, `status`, `is_default`, `ext_json`, `remark`
)
SELECT 1, t.id, 'HTTP', 'HTTP', 'warning', 'API', 0, 30, 'ENABLED', 0, JSON_OBJECT('driver', 'http'), 'HTTP 数据源'
FROM `sys_dict_type` t
WHERE t.`tenant_id` = 1 AND t.`dict_code` = 'datasource_type' AND t.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `sys_dict_item` i
    WHERE i.`tenant_id` = 1 AND i.`dict_type_id` = t.id AND i.`item_value` = 'HTTP' AND i.`deleted` = 0
  );

INSERT INTO `sys_dict_item` (
  `tenant_id`, `dict_type_id`, `item_value`, `item_label`, `item_color`, `item_tag`, `parent_id`,
  `sort_no`, `status`, `is_default`, `ext_json`, `remark`
)
SELECT 1, t.id, 'Y', '是', 'success', 'YES', 0, 10, 'ENABLED', 1, JSON_OBJECT('bool', true), '通用是'
FROM `sys_dict_type` t
WHERE t.`tenant_id` = 1 AND t.`dict_code` = 'common_yes_no' AND t.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `sys_dict_item` i
    WHERE i.`tenant_id` = 1 AND i.`dict_type_id` = t.id AND i.`item_value` = 'Y' AND i.`deleted` = 0
  );

INSERT INTO `sys_dict_item` (
  `tenant_id`, `dict_type_id`, `item_value`, `item_label`, `item_color`, `item_tag`, `parent_id`,
  `sort_no`, `status`, `is_default`, `ext_json`, `remark`
)
SELECT 1, t.id, 'N', '否', 'info', 'NO', 0, 20, 'ENABLED', 0, JSON_OBJECT('bool', false), '通用否'
FROM `sys_dict_type` t
WHERE t.`tenant_id` = 1 AND t.`dict_code` = 'common_yes_no' AND t.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `sys_dict_item` i
    WHERE i.`tenant_id` = 1 AND i.`dict_type_id` = t.id AND i.`item_value` = 'N' AND i.`deleted` = 0
  );
