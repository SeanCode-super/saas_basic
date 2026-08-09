ALTER TABLE `file_upload_session`
  ADD COLUMN `remark` varchar(255) DEFAULT NULL COMMENT '备注' AFTER `version`;
