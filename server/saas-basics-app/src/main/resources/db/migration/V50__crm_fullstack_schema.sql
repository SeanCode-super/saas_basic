-- =====================================================================
-- V50__crm_fullstack_schema.sql
-- Antigravity SaaS 客户关系管理系统 (CRM) 全链路数据结构与初始数据
-- =====================================================================

-- 1. 销售线索表
CREATE TABLE IF NOT EXISTS crm_clue (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    contact_name VARCHAR(64) NOT NULL,
    mobile VARCHAR(32),
    telephone VARCHAR(32),
    email VARCHAR(128),
    address VARCHAR(256),
    source VARCHAR(64) DEFAULT 'ONLINE',
    status VARCHAR(32) DEFAULT 'UNFOLLOWED',
    owner_user_id BIGINT DEFAULT 1,
    owner_user_name VARCHAR(64) DEFAULT '超级管理员',
    next_contact_time DATETIME,
    converted_customer_id BIGINT,
    remark VARCHAR(500),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT DEFAULT 1,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 1,
    deleted TINYINT DEFAULT 0,
    INDEX idx_crm_clue_tenant (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. 客户档案表 (包含公海池机制)
CREATE TABLE IF NOT EXISTS crm_customer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    customer_code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    industry VARCHAR(64) DEFAULT 'INTERNET',
    level VARCHAR(32) DEFAULT 'B',
    source VARCHAR(64) DEFAULT 'ONLINE',
    mobile VARCHAR(32),
    telephone VARCHAR(32),
    email VARCHAR(128),
    address VARCHAR(256),
    deal_status TINYINT DEFAULT 0,
    is_pool TINYINT DEFAULT 0,
    lock_status TINYINT DEFAULT 0,
    owner_user_id BIGINT DEFAULT 1,
    owner_user_name VARCHAR(64) DEFAULT '超级管理员',
    last_contact_time DATETIME,
    next_contact_time DATETIME,
    remark VARCHAR(500),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT DEFAULT 1,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 1,
    deleted TINYINT DEFAULT 0,
    INDEX idx_crm_cust_tenant (tenant_id, is_pool)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. 商机档案表 (销售漏斗)
CREATE TABLE IF NOT EXISTS crm_business (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    business_code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    customer_id BIGINT NOT NULL,
    customer_name VARCHAR(128),
    total_price DECIMAL(12,2) DEFAULT 0.00,
    deal_date DATE,
    stage VARCHAR(32) DEFAULT 'INITIAL',
    win_rate INT DEFAULT 20,
    loss_reason VARCHAR(256),
    owner_user_id BIGINT DEFAULT 1,
    owner_user_name VARCHAR(64) DEFAULT '超级管理员',
    remark VARCHAR(500),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT DEFAULT 1,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 1,
    deleted TINYINT DEFAULT 0,
    INDEX idx_crm_biz_tenant (tenant_id, stage)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. 商务合同表
CREATE TABLE IF NOT EXISTS crm_contract (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    contract_no VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    customer_id BIGINT NOT NULL,
    customer_name VARCHAR(128),
    business_id BIGINT,
    business_name VARCHAR(128),
    total_price DECIMAL(12,2) DEFAULT 0.00,
    start_time DATE,
    end_time DATE,
    signer_user_name VARCHAR(64) DEFAULT '超级管理员',
    status VARCHAR(32) DEFAULT 'DRAFT',
    remark VARCHAR(500),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT DEFAULT 1,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 1,
    deleted TINYINT DEFAULT 0,
    INDEX idx_crm_ct_tenant (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. 财务回款单表
CREATE TABLE IF NOT EXISTS crm_receivable (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    receivable_no VARCHAR(64) NOT NULL,
    contract_id BIGINT NOT NULL,
    contract_name VARCHAR(128),
    customer_id BIGINT NOT NULL,
    customer_name VARCHAR(128),
    price DECIMAL(12,2) NOT NULL,
    return_time DATE,
    pay_type VARCHAR(32) DEFAULT 'BANK_TRANSFER',
    status VARCHAR(32) DEFAULT 'PENDING',
    remark VARCHAR(500),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT DEFAULT 1,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 1,
    deleted TINYINT DEFAULT 0,
    INDEX idx_crm_rec_tenant (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. 全链路跟进记录表 (线索/客户/商机/合同 通用)
CREATE TABLE IF NOT EXISTS crm_follow_up_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    biz_type VARCHAR(32) NOT NULL,
    biz_id BIGINT NOT NULL,
    follow_type VARCHAR(32) DEFAULT 'CALL',
    content VARCHAR(1000) NOT NULL,
    next_contact_time DATETIME,
    contact_user_name VARCHAR(64) DEFAULT '超级管理员',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT DEFAULT 1,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 1,
    deleted TINYINT DEFAULT 0,
    INDEX idx_crm_fur_biz (tenant_id, biz_type, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. 初始种子数据预置
INSERT INTO crm_clue (id, tenant_id, name, contact_name, mobile, telephone, email, address, source, status, owner_user_id, owner_user_name, next_contact_time, remark)
VALUES 
(1, 1, '智能微电网储能数字化中台', '李工', '13811223344', '010-88881234', 'ligong@grid-tech.com', '北京市海淀区中关村南大街1号', 'ONLINE', 'FOLLOWING', 1, '超级管理员', '2026-10-25 14:00:00', '对微电网多租户监控和大屏有强需求，已完成初次电话沟通'),
(2, 1, '连锁新零售全渠道订单与履约系统', '张总', '13922334455', '021-66778899', 'zhang@retailchain.com', '上海市静安区南京西路1266号', 'REFERRAL', 'UNFOLLOWED', 1, '超级管理员', '2026-10-22 10:30:00', '合作伙伴转介绍，需安排线下拜访与方案演示')
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO crm_customer (id, tenant_id, customer_code, name, industry, level, source, mobile, telephone, email, address, deal_status, is_pool, lock_status, owner_user_id, owner_user_name, last_contact_time, next_contact_time, remark)
VALUES
(1, 1, 'CUST-2026-001', '华润数字科技有限公司', '央国企数字化', 'A', 'OFFLINE', '13700137001', '0755-88880001', 'procure@cr-digital.com', '广东省深圳市南山区华润大厦 36 层', 1, 0, 1, 1, '超级管理员', '2026-10-18 16:30:00', '2026-10-28 09:30:00', 'A级标杆核心客户，已签约一期合同并完成首款支付'),
(2, 1, 'CUST-2026-002', '美团智慧物流与仓储事业部', '物流高科技', 'A', 'ONLINE', '13600136002', '010-55556666', 'supply-chain@meituan.com', '北京市朝阳区望京东路 4 号恒基伟业大厦', 0, 0, 1, 1, '超级管理员', '2026-10-19 11:00:00', '2026-10-24 15:00:00', '正在推进智能巡检与ERP出入库集成方案'),
(3, 1, 'CUST-2026-003', '华南智能高端装备制造集团', '高端制造', 'B', 'EXHIBITION', '13500135003', '020-33334444', 'contact@south-equip.com', '广州市黄埔区中新广州知识城创新大道', 0, 1, 0, NULL, '公海池', '2026-10-01 10:00:00', NULL, '展会收集，超过15天未跟进自动退入公海，开放销售认领抢单')
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO crm_business (id, tenant_id, business_code, name, customer_id, customer_name, total_price, deal_date, stage, win_rate, owner_user_id, owner_user_name, remark)
VALUES
(1, 1, 'BIZ-2026-8801', '华润智慧园区数字孪生与运营中台二期', 1, '华润数字科技有限公司', 1280000.00, '2026-11-15', 'NEGOTIATION', 80, 1, '超级管理员', '已通过技术答辩与方案论证，正在进行商务法务条款协商'),
(2, 1, 'BIZ-2026-8802', '美团自动化立体仓库调度管控套件', 2, '美团智慧物流与仓储事业部', 680000.00, '2026-11-30', 'PROPOSAL', 60, 1, '超级管理员', '已提交第二版系统报价与技术架构响应书')
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO crm_contract (id, tenant_id, contract_no, name, customer_id, customer_name, business_id, business_name, total_price, start_time, end_time, signer_user_name, status, remark)
VALUES
(1, 1, 'CT-20261020-001', '华润智慧园区一期私有化实施与授权许可合同', 1, '华润数字科技有限公司', 1, '华润智慧园区数字孪生与运营中台二期', 860000.00, '2026-10-01', '2027-09-30', '超级管理员', 'APPROVED', '一期项目建设合同，经平台工作流全流程审批生效'),
(2, 1, 'CT-20261020-002', '美团智能巡检系统年度维保与技术支持协议', 2, '美团智慧物流与仓储事业部', 2, '美团自动化立体仓库调度管控套件', 240000.00, '2026-11-01', '2027-10-31', '超级管理员', 'APPROVING', '年度标准维保协议，目前处于部门主管审批节点')
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO crm_receivable (id, tenant_id, receivable_no, contract_id, contract_name, customer_id, customer_name, price, return_time, pay_type, status, remark)
VALUES
(1, 1, 'REC-20261020-01', 1, '华润智慧园区一期私有化实施与授权许可合同', 1, '华润数字科技有限公司', 430000.00, '2026-10-15', 'BANK_TRANSFER', 'APPROVED', '一期项目首笔 50% 预付款，招商银行公账到账核销完毕'),
(2, 1, 'REC-20261020-02', 1, '华润智慧园区一期私有化实施与授权许可合同', 1, '华润数字科技有限公司', 258000.00, '2026-11-20', 'BANK_TRANSFER', 'PENDING', '一期中间节点里程碑交付款（30%），等待财务复核')
ON DUPLICATE KEY UPDATE receivable_no = VALUES(receivable_no);

INSERT INTO crm_follow_up_record (id, tenant_id, biz_type, biz_id, follow_type, content, next_contact_time, contact_user_name)
VALUES
(1, 1, 'CUSTOMER', 1, 'VISIT', '现场拜访华润深圳湾技术团队，交流私有化部署网络安全与数据脱敏规则，对方高度认可 AST 多租户隔离方案。', '2026-10-28 09:30:00', '超级管理员'),
(2, 1, 'BUSINESS', 1, 'CALL', '电话对接华润采购商务部王经理，商定二期数字孪生模块折扣及回款节点。', '2026-10-25 15:00:00', '超级管理员'),
(3, 1, 'CLUE', 1, 'WECHAT', '通过微信发送微电网技术白皮书和在线大屏体验 Demo 链接，客户反馈非常好。', '2026-10-25 14:00:00', '超级管理员')
ON DUPLICATE KEY UPDATE content = VALUES(content);

-- 8. 注册 CRM 前端菜单体系与管理员赋权
INSERT INTO iam_menu (id, tenant_id, parent_id, menu_type, menu_code, menu_name, route_path, component_path, permission_code, icon, sort_no, status, visible, keep_alive)
VALUES
(400, 1, 0, 'DIRECTORY', 'crm', '客户关系 CRM', '/crm', '', 'crm:customer:query', 'TrendCharts', 6, 'ENABLED', 1, 1),
(401, 1, 400, 'MENU', 'crm_customer', '客户与公海池', '/crm/customer', '@/views/crm/CrmView.vue', 'crm:customer:query', 'UserFilled', 1, 'ENABLED', 1, 1),
(402, 1, 400, 'MENU', 'crm_clue', '销售线索跟进', '/crm/clue', '@/views/crm/CrmView.vue', 'crm:clue:query', 'Phone', 2, 'ENABLED', 1, 1),
(403, 1, 400, 'MENU', 'crm_business', '商机阶段漏斗', '/crm/business', '@/views/crm/CrmView.vue', 'crm:business:query', 'Money', 3, 'ENABLED', 1, 1),
(404, 1, 400, 'MENU', 'crm_contract', '商务合同管控', '/crm/contract', '@/views/crm/CrmView.vue', 'crm:contract:query', 'Document', 4, 'ENABLED', 1, 1),
(405, 1, 400, 'MENU', 'crm_receivable', '财务回款结算', '/crm/receivable', '@/views/crm/CrmView.vue', 'crm:receivable:query', 'Wallet', 5, 'ENABLED', 1, 1)
ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name);

INSERT IGNORE INTO iam_role_menu (role_id, menu_id, tenant_id)
VALUES
(1, 400, 1),
(1, 401, 1),
(1, 402, 1),
(1, 403, 1),
(1, 404, 1),
(1, 405, 1);
