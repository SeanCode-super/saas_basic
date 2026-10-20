package com.saasbasics.platform.modules.codegen.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.codegen.dto.*;
import com.saasbasics.platform.modules.codegen.dto.CodegenTableDefinitionRequest.CodegenColumnDefinition;
import com.saasbasics.platform.modules.codegen.service.CodegenService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/codegen", "/api/codegen/projects"})
public class CodegenController {

    private final CodegenService codegenService;

    public CodegenController(CodegenService codegenService) {
        this.codegenService = codegenService;
    }

    @GetMapping("/db-tables")
    @RequirePermission("codegen:project:query")
    public ApiResponse<List<DbTableResponse>> listDbTables() {
        return ApiResponse.success(codegenService.listDbTables());
    }

    @GetMapping("/db-tables/{tableName}/columns")
    @RequirePermission("codegen:project:query")
    public ApiResponse<List<CodegenColumnDefinition>> getDbTableColumns(@PathVariable String tableName) {
        return ApiResponse.success(codegenService.getDbTableColumns(tableName));
    }

    @PostMapping("/preview")
    @RequirePermission("codegen:project:query")
    public ApiResponse<List<CodegenPreviewFileResponse>> preview(@Valid @RequestBody CodegenTableDefinitionRequest request) {
        return ApiResponse.success(codegenService.previewCode(request));
    }

    @PostMapping("/download-zip")
    @RequirePermission("codegen:project:query")
    public ResponseEntity<byte[]> downloadZip(@Valid @RequestBody CodegenTableDefinitionRequest request) {
        byte[] zipBytes = codegenService.downloadZip(request);
        String filename = (request.moduleName() != null && !request.moduleName().isBlank()
                ? request.moduleName() : "codegen") + "-bundle.zip";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(zipBytes);
    }

    @PostMapping("/write-to-disk")
    @RequirePermission("codegen:project:write")
    public ApiResponse<java.util.Map<String, Object>> writeToDisk(@Valid @RequestBody CodegenTableDefinitionRequest request) {
        return ApiResponse.success(codegenService.writeToDisk(request));
    }

    @GetMapping({"", "/projects"})
    @RequirePermission("codegen:project:query")
    public ApiResponse<List<CodegenProjectResponse>> list() {
        return ApiResponse.success(codegenService.listProjects());
    }

    @GetMapping({"/{id}", "/projects/{id}"})
    @RequirePermission("codegen:project:query")
    public ApiResponse<CodegenProjectResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(codegenService.getProject(id));
    }

    @PostMapping({"", "/projects"})
    @RequirePermission("codegen:project:write")
    public ApiResponse<CodegenProjectResponse> create(@Valid @RequestBody CodegenProjectSaveRequest request) {
        return ApiResponse.success(codegenService.createProject(request));
    }

    @PutMapping({"/{id}", "/projects/{id}"})
    @RequirePermission("codegen:project:write")
    public ApiResponse<CodegenProjectResponse> update(@PathVariable Long id,
                                                      @Valid @RequestBody CodegenProjectSaveRequest request) {
        return ApiResponse.success(codegenService.updateProject(id, request));
    }
}
