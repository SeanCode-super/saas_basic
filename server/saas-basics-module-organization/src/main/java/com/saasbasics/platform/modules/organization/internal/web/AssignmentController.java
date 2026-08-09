package com.saasbasics.platform.modules.organization.internal.web;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.api.PageResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.organization.api.model.AssignmentModels;
import com.saasbasics.platform.modules.organization.api.model.OrganizationCommonModels.LifecycleTransitionRequest;
import com.saasbasics.platform.modules.organization.api.model.OrganizationCommonModels.TerminateRequest;
import com.saasbasics.platform.modules.organization.internal.application.OrganizationWorkforceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/assignments")
public class AssignmentController {

    private final OrganizationWorkforceService service;

    public AssignmentController(OrganizationWorkforceService service) {
        this.service = service;
    }

    @GetMapping
    @RequirePermission("organization:query")
    public ApiResponse<PageResponse<AssignmentModels.Response>> list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return ApiResponse.success(service.listAssignments(page, size));
    }

    @GetMapping("/{publicId}")
    @RequirePermission("organization:query")
    public ApiResponse<AssignmentModels.Response> get(@PathVariable UUID publicId) {
        return ApiResponse.success(service.getAssignment(publicId));
    }

    @PostMapping
    @RequirePermission("organization:write")
    public ApiResponse<AssignmentModels.Response> create(@Valid @RequestBody AssignmentModels.CreateRequest request) {
        return ApiResponse.success(service.createAssignment(request));
    }

    @PutMapping("/{publicId}")
    @RequirePermission("organization:write")
    public ApiResponse<AssignmentModels.Response> update(@PathVariable UUID publicId,
                                                          @Valid @RequestBody AssignmentModels.UpdateRequest request) {
        return ApiResponse.success(service.updateAssignment(publicId, request));
    }

    @PatchMapping("/{publicId}/lifecycle")
    @RequirePermission("organization:lifecycle")
    public ApiResponse<AssignmentModels.Response> transition(@PathVariable UUID publicId,
                                                              @Valid @RequestBody LifecycleTransitionRequest request) {
        return ApiResponse.success(service.transitionAssignment(publicId, request));
    }

    @PostMapping("/{publicId}/terminate")
    @RequirePermission("organization:lifecycle")
    public ApiResponse<AssignmentModels.Response> terminate(@PathVariable UUID publicId,
                                                             @Valid @RequestBody TerminateRequest request) {
        return ApiResponse.success(service.terminateAssignment(publicId, request.effectiveAt(), request.expectedVersion()));
    }

    @DeleteMapping("/{publicId}")
    @RequirePermission("organization:delete")
    public ApiResponse<Void> delete(@PathVariable UUID publicId, @RequestParam @Min(0) int expectedVersion) {
        service.deleteAssignment(publicId, expectedVersion);
        return ApiResponse.success(null);
    }
}
