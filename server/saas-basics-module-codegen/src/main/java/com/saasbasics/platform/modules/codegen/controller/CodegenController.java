package com.saasbasics.platform.modules.codegen.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.codegen.dto.CodegenProjectResponse;
import com.saasbasics.platform.modules.codegen.dto.CodegenProjectSaveRequest;
import com.saasbasics.platform.modules.codegen.service.CodegenService;
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
@RequestMapping("/api/codegen/projects")
public class CodegenController {

    private final CodegenService codegenService;

    public CodegenController(CodegenService codegenService) {
        this.codegenService = codegenService;
    }

    @GetMapping
    @RequirePermission("codegen:project:query")
    public ApiResponse<List<CodegenProjectResponse>> list() {
        return ApiResponse.success(codegenService.listProjects());
    }

    @GetMapping("/{id}")
    @RequirePermission("codegen:project:query")
    public ApiResponse<CodegenProjectResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(codegenService.getProject(id));
    }

    @PostMapping
    @RequirePermission("codegen:project:write")
    public ApiResponse<CodegenProjectResponse> create(@Valid @RequestBody CodegenProjectSaveRequest request) {
        return ApiResponse.success(codegenService.createProject(request));
    }

    @PutMapping("/{id}")
    @RequirePermission("codegen:project:write")
    public ApiResponse<CodegenProjectResponse> update(@PathVariable Long id,
                                                      @Valid @RequestBody CodegenProjectSaveRequest request) {
        return ApiResponse.success(codegenService.updateProject(id, request));
    }
}
