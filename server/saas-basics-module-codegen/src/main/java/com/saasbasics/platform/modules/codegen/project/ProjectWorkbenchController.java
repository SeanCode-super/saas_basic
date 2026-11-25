package com.saasbasics.platform.modules.codegen.project;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.common.auth.RequireTenantFeature;
import java.io.IOException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/codegen/project-workbench")
@RequireTenantFeature("codegen")
@RequirePermission("codegen:project:write")
public class ProjectWorkbenchController {
    private final ProjectWorkbench workbench;
    public ProjectWorkbenchController(ProjectWorkbench workbench) { this.workbench = workbench; }

    @PostMapping("/analyze")
    public ApiResponse<ProjectWorkbench.Analysis> analyze(@RequestBody ProjectDesign.Sources sources) throws IOException {
        return ApiResponse.success(workbench.analyze(sources));
    }

    @PostMapping("/preview")
    public ApiResponse<ProjectWorkbench.Preview> preview(@RequestBody ProjectDesign.Review review) throws IOException {
        return ApiResponse.success(workbench.preview(review));
    }

    @PostMapping(value="/download", produces="application/zip")
    public ResponseEntity<byte[]> download(@RequestBody ProjectDesign.Review review) throws IOException {
        byte[] bytes = workbench.download(review);
        return ResponseEntity.ok().header("Content-Disposition","attachment; filename=\""+review.design().code()+".zip\"").body(bytes);
    }
}
