package com.saasbasics.platform.modules.erp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.erp.dto.ErpInventoryResponse;
import com.saasbasics.platform.modules.erp.dto.ErpProductResponse;
import com.saasbasics.platform.modules.erp.dto.ErpProductSaveRequest;
import com.saasbasics.platform.modules.erp.dto.ErpPurchaseOrderResponse;
import com.saasbasics.platform.modules.erp.dto.ErpPurchaseOrderSaveRequest;
import com.saasbasics.platform.modules.erp.entity.ErpInventoryEntity;
import com.saasbasics.platform.modules.erp.entity.ErpInventoryRecordEntity;
import com.saasbasics.platform.modules.erp.entity.ErpProductEntity;
import com.saasbasics.platform.modules.erp.entity.ErpPurchaseOrderEntity;
import com.saasbasics.platform.modules.erp.mapper.ErpInventoryMapper;
import com.saasbasics.platform.modules.erp.mapper.ErpInventoryRecordMapper;
import com.saasbasics.platform.modules.erp.mapper.ErpProductMapper;
import com.saasbasics.platform.modules.erp.mapper.ErpPurchaseOrderMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ErpService {

    private final ErpProductMapper productMapper;
    private final ErpInventoryMapper inventoryMapper;
    private final ErpInventoryRecordMapper recordMapper;
    private final ErpPurchaseOrderMapper purchaseOrderMapper;
    private final JdbcTemplate jdbcTemplate;

    public ErpService(
            ErpProductMapper productMapper,
            ErpInventoryMapper inventoryMapper,
            ErpInventoryRecordMapper recordMapper,
            ErpPurchaseOrderMapper purchaseOrderMapper,
            JdbcTemplate jdbcTemplate
    ) {
        this.productMapper = productMapper;
        this.inventoryMapper = inventoryMapper;
        this.recordMapper = recordMapper;
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.jdbcTemplate = jdbcTemplate;
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
        entity.setCreatedBy(userId);
        entity.setUpdatedBy(userId);
        entity.setDeleted(0);
        productMapper.insert(entity);

        // 默认初始化在华东总仓的库存为 0
        ErpInventoryEntity inv = new ErpInventoryEntity();
        inv.setTenantId(tenantId);
        inv.setProductId(entity.getId());
        inv.setWarehouseId(1L);
        inv.setStockQuantity(0);
        inv.setLockedQuantity(0);
        inventoryMapper.insert(inv);

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

        List<ErpInventoryResponse> result = new ArrayList<>();
        for (ErpInventoryEntity inv : invList) {
            ErpProductEntity prod = prodMap.get(inv.getProductId());
            if (prod == null) continue;

            int stock = inv.getStockQuantity() != null ? inv.getStockQuantity() : 0;
            int locked = inv.getLockedQuantity() != null ? inv.getLockedQuantity() : 0;
            int avail = stock - locked;
            int min = prod.getMinStock() != null ? prod.getMinStock() : 10;
            boolean low = stock <= min;
            BigDecimal val = prod.getPurchasePrice().multiply(BigDecimal.valueOf(stock));

            result.add(new ErpInventoryResponse(
                    inv.getId(),
                    prod.getId(),
                    prod.getProductCode(),
                    prod.getProductName(),
                    prod.getSpecification(),
                    prod.getUnit(),
                    inv.getWarehouseId(),
                    inv.getWarehouseId() == 1L ? "华东一号中心立体总仓" : "华南保税智造配送仓",
                    stock,
                    locked,
                    avail,
                    min,
                    low,
                    val,
                    inv.getUpdatedAt() != null ? inv.getUpdatedAt() : LocalDateTime.now()
            ));
        }
        return result;
    }

    public List<com.saasbasics.platform.modules.erp.dto.ErpInventoryRecordResponse> listInventoryRecords() {
        Long tenantId = getTenantId();
        List<ErpInventoryRecordEntity> list = recordMapper.selectList(new LambdaQueryWrapper<ErpInventoryRecordEntity>()
                .eq(ErpInventoryRecordEntity::getTenantId, tenantId)
                .orderByDesc(ErpInventoryRecordEntity::getId)
                .last("LIMIT 50"));
        return list.stream().map(r -> new com.saasbasics.platform.modules.erp.dto.ErpInventoryRecordResponse(
                r.getId(),
                r.getTenantId(),
                r.getRecordNo(),
                r.getRecordType(),
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

    public List<ErpPurchaseOrderResponse> listPurchaseOrders() {
        Long tenantId = getTenantId();
        List<ErpPurchaseOrderEntity> list = purchaseOrderMapper.selectList(new LambdaQueryWrapper<ErpPurchaseOrderEntity>()
                .eq(ErpPurchaseOrderEntity::getTenantId, tenantId)
                .eq(ErpPurchaseOrderEntity::getDeleted, 0)
                .orderByDesc(ErpPurchaseOrderEntity::getId));

        return list.stream().map(o -> new ErpPurchaseOrderResponse(
                o.getId(),
                o.getOrderNo(),
                o.getSupplierId(),
                o.getSupplierName(),
                o.getTotalAmount(),
                o.getStatus(),
                o.getRemark(),
                o.getCreatedAt()
        )).toList();
    }

    @Transactional
    public ErpPurchaseOrderResponse createPurchaseOrder(ErpPurchaseOrderSaveRequest req) {
        Long tenantId = getTenantId();
        Long userId = getUserId();

        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int rand = ThreadLocalRandom.current().nextInt(1000, 9999);
        String orderNo = "PO" + stamp + rand;

        ErpPurchaseOrderEntity order = new ErpPurchaseOrderEntity();
        order.setTenantId(tenantId);
        order.setOrderNo(orderNo);
        order.setSupplierId(req.supplierId());
        order.setSupplierName(req.supplierName() != null ? req.supplierName() : "华为数字能源技术有限公司");
        order.setTotalAmount(req.totalAmount() != null ? req.totalAmount() : new BigDecimal("48500.00"));
        order.setStatus("PENDING");
        order.setRemark(req.remark() != null ? req.remark() : "例行采购订单");
        order.setCreatedBy(userId);
        order.setUpdatedBy(userId);
        order.setDeleted(0);
        purchaseOrderMapper.insert(order);

        return new ErpPurchaseOrderResponse(
                order.getId(),
                order.getOrderNo(),
                order.getSupplierId(),
                order.getSupplierName(),
                order.getTotalAmount(),
                order.getStatus(),
                order.getRemark(),
                LocalDateTime.now()
        );
    }

    @Transactional
    public void stockInPurchaseOrder(Long orderId, Long warehouseId) {
        Long tenantId = getTenantId();
        String userName = getUserName();

        ErpPurchaseOrderEntity order = purchaseOrderMapper.selectById(orderId);
        if (order == null || !order.getTenantId().equals(tenantId) || order.getDeleted() != 0) {
            throw new BizException("NOT_FOUND", "采购订单不存在");
        }
        if ("IN_STORAGE".equals(order.getStatus())) {
            throw new BizException("ALREADY_IN", "该采购订单已完成入库，请勿重复操作");
        }

        order.setStatus("IN_STORAGE");
        purchaseOrderMapper.updateById(order);

        // 默认将物料 1 (UPS) 增加 10 件入库
        Long prodId = 1L;
        Long whId = warehouseId != null ? warehouseId : 1L;
        ErpInventoryEntity inv = inventoryMapper.selectOne(new LambdaQueryWrapper<ErpInventoryEntity>()
                .eq(ErpInventoryEntity::getTenantId, tenantId)
                .eq(ErpInventoryEntity::getProductId, prodId)
                .eq(ErpInventoryEntity::getWarehouseId, whId));

        int before = inv != null && inv.getStockQuantity() != null ? inv.getStockQuantity() : 0;
        int qtyChange = 10;
        int after = before + qtyChange;

        if (inv != null) {
            inv.setStockQuantity(after);
            inv.setUpdatedAt(LocalDateTime.now());
            inventoryMapper.updateById(inv);
        }

        // 记录出入库台账流水
        ErpInventoryRecordEntity record = new ErpInventoryRecordEntity();
        record.setTenantId(tenantId);
        record.setRecordNo("REC" + System.currentTimeMillis());
        record.setRecordType("PURCHASE_IN");
        record.setProductId(prodId);
        record.setProductName("高频工业级 UPS 50kVA 主机");
        record.setWarehouseId(whId);
        record.setWarehouseName(whId == 1L ? "华东一号中心立体总仓" : "华南保税智造配送仓");
        record.setChangeQuantity(qtyChange);
        record.setBeforeQuantity(before);
        record.setAfterQuantity(after);
        record.setOperatorName(userName);
        record.setRemark("采购单 " + order.getOrderNo() + " 核销入库");
        record.setCreatedAt(LocalDateTime.now());
        recordMapper.insert(record);
    }

    private Long getTenantId() {
        AuthPrincipal p = AuthContext.get();
        return p != null && p.tenantId() != null ? p.tenantId() : 1L;
    }

    private Long getUserId() {
        AuthPrincipal p = AuthContext.get();
        return p != null && p.userId() != null ? p.userId() : 1L;
    }

    private String getUserName() {
        AuthPrincipal p = AuthContext.get();
        return p != null && p.username() != null ? p.username() : "平台管理员";
    }
}
