package com.saasbasics.platform.modules.erp.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.modules.erp.dto.ErpInventoryResponse;
import com.saasbasics.platform.modules.erp.dto.ErpProductResponse;
import com.saasbasics.platform.modules.erp.dto.ErpProductSaveRequest;
import com.saasbasics.platform.modules.erp.dto.ErpPurchaseOrderResponse;
import com.saasbasics.platform.modules.erp.dto.ErpPurchaseOrderSaveRequest;
import com.saasbasics.platform.modules.erp.entity.ErpInventoryRecordEntity;
import com.saasbasics.platform.modules.erp.service.ErpService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/erp")
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
    public ApiResponse<List<ErpInventoryRecordEntity>> listInventoryRecords() {
        return ApiResponse.success(erpService.listInventoryRecords());
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
}
