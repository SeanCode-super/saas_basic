-- V59__pay_order_remark.sql
-- 幂等更新 pay_order 和 pay_refund 的 remark 字段元数据定义，保证 MyBatis-Plus 实体映射严格对齐

ALTER TABLE `pay_order` MODIFY COLUMN `remark` varchar(500) DEFAULT NULL COMMENT '备注说明';
ALTER TABLE `pay_refund` MODIFY COLUMN `remark` varchar(500) DEFAULT NULL COMMENT '备注说明';
