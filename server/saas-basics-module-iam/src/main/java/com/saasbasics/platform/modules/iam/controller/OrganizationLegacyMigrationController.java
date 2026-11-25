package com.saasbasics.platform.modules.iam.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.iam.service.OrganizationLegacyMigrationService;
import com.saasbasics.platform.modules.iam.service.OrganizationLegacyMigrationService.LegacyMigrationSummary;
import com.saasbasics.platform.modules.organization.api.OrganizationMigrationFacade.MigrationMode;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/iam/organization-migration")
public class OrganizationLegacyMigrationController {

    private final OrganizationLegacyMigrationService migrationService;

    public OrganizationLegacyMigrationController(OrganizationLegacyMigrationService migrationService) {
        this.migrationService = migrationService;
    }

    public record MigrationRequest(String organizationCode, String organizationName) {
    }

    @PostMapping("/validate")
    @RequirePermission("iam:user:update")
    public ApiResponse<LegacyMigrationSummary> validate(@RequestBody(required = false) MigrationRequest request) {
        String code = request != null ? request.organizationCode() : null;
        String name = request != null ? request.organizationName() : null;
        return ApiResponse.success(migrationService.runMigration(MigrationMode.VALIDATE, code, name));
    }

    @PostMapping("/apply")
    @RequirePermission("iam:user:update")
    public ApiResponse<LegacyMigrationSummary> apply(@RequestBody(required = false) MigrationRequest request) {
        String code = request != null ? request.organizationCode() : null;
        String name = request != null ? request.organizationName() : null;
        return ApiResponse.success(migrationService.runMigration(MigrationMode.APPLY, code, name));
    }

    @PostMapping("/verify")
    @RequirePermission("iam:user:query")
    public ApiResponse<LegacyMigrationSummary> verify(@RequestBody(required = false) MigrationRequest request) {
        String code = request != null ? request.organizationCode() : null;
        String name = request != null ? request.organizationName() : null;
        return ApiResponse.success(migrationService.runMigration(MigrationMode.VERIFY, code, name));
    }
}
