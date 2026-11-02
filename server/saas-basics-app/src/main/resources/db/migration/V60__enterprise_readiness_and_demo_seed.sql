-- ==============================================================================
-- V60__enterprise_readiness_and_demo_seed.sql
-- 天枢云基 (Tianshu Cloud) · 企业级生产可用性加固与全业务闭环种子数据集
-- 包含：多租户套餐全开自愈、组织架构、Flowable 在途审批单据、ERP 动态库存、CRM 客户商机
-- ==============================================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- ------------------------------------------------------------------------------
-- 1. 多租户全量权限自愈：升级所有租户至平台旗舰版并开通全量应用
-- ------------------------------------------------------------------------------
UPDATE `plat_tenant` SET `package_id` = 4, `status` = 'ACTIVE' WHERE `deleted` = 0;

-- 批量为租户 1 (平台管理总部) 与租户 5 (测速控制台) 开通全量核心应用
INSERT INTO `plat_tenant_app` (`tenant_id`, `app_code`, `app_name`, `status`, `remark`, `created_by`, `updated_by`)
SELECT t.id, a.app_code, a.app_name, 'ENABLED', 'Enterprise Readiness Auto-Entitlement', 1, 1
FROM `plat_tenant` t
CROSS JOIN (
    SELECT 'console' AS app_code, '控制台与工作台' AS app_name UNION ALL
    SELECT 'iam', '统一身份权限中心' UNION ALL
    SELECT 'tenant', '多租户管理中枢' UNION ALL
    SELECT 'workflow', 'Flowable流程审批中心' UNION ALL
    SELECT 'file', '分布式文件资产中心' UNION ALL
    SELECT 'crm', '客户全生命周期管理' UNION ALL
    SELECT 'erp', '进销存与仓储物料' UNION ALL
    SELECT 'scheduler', '分布式任务调度' UNION ALL
    SELECT 'audit', '全链路行为安全审计' UNION ALL
    SELECT 'integration', '开放平台与消息网关' UNION ALL
    SELECT 'system', '系统底座与监控运维' UNION ALL
    SELECT 'codegen', '低代码代码生成' UNION ALL
    SELECT 'report', '数据大屏与报表中心'
) a
WHERE t.deleted = 0
ON DUPLICATE KEY UPDATE `status` = 'ENABLED', `updated_at` = NOW(3);

-- ------------------------------------------------------------------------------
-- 2. 租户 5 (测速控制台) 流程定义复制与就绪
-- ------------------------------------------------------------------------------
INSERT INTO `wf_process_definition` (
    `tenant_id`, `process_code`, `process_name`, `category`, `icon`, `color`, `description`,
    `form_schema_json`, `flow_nodes_json`, `bpmn_xml`, `flowable_deployment_id`, `version`, `status`, `sort_no`, `created_by`, `updated_by`, `deleted`
)
SELECT
    5, `process_code`, `process_name`, `category`, `icon`, `color`, `description`,
    `form_schema_json`, `flow_nodes_json`, `bpmn_xml`, `flowable_deployment_id`, `version`, `status`, `sort_no`, 1, 1, 0
FROM `wf_process_definition`
WHERE `tenant_id` = 1 AND `deleted` = 0 AND `status` = 'PUBLISHED'
ON DUPLICATE KEY UPDATE `status` = 'PUBLISHED';

-- ------------------------------------------------------------------------------
-- 3. 在途真实 Flowable 审批单据种子数据 (让 PC 与移动端开箱即见真实待办与已办)
-- ------------------------------------------------------------------------------
-- 实例 1 (待我审批): 商务差旅与高铁报销单
INSERT INTO `wf_process_instance` (
    `id`, `tenant_id`, `instance_no`, `definition_id`, `process_code`, `process_name`, `title`, `category`,
    `initiator_id`, `initiator_name`, `initiator_dept_id`, `initiator_dept_name`, `form_data_json`,
    `current_node_id`, `current_node_name`, `status`, `start_time`, `created_by`, `updated_by`
) VALUES (
    101, 1, 'WF20261102001', 2, 'EXPENSE_REIMBURSE', '费用报销与付款申请', '出差拜访华东重点客户及高铁住宿报销', 'FINANCE',
    1, '平台管理员', 1, '华东战略业务部', '{"amount":"2850.00","type":"EXPENSE","priority":"URGENT","reason":"出差拜访华东智能制造战略客户现场PoC支持，报销往返高铁车票及快捷酒店住宿发票。"}',
    'node_1', '直属部门负责人审批', 'RUNNING', NOW(3) - INTERVAL 2 HOUR, 1, 1
) ON DUPLICATE KEY UPDATE `title` = VALUES(`title`), `status` = VALUES(`status`);

