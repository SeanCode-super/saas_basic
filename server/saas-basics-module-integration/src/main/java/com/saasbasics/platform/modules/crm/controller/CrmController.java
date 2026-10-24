package com.saasbasics.platform.modules.crm.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequireTenantFeature;
import com.saasbasics.platform.modules.crm.dto.*;
import com.saasbasics.platform.modules.crm.service.CrmService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/crm")
@RequireTenantFeature("crm")
public class CrmController {

    private final CrmService crmService;

    public CrmController(CrmService crmService) {
        this.crmService = crmService;
    }

    // 1. 核心看板与数据统计
    @GetMapping("/statistics")
    public ApiResponse<CrmStatisticsResponse> getStatistics() {
        return ApiResponse.success(crmService.getStatistics());
    }

    // 2. 销售线索
    @GetMapping("/clues")
    public ApiResponse<List<CrmClueResponse>> listClues(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(crmService.listClues(status, keyword));
    }

    @PostMapping("/clues")
    public ApiResponse<CrmClueResponse> createClue(@RequestBody CrmClueSaveRequest req) {
        return ApiResponse.success(crmService.createClue(req));
    }

    @PostMapping("/clues/{id}/convert")
    public ApiResponse<CrmCustomerResponse> convertClueToCustomer(@PathVariable Long id) {
        return ApiResponse.success(crmService.convertClueToCustomer(id));
    }

    // 3. 客户档案与公海池
    @GetMapping("/customers")
    public ApiResponse<List<CrmCustomerResponse>> listCustomers(
            @RequestParam(required = false) Integer isPool,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String level
    ) {
        return ApiResponse.success(crmService.listCustomers(isPool, keyword, level));
    }

    @PostMapping("/customers")
    public ApiResponse<CrmCustomerResponse> createCustomer(@RequestBody CrmCustomerSaveRequest req) {
        return ApiResponse.success(crmService.createCustomer(req));
    }

    @PostMapping("/customers/{id}/claim")
    public ApiResponse<Boolean> claimCustomer(@PathVariable Long id) {
        crmService.claimCustomer(id);
        return ApiResponse.success(true);
    }

    @PostMapping("/customers/{id}/return-to-pool")
    public ApiResponse<Boolean> returnCustomerToPool(@PathVariable Long id) {
        crmService.returnCustomerToPool(id);
        return ApiResponse.success(true);
    }

    @PostMapping("/customers/{id}/toggle-lock")
    public ApiResponse<Boolean> toggleCustomerLock(@PathVariable Long id) {
        crmService.toggleCustomerLock(id);
        return ApiResponse.success(true);
    }

    // 4. 销售商机漏斗
    @GetMapping("/businesses")
    public ApiResponse<List<CrmBusinessResponse>> listBusinesses(
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(crmService.listBusinesses(stage, keyword));
    }

    @PostMapping("/businesses")
    public ApiResponse<CrmBusinessResponse> createBusiness(@RequestBody CrmBusinessSaveRequest req) {
        return ApiResponse.success(crmService.createBusiness(req));
    }

    @PutMapping("/businesses/{id}/stage")
    public ApiResponse<Boolean> updateBusinessStage(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body
    ) {
        String stage = (String) body.get("stage");
        Integer winRate = body.containsKey("winRate") && body.get("winRate") != null
                ? Integer.parseInt(body.get("winRate").toString())
                : null;
        String lossReason = (String) body.get("lossReason");
        crmService.updateBusinessStage(id, stage, winRate, lossReason);
        return ApiResponse.success(true);
    }

    // 5. 商务合同
    @GetMapping("/contracts")
    public ApiResponse<List<CrmContractResponse>> listContracts(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(crmService.listContracts(status, keyword));
    }

    @PostMapping("/contracts")
    public ApiResponse<CrmContractResponse> createContract(@RequestBody CrmContractSaveRequest req) {
        return ApiResponse.success(crmService.createContract(req));
    }

    @PostMapping("/contracts/{id}/approve")
    public ApiResponse<Boolean> approveContract(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body
    ) {
        boolean approve = body.getOrDefault("approve", true);
        crmService.approveContract(id, approve);
        return ApiResponse.success(true);
    }

    // 6. 财务回款
    @GetMapping("/receivables")
    public ApiResponse<List<CrmReceivableResponse>> listReceivables(
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.success(crmService.listReceivables(status));
    }

    @PostMapping("/receivables")
    public ApiResponse<CrmReceivableResponse> createReceivable(@RequestBody CrmReceivableSaveRequest req) {
        return ApiResponse.success(crmService.createReceivable(req));
    }

    @PostMapping("/receivables/{id}/approve")
    public ApiResponse<Boolean> approveReceivable(@PathVariable Long id) {
        crmService.approveReceivable(id);
        return ApiResponse.success(true);
    }

    // 7. 全链路跟进记录
    @GetMapping("/follow-up-records")
    public ApiResponse<List<CrmFollowUpRecordResponse>> listFollowUpRecords(
            @RequestParam String bizType,
            @RequestParam Long bizId
    ) {
        return ApiResponse.success(crmService.listFollowUpRecords(bizType, bizId));
    }

    @PostMapping("/follow-up-records")
    public ApiResponse<CrmFollowUpRecordResponse> addFollowUpRecord(@RequestBody CrmFollowUpRecordSaveRequest req) {
        return ApiResponse.success(crmService.addFollowUpRecord(req));
    }
}
