-- V58__pay_center_persistence.sql
-- 工业级聚合支付中枢实体持久化表：交易订单与退款流水

CREATE TABLE IF NOT EXISTS `pay_order` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL DEFAULT 1,
  `pay_trade_no` varchar(64) NOT NULL COMMENT '支付中枢统一交易流水号',
  `order_no` varchar(64) NOT NULL COMMENT '业务订单编号',
  `amount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '交易金额',
  `channel` varchar(32) NOT NULL DEFAULT 'WECHAT' COMMENT '支付渠道: WECHAT/ALIPAY/UNIONPAY/MOCK_SANDBOX',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/SUCCESS/FAILED/CLOSED/REFUNDED',
  `subject` varchar(128) DEFAULT NULL COMMENT '交易摘要',
  `pay_url` varchar(512) DEFAULT NULL COMMENT '唤起支付URL',
  `qr_code_url` varchar(512) DEFAULT NULL COMMENT '支付二维码URL',
  `channel_trade_no` varchar(64) DEFAULT NULL COMMENT '外部渠道交易流水号',
  `expire_at` datetime(3) DEFAULT NULL COMMENT '订单超时时间',
  `success_at` datetime(3) DEFAULT NULL COMMENT '支付完成时间',
  `created_by` bigint unsigned NOT NULL DEFAULT 0,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_by` bigint unsigned NOT NULL DEFAULT 0,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  `deleted_at` datetime(3) DEFAULT NULL,
  `version` int unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pay_trade_no` (`tenant_id`, `pay_trade_no`, `deleted`),
  KEY `idx_pay_order_no` (`tenant_id`, `order_no`, `deleted`),
  KEY `idx_pay_status` (`tenant_id`, `status`, `expire_at`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='聚合支付统一交易订单';

CREATE TABLE IF NOT EXISTS `pay_refund` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL DEFAULT 1,
  `refund_no` varchar(64) NOT NULL COMMENT '退款流水号',
  `pay_trade_no` varchar(64) NOT NULL COMMENT '原支付流水号',
  `refund_amount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '退款金额',
  `reason` varchar(255) DEFAULT NULL COMMENT '退款原因',
  `status` varchar(32) NOT NULL DEFAULT 'SUCCESS' COMMENT '退款状态',
  `created_by` bigint unsigned NOT NULL DEFAULT 0,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_by` bigint unsigned NOT NULL DEFAULT 0,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  `deleted_at` datetime(3) DEFAULT NULL,
  `version` int unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pay_refund_no` (`tenant_id`, `refund_no`, `deleted`),
  KEY `idx_refund_pay_trade` (`tenant_id`, `pay_trade_no`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付退款记录表';