-- 关联待办任务 1
INSERT INTO `wf_task` (
    `id`, `tenant_id`, `instance_id`, `node_id`, `node_name`, `node_type`, `assignee_id`, `assignee_name`, `status`, `created_by`, `updated_by`
) VALUES (
    101, 1, 101, 'node_1', '直属部门负责人审批', 'APPROVER', 1, '平台管理员', 'PENDING', 1, 1
) ON DUPLICATE KEY UPDATE `status` = 'PENDING';

-- 实例 2 (待我审批): 研发高并发集群测试服务器与存储扩容申领
INSERT INTO `wf_process_instance` (
    `id`, `tenant_id`, `instance_no`, `definition_id`, `process_code`, `process_name`, `title`, `category`,
    `initiator_id`, `initiator_name`, `initiator_dept_id`, `initiator_dept_name`, `form_data_json`,
    `current_node_id`, `current_node_name`, `status`, `start_time`, `created_by`, `updated_by`
) VALUES (
    102, 1, 'WF20261102002', 3, 'GENERAL_APPROVAL', '通用业务事项呈批单', '研发团队云原生容器集群高可用存储扩容申领', 'GENERAL',
    1, '平台管理员', 1, '智能云计算研发部', '{"amount":"15600.00","type":"PURCHASE","priority":"CRITICAL","reason":"为支撑企业级百万 QPS 压测，申请扩容 2TB 高性能分布式 NVMe 存储资源及边缘节点网关。"}',
    'node_1', '技术总监审批', 'RUNNING', NOW(3) - INTERVAL 1 HOUR, 1, 1
) ON DUPLICATE KEY UPDATE `title` = VALUES(`title`), `status` = VALUES(`status`);

-- 关联待办任务 2
INSERT INTO `wf_task` (
    `id`, `tenant_id`, `instance_id`, `node_id`, `node_name`, `node_type`, `assignee_id`, `assignee_name`, `status`, `created_by`, `updated_by`
) VALUES (
    102, 1, 102, 'node_1', '技术总监审批', 'APPROVER', 1, '平台管理员', 'PENDING', 1, 1
) ON DUPLICATE KEY UPDATE `status` = 'PENDING';

-- 实例 3 (已办结归档): 带薪年假审批单
INSERT INTO `wf_process_instance` (
    `id`, `tenant_id`, `instance_no`, `definition_id`, `process_code`, `process_name`, `title`, `category`,
    `initiator_id`, `initiator_name`, `initiator_dept_id`, `initiator_dept_name`, `form_data_json`,
    `current_node_id`, `current_node_name`, `status`, `start_time`, `end_time`, `created_by`, `updated_by`
) VALUES (
    103, 1, 'WF20261102003', 1, 'LEAVE_REQUEST', '员工请假与调休申请', '2026 年度带薪年假申请 (3天)', 'HR',
    1, '平台管理员', 1, '技术架构中心', '{"days":"3.0","type":"LEAVE","priority":"NORMAL","reason":"按年度带薪假计划休假，工作已交接给值班架构师。"}',
    'end', '流程办结', 'APPROVED', NOW(3) - INTERVAL 1 DAY, NOW(3) - INTERVAL 20 HOUR, 1, 1
) ON DUPLICATE KEY UPDATE `title` = VALUES(`title`), `status` = VALUES(`status`);

-- 关联已办任务 3
INSERT INTO `wf_task` (
    `id`, `tenant_id`, `instance_id`, `node_id`, `node_name`, `node_type`, `assignee_id`, `assignee_name`, `status`, `action`, `comment`, `finish_time`, `created_by`, `updated_by`
) VALUES (
    103, 1, 103, 'node_1', '部门负责人核准', 'APPROVER', 1, '平台管理员', 'COMPLETED', 'AGREE', '工作交接完备，准予带薪休假。', NOW(3) - INTERVAL 20 HOUR, 1, 1
) ON DUPLICATE KEY UPDATE `status` = 'COMPLETED';

