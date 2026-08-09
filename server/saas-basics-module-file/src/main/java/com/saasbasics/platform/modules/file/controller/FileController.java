package com.saasbasics.platform.modules.file.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.file.dto.FileCapabilityResponse;
import com.saasbasics.platform.modules.file.dto.FileAccessLogCreateRequest;
import com.saasbasics.platform.modules.file.dto.FileAccessLogResponse;
import com.saasbasics.platform.modules.file.dto.FileLifecyclePolicyResponse;
import com.saasbasics.platform.modules.file.dto.FileLifecyclePolicySaveRequest;
import com.saasbasics.platform.modules.file.dto.FileObjectResponse;
import com.saasbasics.platform.modules.file.dto.FileStorageResponse;
import com.saasbasics.platform.modules.file.dto.FileStorageSaveRequest;
import com.saasbasics.platform.modules.file.dto.FileUploadSessionCreateRequest;
import com.saasbasics.platform.modules.file.dto.FileUploadSessionCompleteRequest;
import com.saasbasics.platform.modules.file.dto.FileUploadSessionResponse;
import com.saasbasics.platform.modules.file.service.FileAccessLogService;
import com.saasbasics.platform.modules.file.service.FileLifecyclePolicyService;
import com.saasbasics.platform.modules.file.service.FileObjectService;
import com.saasbasics.platform.modules.file.service.FileService;
import com.saasbasics.platform.modules.file.service.FileStorageService;
import com.saasbasics.platform.modules.file.service.FileUploadSessionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;
    private final FileStorageService fileStorageService;
    private final FileObjectService fileObjectService;
    private final FileUploadSessionService fileUploadSessionService;
    private final FileLifecyclePolicyService fileLifecyclePolicyService;
    private final FileAccessLogService fileAccessLogService;

    public FileController(FileService fileService,
                          FileStorageService fileStorageService,
                          FileObjectService fileObjectService,
                          FileUploadSessionService fileUploadSessionService,
                          FileLifecyclePolicyService fileLifecyclePolicyService,
                          FileAccessLogService fileAccessLogService) {
        this.fileService = fileService;
        this.fileStorageService = fileStorageService;
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
