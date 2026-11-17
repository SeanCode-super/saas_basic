ALTER TABLE `audit_operation_log`
    ADD COLUMN `before_json` text NULL COMMENT '变更前快照JSON',
    ADD COLUMN `after_json` text NULL COMMENT '变更后快照JSON';