-- 同时为租户 5 (测速控制台) 注入相同的开箱体验审批单据
INSERT INTO `wf_process_instance` (
    `id`, `tenant_id`, `instance_no`, `definition_id`, `process_code`, `process_name`, `title`, `category`,
    `initiator_id`, `initiator_name`, `initiator_dept_id`, `initiator_dept_name`, `form_data_json`,
    `current_node_id`, `current_node_name`, `status`, `start_time`, `created_by`, `updated_by`
) VALUES (
    201, 5, 'WF-CE-2026001', 1, 'LEAVE_REQUEST', '员工请假与调休申请', '测速空间技术人员年假调休申请 (1天)', 'HR',
    9, '测速管理员', 1, '测速运维组', '{"days":"1.0","type":"LEAVE","priority":"NORMAL","reason":"因周末机房例行割接调休一天。"}',
    'node_1', '直属主管审批', 'RUNNING', NOW(3) - INTERVAL 30 MINUTE, 9, 9
) ON DUPLICATE KEY UPDATE `title` = VALUES(`title`), `status` = VALUES(`status`);

INSERT INTO `wf_task` (
    `id`, `tenant_id`, `instance_id`, `node_id`, `node_name`, `node_type`, `assignee_id`, `assignee_name`, `status`, `created_by`, `updated_by`
) VALUES (
    201, 5, 201, 'node_1', '直属主管审批', 'APPROVER', 9, '测速管理员', 'PENDING', 9, 9
) ON DUPLICATE KEY UPDATE `status` = 'PENDING';

-- ------------------------------------------------------------------------------
-- 4. ERP 进销存高科技数字化物料与动态库存种子
-- ------------------------------------------------------------------------------
-- 确保仓储总仓存在
INSERT INTO `erp_warehouse` (`id`, `tenant_id`, `warehouse_code`, `warehouse_name`, `address`, `status`, `created_by`, `updated_by`)
VALUES
    (1, 1, 'WH-01', '华东一号中心立体总仓', '江苏省苏州市工业园区生物纳米科技园 A-01 栋', 'ENABLED', 1, 1),
    (2, 1, 'WH-02', '华南保税智造配送仓', '广东省深圳市南山区粤海街道高新南一道 B-03 栋', 'ENABLED', 1, 1),
    (3, 5, 'WH-CE-01', '天枢测速专属实验仓', '上海市浦东新区张江高科技园区 C-08 栋', 'ENABLED', 9, 9)
ON DUPLICATE KEY UPDATE `warehouse_name` = VALUES(`warehouse_name`);

