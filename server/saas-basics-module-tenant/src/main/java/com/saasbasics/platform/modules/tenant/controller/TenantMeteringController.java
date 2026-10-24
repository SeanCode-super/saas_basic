package com.saasbasics.platform.modules.tenant.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.idempotent.Idempotent;
import com.saasbasics.platform.modules.tenant.dto.TenantBillResponse;
import com.saasbasics.platform.modules.tenant.dto.TenantMeteringSummaryResponse;
import com.saasbasics.platform.modules.tenant.dto.TenantSnapshotResponse;
import com.saasbasics.platform.modules.tenant.dto.TenantSnapshotRestoreResponse;
import com.saasbasics.platform.modules.tenant.service.TenantMeteringService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/tenant")
public class TenantMeteringController {

    private final TenantMeteringService meteringService;

    public TenantMeteringController(TenantMeteringService meteringService) {
        this.meteringService = meteringService;
    }

    @GetMapping("/metering/summary")
    @RequirePermission("tenant:metering:query")
    public ApiResponse<TenantMeteringSummaryResponse> getMeteringSummary() {
        return ApiResponse.success(meteringService.getSummary());
    }

    @GetMapping("/billing/bills")
    @RequirePermission("tenant:billing:query")
    public ApiResponse<List<TenantBillResponse>> listBills() {
        return ApiResponse.success(meteringService.listBills());
    }

    @PostMapping("/snapshot/export")
    @RequirePermission("tenant:snapshot:export")
    @Idempotent(message = "快照备份生成中，请勿连续请求", expireMillis = 5000)
    public ApiResponse<TenantSnapshotResponse> exportSnapshot(@RequestParam(required = false) Long tenantId) {
        return ApiResponse.success(meteringService.generateTenantSnapshot(tenantId));
    }

    @PostMapping(value = "/snapshot/restore", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("tenant:snapshot:restore")
    @Idempotent(message = "快照还原处理中，请勿重复提交", expireMillis = 10000)
    public ApiResponse<TenantSnapshotRestoreResponse> restoreSnapshot(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) Long tenantId) {
        return ApiResponse.success(meteringService.restoreTenantSnapshot(file, tenantId));
    }

    @GetMapping("/snapshot/download/{snapshotId}")
    public ResponseEntity<byte[]> downloadSnapshot(@PathVariable String snapshotId) {
        String mockContent = "{\"snapshotId\": \"" + snapshotId + "\", \"format\": \"SaaS-Basics-Tenant-Backup-v1.0\", \"status\": \"VERIFIED\"}";
        byte[] bytes = mockContent.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + snapshotId + ".json\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes);
    }
}
