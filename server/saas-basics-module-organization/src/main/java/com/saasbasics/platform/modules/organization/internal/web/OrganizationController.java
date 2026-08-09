package com.saasbasics.platform.modules.organization.internal.web;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.api.PageResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.organization.api.model.OrganizationCommonModels.LifecycleTransitionRequest;
import com.saasbasics.platform.modules.organization.api.model.OrganizationModels;
import com.saasbasics.platform.modules.organization.internal.application.OrganizationCatalogService;
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
@RequestMapping("/api/organizations")
public class OrganizationController {

    private final OrganizationCatalogService service;

    public OrganizationController(OrganizationCatalogService service) {
        this.service = service;
    }

    @GetMapping
    @RequirePermission("organization:query")
    public ApiResponse<PageResponse<OrganizationModels.Response>> list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return ApiResponse.success(service.listOrganizations(page, size));
    }

    @GetMapping("/{publicId}")
    @RequirePermission("organization:query")
    public ApiResponse<OrganizationModels.Response> get(@PathVariable UUID publicId) {
        return ApiResponse.success(service.getOrganization(publicId));
    }

    @PostMapping
    @RequirePermission("organization:write")
    public ApiResponse<OrganizationModels.Response> create(@Valid @RequestBody OrganizationModels.CreateRequest request) {
        return ApiResponse.success(service.createOrganization(request));
    }

    @PutMapping("/{publicId}")
    @RequirePermission("organization:write")
    public ApiResponse<OrganizationModels.Response> update(@PathVariable UUID publicId,
                                                            @Valid @RequestBody OrganizationModels.UpdateRequest request) {
        return ApiResponse.success(service.updateOrganization(publicId, request));
    }

    @PatchMapping("/{publicId}/lifecycle")
    @RequirePermission("organization:lifecycle")
    public ApiResponse<OrganizationModels.Response> transition(
            @PathVariable UUID publicId,
            @Valid @RequestBody LifecycleTransitionRequest request) {
        return ApiResponse.success(service.transitionOrganization(publicId, request));
    }

    @DeleteMapping("/{publicId}")
    @RequirePermission("organization:delete")
    public ApiResponse<Void> delete(@PathVariable UUID publicId,
                                    @RequestParam @Min(0) int expectedVersion) {
        service.deleteOrganization(publicId, expectedVersion);
        return ApiResponse.success(null);
    }
}