-- 插入 10 款数字化与高科技标准商品物料
INSERT INTO `erp_product` (
    `id`, `tenant_id`, `product_code`, `product_name`, `category`, `specification`, `unit`, `purchase_price`, `sale_price`, `min_stock`, `status`, `remark`, `created_by`, `updated_by`
) VALUES
    (1, 1, 'WL-2026-001', '工业级智能边缘计算网关 Edge-5000', 'HARDWARE', '8核ARM / 16GB内存 / 双千兆网口 / 4G-5G全网通', '台', 1850.00, 2680.00, 10, 'ENABLED', '适用于工厂数据采集与协议转换', 1, 1),
    (2, 1, 'WL-2026-002', '高精度数字激光测距测厚传感器', 'HARDWARE', '量程 0.05-50m / 精度 ±1mm / RS485工业协议', '套', 2199.00, 3180.00, 10, 'ENABLED', '【低库存预警示范物料】', 1, 1),
    (3, 1, 'WL-2026-003', '高性能嵌入式工业级主控核心板', 'COMPONENT', '瑞芯微 RK3588 / 32GB LPDDR4 / 128G eMMC', '块', 1280.00, 1890.00, 15, 'ENABLED', 'AI 视觉边缘分析计算模组', 1, 1),
    (4, 1, 'WL-2026-004', '车规级磷酸铁锂高可靠动力储能电池包', 'COMPONENT', '48V 100Ah / 4.8kWh / 内置智能BMS保护板', '组', 4200.00, 5800.00, 5, 'ENABLED', 'AGV 无人物流车动力电源', 1, 1),
    (5, 1, 'WL-2026-005', '高频工业纯在线式 UPS 不间断电源主机', 'DEVICE', '50kVA / 三进三出 / 纯正弦波 / 智能监控接口', '台', 18900.00, 25800.00, 2, 'ENABLED', 'IDC机房核心配电保障主机', 1, 1),
    (6, 1, 'WL-2026-006', '航空级特种铝合金精密机械结构件', 'MATERIAL', '6061-T6硬质阳极氧化 / CNC精密加工件', '套', 320.00, 480.00, 20, 'ENABLED', '自动化机台外壳与导轨滑槽', 1, 1),
    (7, 1, 'WL-2026-007', '联想 ThinkPad 旗舰商务轻薄笔记本电脑', 'OFFICE', '英特尔酷睿 i7 / 32GB 内存 / 1TB 固态硬盘', '台', 6899.00, 7999.00, 10, 'ENABLED', '技术研发与管理人员标配', 1, 1),
    (8, 1, 'WL-2026-008', '戴尔 27 英寸 4K 办公专业设计高清显示器', 'OFFICE', 'IPS 广色域 / Type-C 90W 反向供电 / 升降底座', '台', 2199.00, 2699.00, 10, 'ENABLED', '设计与架构师专业显示器', 1, 1),
    (9, 1, 'WL-2026-009', '罗技 MX Master 3S 无线静音双模鼠标', 'OFFICE', '8K DPI 激光传感器 / MagSpeed 电磁滚轮', '个', 549.00, 699.00, 15, 'ENABLED', '行政办公高端外设', 1, 1),
    (10, 1, 'WL-2026-010', 'A4 80g 进口纯木浆无尘全木浆复印纸 (整箱)', 'OFFICE', '8包/箱 (共计4000张) / 高白不卡纸', '箱', 168.00, 218.00, 15, 'ENABLED', '行政日常用纸', 1, 1)
ON DUPLICATE KEY UPDATE `product_name` = VALUES(`product_name`), `specification` = VALUES(`specification`), `unit` = VALUES(`unit`), `purchase_price` = VALUES(`purchase_price`), `sale_price` = VALUES(`sale_price`), `remark` = VALUES(`remark`), `min_stock` = VALUES(`min_stock`);

-- 为租户 5 也插入物料档案
INSERT INTO `erp_product` (
    `id`, `tenant_id`, `product_code`, `product_name`, `category`, `specification`, `unit`, `purchase_price`, `sale_price`, `min_stock`, `status`, `remark`, `created_by`, `updated_by`
) VALUES
    (11, 5, 'CE-2026-001', '测速平台高精测试光模块 (100G QSFP28)', 'HARDWARE', '单模 1310nm / 10km传输 / LC接口', '只', 980.00, 1450.00, 5, 'ENABLED', '网络吞吐压测测试耗材', 9, 9),
    (12, 5, 'CE-2026-002', '万兆光纤跳线 (LC-LC 10米 双芯单模)', 'MATERIAL', '低烟无卤 / 陶瓷插芯 / 衰减<0.2dB', '条', 35.00, 60.00, 20, 'ENABLED', '机房布线耗材', 9, 9)
ON DUPLICATE KEY UPDATE `product_name` = VALUES(`product_name`), `specification` = VALUES(`specification`), `unit` = VALUES(`unit`), `remark` = VALUES(`remark`);

