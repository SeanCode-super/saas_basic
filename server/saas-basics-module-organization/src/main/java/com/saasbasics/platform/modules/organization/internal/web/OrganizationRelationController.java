package com.saasbasics.platform.modules.organization.internal.web;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.api.PageResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.organization.api.model.OrganizationCommonModels.LifecycleTransitionRequest;
import com.saasbasics.platform.modules.organization.api.model.OrganizationRelationModels;
import com.saasbasics.platform.modules.organization.internal.application.OrganizationStructureService;
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
@RequestMapping("/api/organization-relations")
public class OrganizationRelationController {

    private final OrganizationStructureService service;

    public OrganizationRelationController(OrganizationStructureService service) {
        this.service = service;
    }

    @GetMapping
    @RequirePermission("organization:query")
    public ApiResponse<PageResponse<OrganizationRelationModels.Response>> list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return ApiResponse.success(service.listOrganizationRelations(page, size));
    }

    @GetMapping("/{publicId}")
    @RequirePermission("organization:query")
    public ApiResponse<OrganizationRelationModels.Response> get(@PathVariable UUID publicId) {
        return ApiResponse.success(service.getOrganizationRelation(publicId));
    }

    @PostMapping
    @RequirePermission("organization:write")
    public ApiResponse<OrganizationRelationModels.Response> create(
            @Valid @RequestBody OrganizationRelationModels.CreateRequest request) {
        return ApiResponse.success(service.createOrganizationRelation(request));
    }

    @PutMapping("/{publicId}")
    @RequirePermission("organization:write")
    public ApiResponse<OrganizationRelationModels.Response> update(
            @PathVariable UUID publicId,
            @Valid @RequestBody OrganizationRelationModels.UpdateRequest request) {
        return ApiResponse.success(service.updateOrganizationRelation(publicId, request));
    }

    @PatchMapping("/{publicId}/lifecycle")
    @RequirePermission("organization:lifecycle")
    public ApiResponse<OrganizationRelationModels.Response> transition(
            @PathVariable UUID publicId,
            @Valid @RequestBody LifecycleTransitionRequest request) {
        return ApiResponse.success(service.transitionOrganizationRelation(publicId, request));
    }

    @DeleteMapping("/{publicId}")
    @RequirePermission("organization:delete")
    public ApiResponse<Void> delete(@PathVariable UUID publicId, @RequestParam @Min(0) int expectedVersion) {
        service.deleteOrganizationRelation(publicId, expectedVersion);
        return ApiResponse.success(null);
    }
}
