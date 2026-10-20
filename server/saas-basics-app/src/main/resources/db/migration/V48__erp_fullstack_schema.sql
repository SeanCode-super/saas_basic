-- =====================================================================
-- V48__erp_fullstack_schema.sql
-- Antigravity SaaS 供应链进销存 (ERP) 标杆系统数据结构与初始数据
-- =====================================================================

-- 1. 商品物料档案表
CREATE TABLE IF NOT EXISTS erp_product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    product_code VARCHAR(64) NOT NULL,
    product_name VARCHAR(128) NOT NULL,
    category VARCHAR(64) DEFAULT 'DEFAULT',
    specification VARCHAR(128),
    unit VARCHAR(32) DEFAULT '件',
    purchase_price DECIMAL(12,2) DEFAULT 0.00,
    sale_price DECIMAL(12,2) DEFAULT 0.00,
    min_stock INT DEFAULT 10,
    status VARCHAR(32) DEFAULT 'ENABLED',
    remark VARCHAR(500),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT DEFAULT 1,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 1,
    deleted TINYINT DEFAULT 0,
    INDEX idx_erp_prod_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE erp_product ADD CONSTRAINT uk_erp_prod_code UNIQUE (tenant_id, product_code, (CASE WHEN deleted = 0 THEN 0 ELSE NULL END));

