package com.saasbasics.platform.modules.file.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.common.auth.RequireTenantFeature;
import com.saasbasics.platform.common.file.PlatformFileStorageGateway;
import com.saasbasics.platform.modules.file.dto.FileAccessLogCreateRequest;
import com.saasbasics.platform.modules.file.dto.FileAccessLogResponse;
import com.saasbasics.platform.modules.file.dto.FileCapabilityResponse;
import com.saasbasics.platform.modules.file.dto.FileLifecyclePolicyResponse;
import com.saasbasics.platform.modules.file.dto.FileLifecyclePolicySaveRequest;
import com.saasbasics.platform.modules.file.dto.FileObjectResponse;
import com.saasbasics.platform.modules.file.dto.FileStorageResponse;
import com.saasbasics.platform.modules.file.dto.FileStorageSaveRequest;
import com.saasbasics.platform.modules.file.dto.FileStorageTestResponse;
import com.saasbasics.platform.modules.file.dto.FileUploadRouteResponse;
import com.saasbasics.platform.modules.file.dto.FileUploadRouteSaveRequest;
import com.saasbasics.platform.modules.file.dto.FileUploadSessionCompleteRequest;
import com.saasbasics.platform.modules.file.dto.FileUploadSessionCreateRequest;
import com.saasbasics.platform.modules.file.dto.FileUploadSessionResponse;
import com.saasbasics.platform.modules.file.service.FileAccessLogService;
import com.saasbasics.platform.modules.file.service.FileLifecyclePolicyService;
import com.saasbasics.platform.modules.file.service.FileObjectService;
import com.saasbasics.platform.modules.file.service.FileService;
import com.saasbasics.platform.modules.file.service.FileStorageService;
import com.saasbasics.platform.modules.file.service.FileUploadRouteService;
import com.saasbasics.platform.modules.file.service.FileUploadSessionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/files")
@RequireTenantFeature("file")
public class FileController {

    private final FileService fileService;
    private final FileStorageService fileStorageService;
    private final FileUploadRouteService fileUploadRouteService;
    private final PlatformFileStorageGateway fileStorageGateway;
    private final FileObjectService fileObjectService;
    private final FileUploadSessionService fileUploadSessionService;
    private final FileLifecyclePolicyService fileLifecyclePolicyService;
    private final FileAccessLogService fileAccessLogService;

    public FileController(FileService fileService,
                          FileStorageService fileStorageService,
                          FileUploadRouteService fileUploadRouteService,
                          PlatformFileStorageGateway fileStorageGateway,
                          FileObjectService fileObjectService,
                          FileUploadSessionService fileUploadSessionService,
                          FileLifecyclePolicyService fileLifecyclePolicyService,
                          FileAccessLogService fileAccessLogService) {
        this.fileService = fileService;
        this.fileStorageService = fileStorageService;
        this.fileUploadRouteService = fileUploadRouteService;
        this.fileStorageGateway = fileStorageGateway;
        this.fileObjectService = fileObjectService;
        this.fileUploadSessionService = fileUploadSessionService;
        this.fileLifecyclePolicyService = fileLifecyclePolicyService;
        this.fileAccessLogService = fileAccessLogService;
    }

    @GetMapping("/capability")
    @RequirePermission("file:object:query")
    public ApiResponse<FileCapabilityResponse> capability() {
        return ApiResponse.success(fileService.capability());
    }

    @GetMapping("/storage-status")
    public ApiResponse<PlatformFileStorageGateway.StorageAvailability> storageStatus(
            @RequestParam(required = false, defaultValue = "COMMON") String bizType) {
        AuthPrincipal principal = AuthContext.get();
        Long tenantId = principal != null ? principal.tenantId() : 1L;
        return ApiResponse.success(fileStorageGateway.checkAvailability(tenantId, bizType));
    }

    @GetMapping("/storages")
    @RequirePermission("file:object:query")
    public ApiResponse<List<FileStorageResponse>> storages() {
        return ApiResponse.success(fileStorageService.listStorages());
    }

    @GetMapping("/storages/{id}")
    @RequirePermission("file:object:query")
    public ApiResponse<FileStorageResponse> storage(@PathVariable Long id) {
        return ApiResponse.success(fileStorageService.getStorage(id));
    }

    @PostMapping("/storages")
    @RequirePermission("file:storage:write")
    public ApiResponse<FileStorageResponse> createStorage(@Valid @RequestBody FileStorageSaveRequest request) {
        return ApiResponse.success(fileStorageService.createStorage(request));
    }

    @PutMapping("/storages/{id}")
    @RequirePermission("file:storage:write")
    public ApiResponse<FileStorageResponse> updateStorage(@PathVariable Long id,
                                                          @Valid @RequestBody FileStorageSaveRequest request) {
        return ApiResponse.success(fileStorageService.updateStorage(id, request));
    }