-- 注入实时库存数据 (包含充足库存与低库存预警物料)
INSERT INTO `erp_inventory` (`tenant_id`, `product_id`, `warehouse_id`, `stock_quantity`, `locked_quantity`)
VALUES
    (1, 1, 1, 42, 2),    -- 网关: 42台 (正常充足)
    (1, 2, 1, 4, 1),     -- 激光传感器: 4套 (预警缺料！min_stock=10，动态高亮标红！)
    (1, 3, 1, 85, 5),    -- 核心板: 85块 (正常充足)
    (1, 4, 1, 18, 0),    -- 动力电池: 18组 (正常充足)
    (1, 5, 1, 3, 0),     -- UPS主机: 3台 (正常充足)
    (1, 6, 1, 260, 20),  -- 铝合金件: 260套 (正常充足)
    (1, 7, 1, 38, 2),    -- ThinkPad: 38台 (正常充足)
    (1, 8, 1, 5, 2),     -- 戴尔显示器: 5台 (预警缺料！min_stock=10，动态高亮标红！)
    (1, 9, 1, 64, 0),    -- 罗技鼠标: 64个 (正常充足)
    (1, 10, 1, 112, 0),  -- A4复印纸: 112箱 (正常充足)
    (5, 11, 3, 26, 0),   -- 租户5光模块: 26只
    (5, 12, 3, 180, 0)   -- 租户5光纤跳线: 180条
ON DUPLICATE KEY UPDATE `stock_quantity` = VALUES(`stock_quantity`);

-- 注入真实的进出库流水台账
INSERT INTO `erp_inventory_record` (
    `tenant_id`, `record_no`, `record_type`, `product_id`, `product_name`, `warehouse_id`, `warehouse_name`,
    `change_quantity`, `before_quantity`, `after_quantity`, `operator_name`, `remark`, `created_at`
) VALUES
    (1, 'REC20261101001', 'PURCHASE_IN', 1, '工业级智能边缘计算网关 Edge-5000', 1, '华东一号中心立体总仓', 50, 0, 50, '平台管理员', '供应商华为数字能源批量送检入库', NOW(3) - INTERVAL 2 DAY),
    (1, 'REC20261101002', 'MANUAL_OUT', 1, '工业级智能边缘计算网关 Edge-5000', 1, '华东一号中心立体总仓', -8, 50, 42, '平台管理员', '华东智能制造项目现场设备发货领用', NOW(3) - INTERVAL 1 DAY),
    (1, 'REC20261102001', 'PURCHASE_IN', 2, '高精度数字激光测距测厚传感器', 1, '华东一号中心立体总仓', 15, 0, 15, '平台管理员', '第一批次采购验收入库', NOW(3) - INTERVAL 3 DAY),
    (1, 'REC20261102002', 'MANUAL_OUT', 2, '高精度数字激光测距测厚传感器', 1, '华东一号中心立体总仓', -11, 15, 4, '平台管理员', '自动化测试线集成领料出库 (已达预警线)', NOW(3) - INTERVAL 6 HOUR),
    (1, 'REC20261102003', 'STOCK_CHECK', 10, 'A4 80g 进口纯木浆无尘全木浆复印纸 (整箱)', 1, '华东一号中心立体总仓', 12, 100, 112, '平台管理员', '月末仓储库存例行盘点盘盈入库', NOW(3) - INTERVAL 12 HOUR);

-- ------------------------------------------------------------------------------
-- 5. CRM 客户全生命周期标杆数据 (私海客户、公海池客户、商机漏斗与合同)
-- ------------------------------------------------------------------------------
INSERT INTO `crm_customer` (
    `id`, `tenant_id`, `customer_code`, `name`, `industry`, `level`, `source`, `mobile`, `telephone`, `email`,
    `address`, `deal_status`, `is_pool`, `lock_status`, `owner_user_id`, `owner_user_name`, `created_by`, `updated_by`
) VALUES
    (1, 1, 'KH-2026-001', '北京华科盛世软件技术有限公司', 'INTERNET', 'A', 'REFERRAL', '13911223344', '010-88996655', 'contact@huake.com', '北京市海淀区中关村软件园二期 12 号楼', 1, 0, 1, 1, '平台管理员', 1, 1),
    (2, 1, 'KH-2026-002', '上海智联达智能装备制造有限公司', 'MANUFACTURING', 'B', 'ONLINE', '13855667788', '021-66778899', 'biz@zhilianda.cn', '上海市嘉定区安亭国际汽车城核心园区 88 号', 0, 0, 0, 1, '平台管理员', 1, 1),
    (3, 1, 'KH-2026-003', '深圳创越数智供应链管理集团', 'SUPPLY_CHAIN', 'A', 'CONFERENCE', '13799887766', '0755-22334455', 'sales@chuangyue.com', '深圳市南山区深圳湾科技生态园 9 栋 A 座', 0, 0, 1, 1, '平台管理员', 1, 1),
    (4, 1, 'KH-2026-004', '杭州聚数云享网络科技有限公司', 'INTERNET', 'C', 'INBOUND', '13612345678', '0571-88776655', 'hi@jushuyun.com', '浙江省杭州市余杭区阿里巴巴未来科技城 6 幢', 0, 1, 0, 1, '公海池共享', 1, 1),
    (5, 1, 'KH-2026-005', '苏州汇能新能源动力科技有限公司', 'NEW_ENERGY', 'A', 'OUTBOUND', '13566778899', '0512-66889900', 'info@huineng.cn', '江苏省苏州市相城区阳澄湖节能环保产业园 5 号', 0, 0, 0, 1, '平台管理员', 1, 1),
    (6, 5, 'KH-CE-001', '中国联合网络通信集团测速联合创新室', 'TELECOM', 'A', 'OFFICIAL', '18600100100', '010-66299999', 'speed@unicom.cn', '北京市西城区金融大街 21 号', 1, 0, 1, 9, '测速管理员', 9, 9)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `industry` = VALUES(`industry`), `is_pool` = VALUES(`is_pool`);