-- 2. 供应商档案表
CREATE TABLE IF NOT EXISTS erp_supplier (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    supplier_code VARCHAR(64) NOT NULL,
    supplier_name VARCHAR(128) NOT NULL,
    contact_person VARCHAR(64),
    contact_phone VARCHAR(32),
    status VARCHAR(32) DEFAULT 'ENABLED',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT DEFAULT 1,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 1,
    deleted TINYINT DEFAULT 0,
    INDEX idx_erp_sup_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE erp_supplier ADD CONSTRAINT uk_erp_sup_code UNIQUE (tenant_id, supplier_code, (CASE WHEN deleted = 0 THEN 0 ELSE NULL END));

-- 3. 客户档案表
CREATE TABLE IF NOT EXISTS erp_customer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    customer_code VARCHAR(64) NOT NULL,
    customer_name VARCHAR(128) NOT NULL,
    contact_person VARCHAR(64),
    contact_phone VARCHAR(32),
    status VARCHAR(32) DEFAULT 'ENABLED',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT DEFAULT 1,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 1,
    deleted TINYINT DEFAULT 0,
    INDEX idx_erp_cust_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE erp_customer ADD CONSTRAINT uk_erp_cust_code UNIQUE (tenant_id, customer_code, (CASE WHEN deleted = 0 THEN 0 ELSE NULL END));

-- 4. 仓库档案表
CREATE TABLE IF NOT EXISTS erp_warehouse (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    warehouse_code VARCHAR(64) NOT NULL,
    warehouse_name VARCHAR(128) NOT NULL,
    manager_name VARCHAR(64),
    phone VARCHAR(32),
    address VARCHAR(256),
    status VARCHAR(32) DEFAULT 'ENABLED',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT DEFAULT 1,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 1,
    deleted TINYINT DEFAULT 0,
    INDEX idx_erp_wh_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE erp_warehouse ADD CONSTRAINT uk_erp_wh_code UNIQUE (tenant_id, warehouse_code, (CASE WHEN deleted = 0 THEN 0 ELSE NULL END));

-- 5. 实时多仓库存表
CREATE TABLE IF NOT EXISTS erp_inventory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    stock_quantity INT DEFAULT 0,
    locked_quantity INT DEFAULT 0,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_erp_inv_lookup (tenant_id, product_id, warehouse_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. 出入库台账流水明细表
CREATE TABLE IF NOT EXISTS erp_inventory_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    record_no VARCHAR(64) NOT NULL,
    record_type VARCHAR(32) NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(128),
    warehouse_id BIGINT NOT NULL,
    warehouse_name VARCHAR(128),
    change_quantity INT NOT NULL,
    before_quantity INT NOT NULL,
    after_quantity INT NOT NULL,
    operator_name VARCHAR(64),
    remark VARCHAR(256),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_erp_rec_tenant (tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. 采购订单表
CREATE TABLE IF NOT EXISTS erp_purchase_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    supplier_id BIGINT NOT NULL,
    supplier_name VARCHAR(128),
    total_amount DECIMAL(12,2) DEFAULT 0.00,
    status VARCHAR(32) DEFAULT 'PENDING',
    remark VARCHAR(500),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT DEFAULT 1,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 1,
    deleted TINYINT DEFAULT 0,
    INDEX idx_erp_po_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. 采购明细项
CREATE TABLE IF NOT EXISTS erp_purchase_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(128),
    quantity INT NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    subtotal DECIMAL(12,2) NOT NULL,
    INDEX idx_erp_pi_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 9. 销售订单表
CREATE TABLE IF NOT EXISTS erp_sale_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    customer_id BIGINT NOT NULL,
    customer_name VARCHAR(128),
    total_amount DECIMAL(12,2) DEFAULT 0.00,
    status VARCHAR(32) DEFAULT 'PENDING',
    remark VARCHAR(500),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT DEFAULT 1,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 1,
    deleted TINYINT DEFAULT 0,
    INDEX idx_erp_so_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 10. 销售明细项
CREATE TABLE IF NOT EXISTS erp_sale_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(128),
    quantity INT NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    subtotal DECIMAL(12,2) NOT NULL,
    INDEX idx_erp_si_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 初始基础数据预置 (种子数据)
INSERT INTO erp_product (id, tenant_id, product_code, product_name, category, specification, unit, purchase_price, sale_price, min_stock, status, remark)
VALUES 
(1, 1, 'PRD-UPS-50K', '高频工业级 UPS 50kVA 主机', '电力装备', '50kVA/380V 三相', '台', 18500.00, 26800.00, 5, 'ENABLED', '机房核心动力不间断保障'),
(2, 1, 'PRD-CAB-42U', '微模块智能机柜', 'IT设施', '42U 600*1200mm', '套', 6200.00, 8900.00, 10, 'ENABLED', '标准高密微模块服务器机柜'),
(3, 1, 'PRD-BAT-100A', '磷酸铁锂储能电池组', '新能源', '51.2V 100Ah 储能专用', '组', 4200.00, 5980.00, 20, 'ENABLED', '长循环寿命高倍率电芯'),
(4, 1, 'PRD-SENS-IOT', '高精度工业温湿度变送器', '传感器', 'RS485 Modbus-RTU 精度0.2%', '支', 350.00, 580.00, 50, 'ENABLED', '动环监控系统前置传感器')
ON DUPLICATE KEY UPDATE product_name = VALUES(product_name);

INSERT INTO erp_supplier (id, tenant_id, supplier_code, supplier_name, contact_person, contact_phone, status)
VALUES
(1, 1, 'SUP-HW-001', '华为数字能源技术有限公司', '李经理', '13800138001', 'ENABLED'),
(2, 1, 'SUP-CATL-002', '宁德时代新能源科技股份有限公司', '陈总监', '13900139002', 'ENABLED')
ON DUPLICATE KEY UPDATE supplier_name = VALUES(supplier_name);

INSERT INTO erp_customer (id, tenant_id, customer_code, customer_name, contact_person, contact_phone, status)
VALUES
(1, 1, 'CUST-BYD-001', '比亚迪汽车工业有限公司', '张部长', '13700137001', 'ENABLED'),
(2, 1, 'CUST-SF-002', '顺丰速运全球物流中枢', '王总', '13600136002', 'ENABLED')
ON DUPLICATE KEY UPDATE customer_name = VALUES(customer_name);

INSERT INTO erp_warehouse (id, tenant_id, warehouse_code, warehouse_name, manager_name, phone, address, status)
VALUES
(1, 1, 'WH-SH-01', '华东一号中心立体总仓', '刘仓管', '021-88889999', '上海市青浦区崧泽大道 1888 号自动化物流园', 'ENABLED'),
(2, 1, 'WH-SZ-02', '华南保税智造配送仓', '周主管', '0755-66667777', '广东省深圳市宝安区福永保税物流园 C 栋', 'ENABLED')
ON DUPLICATE KEY UPDATE warehouse_name = VALUES(warehouse_name);

INSERT INTO erp_inventory (id, tenant_id, product_id, warehouse_id, stock_quantity, locked_quantity)
VALUES
(1, 1, 1, 1, 28, 2),
(2, 1, 2, 1, 64, 4),
(3, 1, 3, 1, 150, 10),
(4, 1, 4, 1, 320, 0)
ON DUPLICATE KEY UPDATE stock_quantity = VALUES(stock_quantity);

INSERT INTO erp_inventory_record (id, tenant_id, record_no, record_type, product_id, product_name, warehouse_id, warehouse_name, change_quantity, before_quantity, after_quantity, operator_name, remark)
VALUES
(1, 1, 'REC-20261019-001', 'PURCHASE_IN', 1, '高频工业级 UPS 50kVA 主机', 1, '华东一号中心立体总仓', 10, 18, 28, '系统管理员', '采购订单 PO-20261019-8802 自动化验收核销入库'),
(2, 1, 'REC-20261019-002', 'SALE_OUT', 2, '微模块智能机柜', 1, '华东一号中心立体总仓', -6, 70, 64, '系统管理员', '销售订单 SO-20261019-5501 履约扣减出库')
ON DUPLICATE KEY UPDATE record_no = VALUES(record_no);

-- 注册 ERP 进销存前端菜单与权限
INSERT INTO iam_menu (id, tenant_id, parent_id, menu_type, menu_code, menu_name, route_path, component_path, permission_code, icon, sort_no, status)
VALUES
(300, 1, 0, 'DIRECTORY', 'erp', '进销存 ERP', '/erp', '', 'erp:inventory:query', 'Briefcase', 5, 'ENABLED'),
(301, 1, 300, 'MENU', 'erp_product', '物料商品档案', '/erp/product', '@/views/erp/ErpView.vue', 'erp:product:query', 'Goods', 1, 'ENABLED'),
(302, 1, 300, 'MENU', 'erp_inventory', '实时库存台账', '/erp/inventory', '@/views/erp/ErpView.vue', 'erp:inventory:query', 'Box', 2, 'ENABLED'),
(303, 1, 300, 'MENU', 'erp_purchase', '采购订单履约', '/erp/purchase', '@/views/erp/ErpView.vue', 'erp:purchase:query', 'ShoppingCart', 3, 'ENABLED')
ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name);

-- 赋权给管理员角色
INSERT INTO iam_role_menu (role_id, menu_id, tenant_id)
VALUES
(1, 300, 1),
(1, 301, 1),
(1, 302, 1),
(1, 303, 1)
ON DUPLICATE KEY UPDATE tenant_id = VALUES(tenant_id);

