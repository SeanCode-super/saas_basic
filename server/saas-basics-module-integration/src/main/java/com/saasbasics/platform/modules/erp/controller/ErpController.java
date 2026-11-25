package com.saasbasics.platform.modules.erp.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequireTenantFeature;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.erp.dto.ErpInventoryRecordResponse;
import com.saasbasics.platform.modules.erp.dto.ErpMasterDataOption;
import com.saasbasics.platform.modules.erp.dto.ErpMasterDataRequest;
import com.saasbasics.platform.modules.erp.dto.ErpStockAdjustmentRequest;
import com.saasbasics.platform.modules.erp.dto.ErpInventoryResponse;
import com.saasbasics.platform.modules.erp.dto.ErpProductResponse;
import com.saasbasics.platform.modules.erp.dto.ErpProductSaveRequest;
import com.saasbasics.platform.modules.erp.dto.ErpPurchaseOrderResponse;
import com.saasbasics.platform.modules.erp.dto.ErpPurchaseOrderSaveRequest;
import com.saasbasics.platform.modules.erp.service.ErpService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/erp")
@RequireTenantFeature("erp")
public class ErpController {

    private final ErpService erpService;

    public ErpController(ErpService erpService) {
        this.erpService = erpService;
    }

    @GetMapping("/products")
    public ApiResponse<List<ErpProductResponse>> listProducts() {
        return ApiResponse.success(erpService.listProducts());
    }

    @PostMapping("/products")
    public ApiResponse<ErpProductResponse> saveProduct(@Valid @RequestBody ErpProductSaveRequest request) {
        return ApiResponse.success(erpService.saveProduct(request));
    }

    @DeleteMapping("/products/{id}")
    public ApiResponse<Void> deleteProduct(@PathVariable Long id) {
        erpService.deleteProduct(id);
        return ApiResponse.success(null);
    }

    @GetMapping("/inventory")
    public ApiResponse<List<ErpInventoryResponse>> listInventory() {
        return ApiResponse.success(erpService.listInventory());
    }

    @GetMapping("/inventory/records")
    public ApiResponse<List<ErpInventoryRecordResponse>> listInventoryRecords(
            @RequestParam(required = false) Long productId, @RequestParam(required = false) Long warehouseId) {
        return ApiResponse.success(erpService.listInventoryRecords(productId, warehouseId));
    }

    @GetMapping("/purchase-orders")
    public ApiResponse<List<ErpPurchaseOrderResponse>> listPurchaseOrders() {
        return ApiResponse.success(erpService.listPurchaseOrders());
    }

    @PostMapping("/purchase-orders")
    public ApiResponse<ErpPurchaseOrderResponse> createPurchaseOrder(@Valid @RequestBody ErpPurchaseOrderSaveRequest request) {
        return ApiResponse.success(erpService.createPurchaseOrder(request));
    }

    @PostMapping("/purchase-orders/{id}/stock-in")
    public ApiResponse<Void> stockInPurchaseOrder(@PathVariable Long id, @RequestParam(required = false) Long warehouseId) {
        erpService.stockInPurchaseOrder(id, warehouseId);
        return ApiResponse.success(null);
    }

    @PostMapping("/inventory/adjust")
    public ApiResponse<Void> adjustInventory(@Valid @RequestBody ErpStockAdjustmentRequest request) {
        erpService.adjustStock(request);
        return ApiResponse.success(null);
    }

    @PutMapping("/purchase-orders/{id}/confirmation")
    @RequirePermission("erp:purchase:confirm")
    public ApiResponse<ErpPurchaseOrderResponse> confirmLegacyOrder(@PathVariable Long id,
            @Valid @RequestBody ErpPurchaseOrderSaveRequest request) {
        return ApiResponse.success(erpService.confirmLegacyOrder(id, request));
    }

    @GetMapping("/suppliers")
    public ApiResponse<List<ErpMasterDataOption>> listSuppliers() {
        return ApiResponse.success(erpService.listSuppliers());
    }

    @PostMapping("/suppliers")
    @RequirePermission("erp:supplier:create")
    public ApiResponse<ErpMasterDataOption> createSupplier(@Valid @RequestBody ErpMasterDataRequest request) {
        return ApiResponse.success(erpService.createSupplier(request));
    }

    @GetMapping("/warehouses")
    public ApiResponse<List<ErpMasterDataOption>> listWarehouses() {
        return ApiResponse.success(erpService.listWarehouses());
    }

    @PostMapping("/warehouses")
    @RequirePermission("erp:warehouse:create")
    public ApiResponse<ErpMasterDataOption> createWarehouse(@Valid @RequestBody ErpMasterDataRequest request) {
        return ApiResponse.success(erpService.createWarehouse(request));
    }
}
