-- Older orders have no confirmed receiving warehouse. Pending orders require an explicit review before posting.
ALTER TABLE erp_purchase_order ADD COLUMN warehouse_id BIGINT NULL AFTER supplier_name;

-- Previously posted lines remain part of their historical ledger. New purchase receipts link each entry to its order line.
ALTER TABLE erp_inventory_record
    ADD COLUMN purchase_item_id BIGINT NULL AFTER record_type,
    ADD UNIQUE KEY uk_erp_receipt_purchase_item (tenant_id, purchase_item_id);

INSERT INTO iam_api_resource (tenant_id, resource_code, resource_name, http_method, url_pattern,
                              auth_required, status, remark, created_by, updated_by)
SELECT t.id, resource.code, resource.name, resource.method, resource.path,
       1, 'ENABLED', 'ERP 采购主数据及历史单复核', 0, 0
FROM plat_tenant t
CROSS JOIN (
    SELECT 'erp:supplier:create' AS code, '新增供应商' AS name, 'POST' AS method, '/api/erp/suppliers' AS path
    UNION ALL SELECT 'erp:warehouse:create', '新增收货仓库', 'POST', '/api/erp/warehouses'
    UNION ALL SELECT 'erp:purchase:confirm', '核对历史采购单', 'PUT', '/api/erp/purchase-orders/*/confirmation'
) resource
WHERE t.deleted = 0 AND NOT EXISTS (
    SELECT 1 FROM iam_api_resource a WHERE a.tenant_id = t.id AND a.resource_code = resource.code AND a.deleted = 0
);

INSERT INTO iam_role_api (tenant_id, role_id, api_resource_id, remark, created_by, updated_by)
SELECT r.tenant_id, r.id, a.id, 'ERP 采购主数据及历史单复核', 0, 0
FROM iam_role r
JOIN plat_tenant t ON t.id = r.tenant_id AND t.deleted = 0
JOIN iam_api_resource a ON a.tenant_id = r.tenant_id AND a.deleted = 0
    AND a.resource_code IN ('erp:supplier:create', 'erp:warehouse:create', 'erp:purchase:confirm')
WHERE r.deleted = 0 AND r.status = 'ENABLED' AND r.is_system = 1 AND r.data_scope_type = 'ALL'
  AND r.role_code IN ('platform_super_admin', CONCAT(t.tenant_code, '_super_admin'))
  AND NOT EXISTS (
    SELECT 1 FROM iam_role_api existing WHERE existing.tenant_id = r.tenant_id
      AND existing.role_id = r.id AND existing.api_resource_id = a.id AND existing.deleted = 0
  );
