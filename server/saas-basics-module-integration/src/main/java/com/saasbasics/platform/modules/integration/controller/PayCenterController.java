package com.saasbasics.platform.modules.integration.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.modules.integration.dto.PayOrderCreateRequest;
import com.saasbasics.platform.modules.integration.dto.PayOrderResponse;
import com.saasbasics.platform.modules.integration.service.PayCenterService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pay")
public class PayCenterController {

    private final PayCenterService payCenterService;

    public PayCenterController(PayCenterService payCenterService) {
        this.payCenterService = payCenterService;
    }

    @PostMapping({"/create-order", "/orders"})
    public ApiResponse<PayOrderResponse> createOrder(@Valid @RequestBody PayOrderCreateRequest request) {
        AuthPrincipal principal = AuthContext.get();
        Long tenantId = principal != null ? principal.tenantId() : 1L;
        PayOrderResponse response = payCenterService.createPayOrder(tenantId, request);
        return ApiResponse.success(response);
    }

    @GetMapping("/orders")
    public ApiResponse<java.util.List<PayOrderResponse>> listOrders() {
        AuthPrincipal principal = AuthContext.get();
        Long tenantId = principal != null ? principal.tenantId() : 1L;
        return ApiResponse.success(payCenterService.listRecentOrders(tenantId));
    }

    @GetMapping("/order-status/{orderNo}")
    public ApiResponse<PayOrderResponse> getOrderStatus(@PathVariable String orderNo) {
        return ApiResponse.success(payCenterService.getOrderStatus(orderNo));
    }

    @PostMapping("/mock-success/{orderNo}")
    public ApiResponse<PayOrderResponse> mockSuccess(@PathVariable String orderNo) {
        PayOrderResponse response = payCenterService.mockPaySuccess(orderNo);
        if (response == null) {
            return ApiResponse.failure("ORDER_NOT_FOUND", "订单未找到");
        }
        return ApiResponse.success(response);
    }

    @PostMapping("/refund/{orderNo}")
    public ApiResponse<PayOrderResponse> refund(@PathVariable String orderNo) {
        PayOrderResponse response = payCenterService.refundOrder(orderNo, null, "用户申请正常退款");
        return ApiResponse.success(response);
    }
}
