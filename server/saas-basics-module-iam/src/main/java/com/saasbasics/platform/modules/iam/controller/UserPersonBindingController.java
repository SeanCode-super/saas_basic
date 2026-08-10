package com.saasbasics.platform.modules.iam.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.api.PageResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.iam.api.model.UserPersonBindingModels;
import com.saasbasics.platform.modules.iam.service.UserPersonBindingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
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
@RequestMapping("/api/iam/user-person-bindings")
public class UserPersonBindingController {

    private final UserPersonBindingService service;

    public UserPersonBindingController(UserPersonBindingService service) {
        this.service = service;
    }

    @GetMapping
    @RequirePermission("iam:user-person-binding:query")
    public ApiResponse<PageResponse<UserPersonBindingModels.Response>> list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size,
            @RequestParam(required = false) UUID userPublicId,
            @RequestParam(required = false) UUID personPublicId,
            @RequestParam(required = false) UserPersonBindingModels.Status status,
            @RequestParam(required = false) Instant effectiveAt) {
        return ApiResponse.success(service.list(page, size, userPublicId, personPublicId, status, effectiveAt));
    }

    @GetMapping("/{publicId}")
    @RequirePermission("iam:user-person-binding:query")
    public ApiResponse<UserPersonBindingModels.Response> get(@PathVariable UUID publicId) {
        return ApiResponse.success(service.get(publicId));
    }

    @PostMapping
    @RequirePermission("iam:user-person-binding:write")
    public ApiResponse<UserPersonBindingModels.Response> create(
            @Valid @RequestBody UserPersonBindingModels.CreateRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @PutMapping("/{publicId}")
    @RequirePermission("iam:user-person-binding:update")
    public ApiResponse<UserPersonBindingModels.Response> update(
            @PathVariable UUID publicId,
            @Valid @RequestBody UserPersonBindingModels.UpdateRequest request) {
        return ApiResponse.success(service.update(publicId, request));
    }

    @PatchMapping("/{publicId}/lifecycle")
    @RequirePermission("iam:user-person-binding:lifecycle")
    public ApiResponse<UserPersonBindingModels.Response> transition(
            @PathVariable UUID publicId,
            @Valid @RequestBody UserPersonBindingModels.TransitionRequest request) {
        return ApiResponse.success(service.transition(publicId, request));
    }

    @DeleteMapping("/{publicId}")
    @RequirePermission("iam:user-person-binding:delete")
    public ApiResponse<Void> delete(@PathVariable UUID publicId,
                                    @RequestParam @Min(0) int expectedVersion) {
        service.delete(publicId, expectedVersion);
        return ApiResponse.success(null);
    }
}
