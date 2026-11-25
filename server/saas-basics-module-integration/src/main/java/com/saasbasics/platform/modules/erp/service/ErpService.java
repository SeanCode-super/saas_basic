package com.saasbasics.platform.modules.erp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.CurrentDepartmentResolver;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.erp.dto.ErpInventoryResponse;
import com.saasbasics.platform.modules.erp.dto.ErpInventoryRecordResponse;
import com.saasbasics.platform.modules.erp.dto.ErpMasterDataOption;
import com.saasbasics.platform.modules.erp.dto.ErpMasterDataRequest;
import com.saasbasics.platform.modules.erp.dto.ErpPurchaseItemResponse;
import com.saasbasics.platform.modules.erp.dto.ErpStockAdjustmentRequest;
import com.saasbasics.platform.modules.erp.dto.ErpProductResponse;
import com.saasbasics.platform.modules.erp.dto.ErpProductSaveRequest;
import com.saasbasics.platform.modules.erp.dto.ErpPurchaseItemRequest;
import com.saasbasics.platform.modules.erp.dto.ErpPurchaseOrderResponse;
import com.saasbasics.platform.modules.erp.dto.ErpPurchaseOrderSaveRequest;
import com.saasbasics.platform.modules.erp.entity.ErpInventoryEntity;
import com.saasbasics.platform.modules.erp.entity.ErpInventoryRecordEntity;
import com.saasbasics.platform.modules.erp.entity.ErpProductEntity;
import com.saasbasics.platform.modules.erp.entity.ErpPurchaseItemEntity;
import com.saasbasics.platform.modules.erp.entity.ErpPurchaseOrderEntity;
import com.saasbasics.platform.modules.erp.entity.ErpWarehouseEntity;
import com.saasbasics.platform.modules.erp.entity.ErpSupplierEntity;
import com.saasbasics.platform.modules.erp.mapper.ErpInventoryMapper;
import com.saasbasics.platform.modules.erp.mapper.ErpInventoryRecordMapper;
import com.saasbasics.platform.modules.erp.mapper.ErpProductMapper;
import com.saasbasics.platform.modules.erp.mapper.ErpPurchaseItemMapper;
import com.saasbasics.platform.modules.erp.mapper.ErpPurchaseOrderMapper;
import com.saasbasics.platform.modules.erp.mapper.ErpWarehouseMapper;
import com.saasbasics.platform.modules.erp.mapper.ErpSupplierMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ErpService {

    private final ErpProductMapper productMapper;
    private final ErpInventoryMapper inventoryMapper;
    private final ErpInventoryRecordMapper recordMapper;
    private final ErpPurchaseOrderMapper purchaseOrderMapper;
    private final ErpPurchaseItemMapper purchaseItemMapper;
    private final ErpWarehouseMapper warehouseMapper;
    private final ErpSupplierMapper supplierMapper;
    private final ObjectProvider<CurrentDepartmentResolver> departmentResolverProvider;

    public ErpService(
            ErpProductMapper productMapper,
            ErpInventoryMapper inventoryMapper,
            ErpInventoryRecordMapper recordMapper,
            ErpPurchaseOrderMapper purchaseOrderMapper,
            ErpPurchaseItemMapper purchaseItemMapper,
            ErpWarehouseMapper warehouseMapper,
            ErpSupplierMapper supplierMapper,
            ObjectProvider<CurrentDepartmentResolver> departmentResolverProvider
    ) {
        this.productMapper = productMapper;
        this.inventoryMapper = inventoryMapper;
        this.recordMapper = recordMapper;
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.purchaseItemMapper = purchaseItemMapper;
        this.warehouseMapper = warehouseMapper;
        this.supplierMapper = supplierMapper;
        this.departmentResolverProvider = departmentResolverProvider;
    }

    public List<ErpProductResponse> listProducts() {
        Long tenantId = getTenantId();
        List<ErpProductEntity> list = productMapper.selectList(new LambdaQueryWrapper<ErpProductEntity>()
                .eq(ErpProductEntity::getTenantId, tenantId)
                .eq(ErpProductEntity::getDeleted, 0)
                .orderByDesc(ErpProductEntity::getId));

        return list.stream().map(p -> new ErpProductResponse(
                p.getId(),
                p.getProductCode(),
                p.getProductName(),
                p.getCategory(),
                p.getSpecification(),
                p.getUnit(),
                p.getPurchasePrice(),
                p.getSalePrice(),
                p.getMinStock(),
                p.getStatus(),
                p.getRemark(),
                p.getCreatedAt()
        )).toList();
    }

    @Transactional
    public ErpProductResponse saveProduct(ErpProductSaveRequest req) {
        Long tenantId = getTenantId();
        Long userId = getUserId();

        ErpProductEntity entity = new ErpProductEntity();
        entity.setTenantId(tenantId);
        entity.setProductCode(req.productCode());
        entity.setProductName(req.productName());
        entity.setCategory(req.category() != null ? req.category() : "DEFAULT");
        entity.setSpecification(req.specification());
        entity.setUnit(req.unit() != null ? req.unit() : "件");
        entity.setPurchasePrice(req.purchasePrice() != null ? req.purchasePrice() : BigDecimal.ZERO);
        entity.setSalePrice(req.salePrice() != null ? req.salePrice() : BigDecimal.ZERO);
        entity.setMinStock(req.minStock() != null ? req.minStock() : 10);
        entity.setStatus(req.status() != null ? req.status() : "ENABLED");
        entity.setRemark(req.remark());
        entity.setDeptId(resolveCurrentDeptId());
        entity.setCreatedBy(userId);
        entity.setUpdatedBy(userId);
        entity.setDeleted(0);
        productMapper.insert(entity);

        List<ErpWarehouseEntity> warehouses = enabledWarehouses();
        if (!warehouses.isEmpty()) {
            ErpInventoryEntity inventory = new ErpInventoryEntity();
            inventory.setTenantId(tenantId);
            inventory.setProductId(entity.getId());
            inventory.setWarehouseId(warehouses.getFirst().getId());
            inventory.setStockQuantity(0);
            inventory.setLockedQuantity(0);
            inventoryMapper.insert(inventory);
        }

        return new ErpProductResponse(
                entity.getId(),
                entity.getProductCode(),
                entity.getProductName(),
                entity.getCategory(),
                entity.getSpecification(),
                entity.getUnit(),
                entity.getPurchasePrice(),
                entity.getSalePrice(),
                entity.getMinStock(),
                entity.getStatus(),
                entity.getRemark(),
                LocalDateTime.now()
        );
    }

    @Transactional
    public void deleteProduct(Long id) {
        Long tenantId = getTenantId();
        ErpProductEntity p = productMapper.selectById(id);
        if (p == null || !p.getTenantId().equals(tenantId) || p.getDeleted() != 0) {
            throw new BizException("NOT_FOUND", "物料商品不存在");
        }
        p.setDeleted(1);
        productMapper.updateById(p);
    }

    public List<ErpInventoryResponse> listInventory() {
        Long tenantId = getTenantId();
        List<ErpInventoryEntity> invList = inventoryMapper.selectList(new LambdaQueryWrapper<ErpInventoryEntity>()
                .eq(ErpInventoryEntity::getTenantId, tenantId)
                .orderByDesc(ErpInventoryEntity::getId));

        Map<Long, ErpProductEntity> prodMap = productMapper.selectList(new LambdaQueryWrapper<ErpProductEntity>()
                .eq(ErpProductEntity::getTenantId, tenantId)
                .eq(ErpProductEntity::getDeleted, 0))
                .stream().collect(Collectors.toMap(ErpProductEntity::getId, Function.identity()));

        Map<Long, String> warehouses = warehouseNames(tenantId);
        List<ErpInventoryResponse> result = new ArrayList<>();
        for (ErpInventoryEntity inv : invList) {
            ErpProductEntity prod = prodMap.get(inv.getProductId());
            if (prod == null) continue;

            int stock = inv.getStockQuantity() != null ? inv.getStockQuantity() : 0;
            int locked = inv.getLockedQuantity() != null ? inv.getLockedQuantity() : 0;
            int avail = stock - locked;
            int min = prod.getMinStock() != null ? prod.getMinStock() : 10;
            boolean low = stock <= min;
            BigDecimal val = prod.getPurchasePrice() == null ? null : prod.getPurchasePrice().multiply(BigDecimal.valueOf(stock));

            result.add(new ErpInventoryResponse(
                    inv.getId(),
                    prod.getId(),
                    prod.getProductCode(),
                    prod.getProductName(),
                    prod.getSpecification(),
                    prod.getUnit(),
                    inv.getWarehouseId(),
                    warehouses.get(inv.getWarehouseId()),
                    stock,
                    locked,
                    avail,
                    min,
                    low,
                    val,
                    inv.getUpdatedAt()
            ));
        }
        return result;
    }

    public List<ErpInventoryRecordResponse> listInventoryRecords(Long productId, Long warehouseId) {
        Long tenantId = getTenantId();
        List<ErpInventoryRecordEntity> list = recordMapper.selectList(new LambdaQueryWrapper<ErpInventoryRecordEntity>()
                .eq(ErpInventoryRecordEntity::getTenantId, tenantId)
                .eq(productId != null, ErpInventoryRecordEntity::getProductId, productId)
                .eq(warehouseId != null, ErpInventoryRecordEntity::getWarehouseId, warehouseId)
                .orderByDesc(ErpInventoryRecordEntity::getId)
                .last("LIMIT 50"));
        return list.stream().map(r -> new ErpInventoryRecordResponse(
                r.getId(),
                r.getTenantId(),
                r.getRecordNo(),
                r.getRecordType(),
                r.getPurchaseItemId(),
                r.getProductId(),
                r.getProductName(),
                r.getWarehouseId(),
                r.getWarehouseName(),
                r.getChangeQuantity(),
                r.getBeforeQuantity(),
                r.getAfterQuantity(),
                r.getOperatorName(),
                r.getRemark(),
                r.getCreatedAt()
        )).toList();
    }

    public List<ErpMasterDataOption> listSuppliers() {
        return supplierMapper.selectList(new LambdaQueryWrapper<ErpSupplierEntity>()
                .eq(ErpSupplierEntity::getTenantId, getTenantId())
                .eq(ErpSupplierEntity::getDeleted, 0).eq(ErpSupplierEntity::getStatus, "ENABLED")
                .orderByAsc(ErpSupplierEntity::getSupplierCode)).stream()
                .map(row -> new ErpMasterDataOption(row.getId(), row.getSupplierCode(), row.getSupplierName())).toList();
    }

    public List<ErpMasterDataOption> listWarehouses() {
        return enabledWarehouses().stream()
                .map(row -> new ErpMasterDataOption(row.getId(), row.getWarehouseCode(), row.getWarehouseName())).toList();
    }

    private List<ErpWarehouseEntity> enabledWarehouses() {
        return warehouseMapper.selectList(new LambdaQueryWrapper<ErpWarehouseEntity>()
                .eq(ErpWarehouseEntity::getTenantId, getTenantId())
                .eq(ErpWarehouseEntity::getDeleted, 0).eq(ErpWarehouseEntity::getStatus, "ENABLED")
                .orderByAsc(ErpWarehouseEntity::getId));
    }

    private Map<Long, String> warehouseNames(Long tenantId) {
        return warehouseMapper.selectList(new LambdaQueryWrapper<ErpWarehouseEntity>()
                .eq(ErpWarehouseEntity::getTenantId, tenantId)).stream()
                .collect(Collectors.toMap(ErpWarehouseEntity::getId, ErpWarehouseEntity::getWarehouseName));
    }

    @Transactional
    public ErpMasterDataOption createSupplier(ErpMasterDataRequest request) {
        ErpSupplierEntity supplier = new ErpSupplierEntity();
        supplier.setTenantId(getTenantId());
        supplier.setSupplierCode(request.code().trim());
        supplier.setSupplierName(request.name().trim());
        supplier.setStatus("ENABLED");
        supplier.setCreatedBy(getUserId());
        supplier.setUpdatedBy(getUserId());
        supplierMapper.insert(supplier);
        return new ErpMasterDataOption(supplier.getId(), supplier.getSupplierCode(), supplier.getSupplierName());
    }

    @Transactional
    public ErpMasterDataOption createWarehouse(ErpMasterDataRequest request) {
        ErpWarehouseEntity warehouse = new ErpWarehouseEntity();
        warehouse.setTenantId(getTenantId());
        warehouse.setWarehouseCode(request.code().trim());
        warehouse.setWarehouseName(request.name().trim());
        warehouse.setStatus("ENABLED");
        warehouse.setCreatedBy(getUserId());
        warehouse.setUpdatedBy(getUserId());
        warehouseMapper.insert(warehouse);
        return new ErpMasterDataOption(warehouse.getId(), warehouse.getWarehouseCode(), warehouse.getWarehouseName());
    }

    private ErpWarehouseEntity requireWarehouse(Long tenantId, Long id) {
        var warehouse = warehouseMapper.selectOne(new LambdaQueryWrapper<ErpWarehouseEntity>()
                .eq(ErpWarehouseEntity::getTenantId, tenantId).eq(ErpWarehouseEntity::getId, id)
                .eq(ErpWarehouseEntity::getStatus, "ENABLED").eq(ErpWarehouseEntity::getDeleted, 0));
        if (warehouse == null) throw new BizException("INVALID_WAREHOUSE", "收货仓库不存在或已停用");
        return warehouse;
    }

    public List<ErpPurchaseOrderResponse> listPurchaseOrders() {
        Long tenantId = getTenantId();
        var orders = purchaseOrderMapper.selectList(new LambdaQueryWrapper<ErpPurchaseOrderEntity>()
                .eq(ErpPurchaseOrderEntity::getTenantId, tenantId).eq(ErpPurchaseOrderEntity::getDeleted, 0)
                .orderByDesc(ErpPurchaseOrderEntity::getId));
        if (orders.isEmpty()) return List.of();
        var items = purchaseItemMapper.selectList(new LambdaQueryWrapper<ErpPurchaseItemEntity>()
                .eq(ErpPurchaseItemEntity::getTenantId, tenantId)
                .in(ErpPurchaseItemEntity::getOrderId, orders.stream().map(ErpPurchaseOrderEntity::getId).toList())
                .orderByAsc(ErpPurchaseItemEntity::getId)).stream()
                .collect(Collectors.groupingBy(ErpPurchaseItemEntity::getOrderId));
        var names = warehouseNames(tenantId);
        return orders.stream().map(order -> response(order, names.get(order.getWarehouseId()),
                items.getOrDefault(order.getId(), List.of()))).toList();
    }

    private ErpPurchaseOrderResponse response(ErpPurchaseOrderEntity order, String warehouse,
                                              List<ErpPurchaseItemEntity> items) {
        var lines = items.stream().map(item -> new ErpPurchaseItemResponse(item.getProductId(),
                item.getProductName(), item.getQuantity(), item.getUnitPrice(), item.getSubtotal())).toList();
        return new ErpPurchaseOrderResponse(order.getId(), order.getOrderNo(), order.getSupplierId(),
                order.getSupplierName(), order.getTotalAmount(), order.getStatus(), order.getRemark(),
                order.getCreatedAt(), order.getWarehouseId(), warehouse, lines);
    }

    @Transactional
    public ErpPurchaseOrderResponse createPurchaseOrder(ErpPurchaseOrderSaveRequest request) {
        var order = new ErpPurchaseOrderEntity();
        order.setTenantId(getTenantId());
        order.setOrderNo("PO-" + UUID.randomUUID());
        order.setStatus("PENDING");
        order.setDeptId(resolveCurrentDeptId());
        order.setCreatedBy(getUserId());
        order.setCreatedAt(LocalDateTime.now());
        order.setDeleted(0);
        return saveOrder(order, request);
    }

    @Transactional
    public ErpPurchaseOrderResponse confirmLegacyOrder(Long id, ErpPurchaseOrderSaveRequest request) {
        var order = lockPendingOrder(id);
        if (order.getWarehouseId() != null && purchaseItemMapper.exists(new LambdaQueryWrapper<ErpPurchaseItemEntity>()
                .eq(ErpPurchaseItemEntity::getTenantId, order.getTenantId()).eq(ErpPurchaseItemEntity::getOrderId, id))) {
            throw new BizException("ORDER_ALREADY_CONFIRMED", "该采购单已经确认");
        }
        return saveOrder(order, request);
    }

    private ErpPurchaseOrderResponse saveOrder(ErpPurchaseOrderEntity order, ErpPurchaseOrderSaveRequest request) {
        Long tenantId = order.getTenantId();
        var supplier = supplierMapper.selectOne(new LambdaQueryWrapper<ErpSupplierEntity>()
                .eq(ErpSupplierEntity::getTenantId, tenantId).eq(ErpSupplierEntity::getId, request.supplierId())
                .eq(ErpSupplierEntity::getStatus, "ENABLED").eq(ErpSupplierEntity::getDeleted, 0));
        if (supplier == null) throw new BizException("INVALID_SUPPLIER", "供应商不存在或已停用");
        var warehouse = requireWarehouse(tenantId, request.warehouseId());
        var products = productMapper.selectList(new LambdaQueryWrapper<ErpProductEntity>()
                .eq(ErpProductEntity::getTenantId, tenantId).eq(ErpProductEntity::getDeleted, 0)
                .eq(ErpProductEntity::getStatus, "ENABLED")
                .in(ErpProductEntity::getId, request.items().stream().map(ErpPurchaseItemRequest::productId).distinct().toList()))
                .stream().collect(Collectors.toMap(ErpProductEntity::getId, Function.identity()));
        List<ErpPurchaseItemEntity> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (var input : request.items()) {
            var product = products.get(input.productId());
            if (product == null) throw new BizException("INVALID_PRODUCT", "采购物料不存在或已停用");
            var line = new ErpPurchaseItemEntity();
            line.setTenantId(tenantId);
            line.setProductId(product.getId());
            line.setProductName(product.getProductName());
            line.setQuantity(input.quantity());
            line.setUnitPrice(input.unitPrice());
            line.setSubtotal(input.unitPrice().multiply(BigDecimal.valueOf(input.quantity())));
            total = total.add(line.getSubtotal());
            lines.add(line);
        }
        if (total.compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw new BizException("INVALID_AMOUNT", "采购单合计超过金额上限");
        }
        if (request.totalAmount() != null && total.compareTo(request.totalAmount()) != 0) {
            throw new BizException("INVALID_AMOUNT", "采购单总额与明细合计不符");
        }
        order.setWarehouseId(warehouse.getId());
        order.setSupplierId(supplier.getId());
        order.setSupplierName(supplier.getSupplierName());
        order.setTotalAmount(total);
        order.setUpdatedBy(getUserId());
        order.setRemark(request.remark());
        if (order.getId() == null) {
            purchaseOrderMapper.insert(order);
        } else {
            purchaseOrderMapper.updateById(order);
            purchaseItemMapper.delete(new LambdaQueryWrapper<ErpPurchaseItemEntity>()
                    .eq(ErpPurchaseItemEntity::getTenantId, tenantId).eq(ErpPurchaseItemEntity::getOrderId, order.getId()));
        }
        for (var line : lines) {
            line.setOrderId(order.getId());
            purchaseItemMapper.insert(line);
        }
        return response(order, warehouse.getWarehouseName(), lines);
    }

    private ErpPurchaseOrderEntity lockPendingOrder(Long id) {
        var order = purchaseOrderMapper.lockForUpdate(getTenantId(), id);
        if (order == null) throw new BizException("NOT_FOUND", "采购单不存在");
        if (!"PENDING".equals(order.getStatus())) throw new BizException("ORDER_NOT_PENDING", "仅待入库的采购单可操作");
        return order;
    }

    @Transactional
    public void stockInPurchaseOrder(Long orderId, Long requestedWarehouse) {
        var order = lockPendingOrder(orderId);
        Long tenantId = order.getTenantId();
        if (order.getWarehouseId() == null) throw new BizException("REVIEW_REQUIRED", "请先核对采购明细并确认收货仓库");
        if (requestedWarehouse != null && !requestedWarehouse.equals(order.getWarehouseId())) {
            throw new BizException("INVALID_WAREHOUSE", "入库仓库与采购单不一致");
        }
        var warehouse = requireWarehouse(tenantId, order.getWarehouseId());
        var items = purchaseItemMapper.selectList(new LambdaQueryWrapper<ErpPurchaseItemEntity>()
                .eq(ErpPurchaseItemEntity::getTenantId, tenantId).eq(ErpPurchaseItemEntity::getOrderId, orderId)
                .orderByAsc(ErpPurchaseItemEntity::getProductId, ErpPurchaseItemEntity::getId));
        if (items.isEmpty()) throw new BizException("REVIEW_REQUIRED", "采购单缺少采购明细");
        BigDecimal total = BigDecimal.ZERO;
        for (var item : items) {
            if (item.getQuantity() <= 0 || item.getUnitPrice().signum() < 0 ||
                    item.getSubtotal().compareTo(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()))) != 0) {
                throw new BizException("INVALID_AMOUNT", "采购明细的数量或金额不符");
            }
            total = total.add(item.getSubtotal());
        }
        if (order.getTotalAmount() == null || total.compareTo(order.getTotalAmount()) != 0) {
            throw new BizException("INVALID_AMOUNT", "采购单总额与明细合计不符");
        }
        // Every receipt acquires product and balance locks in the same product order.
        for (var item : items) {
            var product = requireProductForPosting(tenantId, item.getProductId());
            postMovement(product, warehouse, "PURCHASE_IN", item.getQuantity(), item.getId(), "采购单 " + order.getOrderNo());
        }
        order.setStatus("IN_STORAGE");
        order.setUpdatedBy(getUserId());
        purchaseOrderMapper.updateById(order);
    }

    private ErpProductEntity requireProductForPosting(Long tenantId, Long productId) {
        var product = productMapper.lockForUpdate(tenantId, productId);
        if (product == null) throw new BizException("INVALID_PRODUCT", "物料不存在或已停用");
        return product;
    }

    @Transactional
    public void adjustStock(ErpStockAdjustmentRequest input) {
        Long tenantId = getTenantId();
        var warehouse = requireWarehouse(tenantId, input.warehouseId());
        var product = requireProductForPosting(tenantId, input.productId());
        String type = switch (input.type()) {
            case "IN" -> "MANUAL_IN";
            case "OUT" -> "MANUAL_OUT";
            case "AUDIT" -> "STOCK_CHECK";
            default -> throw new BizException("INVALID_TYPE", "不支持的库存调整方式");
        };
        if (input.delta() == 0 && !"STOCK_CHECK".equals(type)) {
            throw new BizException("INVALID_QUANTITY", "出入库数量必须为正整数");
        }
        postMovement(product, warehouse, type, input.delta(), null, input.remark());
    }

    private void postMovement(ErpProductEntity product, ErpWarehouseEntity warehouse, String type,
                              int quantity, Long purchaseItemId, String remark) {
        inventoryMapper.ensureBalance(product.getTenantId(), product.getId(), warehouse.getId());
        var balance = inventoryMapper.lockForUpdate(product.getTenantId(), product.getId(), warehouse.getId());
        int before = balance.getStockQuantity();
        int locked = balance.getLockedQuantity();
        long target = switch (type) {
            case "STOCK_CHECK" -> quantity;
            case "MANUAL_OUT" -> (long) before - quantity;
            default -> (long) before + quantity;
        };
        if (target < locked) throw new BizException("INSUFFICIENT_STOCK", "调整后库存不能低于锁定数量");
        if (target > Integer.MAX_VALUE) throw new BizException("INVALID_QUANTITY", "调整后库存超过数量上限");
        int after = (int) target;
        balance.setStockQuantity(after);
        balance.setUpdatedAt(LocalDateTime.now());
        inventoryMapper.updateById(balance);
        var record = new ErpInventoryRecordEntity();
        record.setTenantId(product.getTenantId());
        record.setRecordNo("REC-" + UUID.randomUUID());
        record.setPurchaseItemId(purchaseItemId);
        record.setRecordType(type);
        record.setProductId(product.getId());
        record.setProductName(product.getProductName());
        record.setWarehouseId(warehouse.getId());
        record.setWarehouseName(warehouse.getWarehouseName());
        record.setBeforeQuantity(before);
        record.setAfterQuantity(after);
        record.setChangeQuantity(after - before);
        record.setOperatorName(AuthContext.requirePrincipal().username());
        record.setRemark(remark);
        record.setCreatedBy(getUserId());
        record.setDeptId(resolveCurrentDeptId());
        recordMapper.insert(record);
    }

    private Long getTenantId() {
        return AuthContext.requirePrincipal().tenantId();
    }

    private Long getUserId() {
        return AuthContext.requirePrincipal().userId();
    }

    private Long resolveCurrentDeptId() {
        CurrentDepartmentResolver resolver = departmentResolverProvider.getIfAvailable();
        return resolver == null ? null : resolver.resolveDepartmentId(getTenantId(), getUserId());
    }
}
