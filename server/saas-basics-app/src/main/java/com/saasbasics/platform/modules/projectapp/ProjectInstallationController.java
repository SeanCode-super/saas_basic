package com.saasbasics.platform.modules.projectapp;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.*;
import com.saasbasics.platform.modules.codegen.project.ProjectDesign;
import java.io.IOException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/codegen/project-workbench")
@RequirePlatformTenant
@RequireTenantFeature("codegen")
@RequirePermission("codegen:project:write")
public class ProjectInstallationController {
    public record Install(Long tenantId,ProjectDesign.Sources sources,ProjectDesign design) {}
    private final ProjectAppInstaller installer;
    public ProjectInstallationController(ProjectAppInstaller installer) { this.installer=installer; }
    @PostMapping("/install")
    @RequirePermission("tenant:write")
    public ApiResponse<ProjectAppInstaller.Result> install(@RequestBody Install request) throws IOException {
        return ApiResponse.success(installer.install(request.tenantId(),new ProjectDesign.Review(request.sources(),request.design())));
    }
}
