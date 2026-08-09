package com.saasbasics.platform.modules.organization.internal.web;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.api.PageResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.organization.api.model.OrgUnitModels;
import com.saasbasics.platform.modules.organization.api.model.OrganizationCommonModels.LifecycleTransitionRequest;
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
@RequestMapping("/api/org-units")
public class OrgUnitController {

    private final OrganizationStructureService service;

    public OrgUnitController(OrganizationStructureService service) {
        this.service = service;
    }

    @GetMapping
    @RequirePermission("organization:query")
    public ApiResponse<PageResponse<OrgUnitModels.Response>> list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return ApiResponse.success(service.listUnits(page, size));
    }

    @GetMapping("/{publicId}")
    @RequirePermission("organization:query")
    public ApiResponse<OrgUnitModels.Response> get(@PathVariable UUID publicId) {
        return ApiResponse.success(service.getUnit(publicId));
    }

    @PostMapping
    @RequirePermission("organization:write")
    public ApiResponse<OrgUnitModels.Response> create(@Valid @RequestBody OrgUnitModels.CreateRequest request) {
        return ApiResponse.success(service.createUnit(request));
    }

    @PutMapping("/{publicId}")
    @RequirePermission("organization:write")
    public ApiResponse<OrgUnitModels.Response> update(@PathVariable UUID publicId,
                                                       @Valid @RequestBody OrgUnitModels.UpdateRequest request) {
        return ApiResponse.success(service.updateUnit(publicId, request));
    }

    @PostMapping("/{publicId}/move")
    @RequirePermission("organization:write")
    public ApiResponse<OrgUnitModels.Response> move(@PathVariable UUID publicId,
                                                     @Valid @RequestBody OrgUnitModels.MoveRequest request) {
        return ApiResponse.success(service.moveUnit(publicId, request));
    }

    @PatchMapping("/{publicId}/lifecycle")
    @RequirePermission("organization:lifecycle")
    public ApiResponse<OrgUnitModels.Response> transition(@PathVariable UUID publicId,
                                                           @Valid @RequestBody LifecycleTransitionRequest request) {
        return ApiResponse.success(service.transitionUnit(publicId, request));
    }

    @DeleteMapping("/{publicId}")
    @RequirePermission("organization:delete")
    public ApiResponse<Void> delete(@PathVariable UUID publicId, @RequestParam @Min(0) int expectedVersion) {
        service.deleteUnit(publicId, expectedVersion);
        return ApiResponse.success(null);
    }
}