-- 插入商机漏斗数据 (覆盖不同销售阶段)
INSERT INTO `crm_business` (
    `id`, `tenant_id`, `business_code`, `customer_id`, `customer_name`, `name`, `total_price`, `stage`, `win_rate`, `deal_date`, `owner_user_id`, `owner_user_name`, `created_by`, `updated_by`
) VALUES
    (1, 1, 'BIZ20261102001', 1, '北京华科盛世软件技术有限公司', '天枢 PaaS 企业底座全套私有化部署采购项目', 280000.00, 'WON', 100, CURDATE() + INTERVAL 10 DAY, 1, '平台管理员', 1, 1),
    (2, 1, 'BIZ20261102002', 2, '上海智联达智能装备制造有限公司', '工厂车间物料出入库与 Flowable 流程中台二期', 156000.00, 'PROPOSAL', 60, CURDATE() + INTERVAL 25 DAY, 1, '平台管理员', 1, 1),
    (3, 1, 'BIZ20261102003', 3, '深圳创越数智供应链管理集团', '全渠道数字供应链云仓联动协同平台', 520000.00, 'NEGOTIATION', 80, CURDATE() + INTERVAL 15 DAY, 1, '平台管理员', 1, 1),
    (4, 1, 'BIZ20261102004', 5, '苏州汇能新能源动力科技有限公司', '动力电池数字化产线MES与质量追溯系统', 360000.00, 'QUALIFICATION', 30, CURDATE() + INTERVAL 45 DAY, 1, '平台管理员', 1, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `total_price` = VALUES(`total_price`), `stage` = VALUES(`stage`);

-- 插入签约合同与财务回款记录
INSERT INTO `crm_contract` (
    `id`, `tenant_id`, `contract_no`, `name`, `customer_id`, `customer_name`, `business_id`, `business_name`, `total_price`, `start_time`, `end_time`, `status`, `created_by`, `updated_by`
) VALUES
    (1, 1, 'HT-202611-001', '天枢 PaaS 底座软件许可与三年技术维保合同', 1, '北京华科盛世软件技术有限公司', 1, '天枢 PaaS 企业底座全套私有化部署采购项目', 280000.00, CURDATE() - INTERVAL 10 DAY, CURDATE() + INTERVAL 3 YEAR, 'EXECUTING', 1, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `total_price` = VALUES(`total_price`);

INSERT INTO `crm_receivable` (
    `id`, `tenant_id`, `receivable_no`, `contract_id`, `contract_name`, `customer_id`, `customer_name`, `price`, `return_time`, `pay_type`, `status`, `created_by`, `updated_by`
) VALUES
    (1, 1, 'REC-202611-001', 1, '天枢 PaaS 底座软件许可与三年技术维保合同', 1, '北京华科盛世软件技术有限公司', 140000.00, CURDATE() - INTERVAL 5 DAY, 'BANK_TRANSFER', 'RECEIVED', 1, 1)
ON DUPLICATE KEY UPDATE `price` = VALUES(`price`);