    @PostMapping("/storages/{id}/set-default")
    @RequirePermission("file:storage:write")
    public ApiResponse<FileStorageResponse> setDefaultStorage(@PathVariable Long id) {
        return ApiResponse.success(fileStorageService.setDefaultStorage(id));
    }

    @PostMapping("/storages/{id}/test")
    @RequirePermission("file:object:query")
    public ApiResponse<FileStorageTestResponse> testStorage(@PathVariable Long id) {
        return ApiResponse.success(fileStorageService.testStorage(id));
    }

    @DeleteMapping("/storages/{id}")
    @RequirePermission("file:storage:write")
    public ApiResponse<Void> deleteStorage(@PathVariable Long id) {
        fileStorageService.deleteStorage(id);
        return ApiResponse.success(null);
    }

    @GetMapping("/upload-routes")
    @RequirePermission("file:object:query")
    public ApiResponse<List<FileUploadRouteResponse>> uploadRoutes() {
        return ApiResponse.success(fileUploadRouteService.listRoutes());
    }

    @PostMapping("/upload-routes")
    @RequirePermission("file:storage:write")
    public ApiResponse<FileUploadRouteResponse> createUploadRoute(@Valid @RequestBody FileUploadRouteSaveRequest request) {
        return ApiResponse.success(fileUploadRouteService.createRoute(request));
    }

    @PutMapping("/upload-routes/{id}")
    @RequirePermission("file:storage:write")
    public ApiResponse<FileUploadRouteResponse> updateUploadRoute(@PathVariable Long id,
                                                                  @Valid @RequestBody FileUploadRouteSaveRequest request) {
        return ApiResponse.success(fileUploadRouteService.updateRoute(id, request));
    }

    @DeleteMapping("/upload-routes/{id}")
    @RequirePermission("file:storage:write")
    public ApiResponse<Void> deleteUploadRoute(@PathVariable Long id) {
        fileUploadRouteService.deleteRoute(id);
        return ApiResponse.success(null);
    }

    @GetMapping("/objects")
    @RequirePermission("file:object:query")
    public ApiResponse<List<FileObjectResponse>> objects() {
        return ApiResponse.success(fileObjectService.listObjects());
    }

    @GetMapping("/lifecycle-policies")
    @RequirePermission("file:object:query")
    public ApiResponse<List<FileLifecyclePolicyResponse>> lifecyclePolicies() {
        return ApiResponse.success(fileLifecyclePolicyService.listPolicies());
    }

    @PostMapping("/lifecycle-policies")
    @RequirePermission("file:storage:write")
    public ApiResponse<FileLifecyclePolicyResponse> createLifecyclePolicy(@Valid @RequestBody FileLifecyclePolicySaveRequest request) {
        return ApiResponse.success(fileLifecyclePolicyService.createPolicy(request));
    }

    @PutMapping("/lifecycle-policies/{id}")
    @RequirePermission("file:storage:write")
    public ApiResponse<FileLifecyclePolicyResponse> updateLifecyclePolicy(@PathVariable Long id,
                                                                         @Valid @RequestBody FileLifecyclePolicySaveRequest request) {
        return ApiResponse.success(fileLifecyclePolicyService.updatePolicy(id, request));
    }

    @GetMapping("/access-logs")
    @RequirePermission("file:object:query")
    public ApiResponse<List<FileAccessLogResponse>> accessLogs() {
        return ApiResponse.success(fileAccessLogService.listLogs());
    }

    @PostMapping("/access-logs")
    @RequirePermission("file:storage:write")
    public ApiResponse<FileAccessLogResponse> createAccessLog(@Valid @RequestBody FileAccessLogCreateRequest request) {
        return ApiResponse.success(fileAccessLogService.createLog(request));
    }

    @GetMapping("/upload-sessions")
    @RequirePermission("file:object:query")
    public ApiResponse<List<FileUploadSessionResponse>> uploadSessions() {
        return ApiResponse.success(fileUploadSessionService.listSessions());
    }

    @PostMapping("/upload-sessions")
    @RequirePermission("file:storage:write")
    public ApiResponse<FileUploadSessionResponse> createUploadSession(@Valid @RequestBody FileUploadSessionCreateRequest request) {
        return ApiResponse.success(fileUploadSessionService.createSession(request));
    }

    @PostMapping("/upload-sessions/{id}/complete")
    @RequirePermission("file:storage:write")
    public ApiResponse<FileObjectResponse> completeUploadSession(@PathVariable Long id,
                                                                 @Valid @RequestBody FileUploadSessionCompleteRequest request) {
        return ApiResponse.success(fileUploadSessionService.completeSession(id, request));
    }
}
