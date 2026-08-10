package com.saasbasics.platform.modules.organization.internal.web;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.organization.api.OrganizationDirectory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organization-context")
public class OrganizationContextController {

    private final OrganizationDirectory directory;

    public OrganizationContextController(OrganizationDirectory directory) {
        this.directory = directory;
    }

    @GetMapping("/options")
    @RequirePermission("organization:query")
    public ApiResponse<List<OrganizationDirectory.AssignmentSummary>> options(
            @RequestParam UUID personPublicId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant effectiveAt) {
        return ApiResponse.success(directory.findEffectiveAssignments(personPublicId, effectiveAt));
    }
}
