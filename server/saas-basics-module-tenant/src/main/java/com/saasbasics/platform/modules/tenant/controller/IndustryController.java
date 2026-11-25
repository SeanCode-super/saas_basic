package com.saasbasics.platform.modules.tenant.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.common.auth.RequirePlatformTenant;
import com.saasbasics.platform.modules.tenant.service.IndustryManifest;
import com.saasbasics.platform.modules.tenant.service.IndustryWorkspace;
import com.saasbasics.platform.modules.tenant.service.IndustryRegistry;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/industry")
public class IndustryController {
    private final IndustryWorkspace workspace;
    private final IndustryRegistry registry;

    public IndustryController(IndustryWorkspace workspace, IndustryRegistry registry) {
        this.workspace = workspace;
        this.registry = registry;
    }

    @GetMapping("/registry")
    @RequirePlatformTenant
    @RequirePermission("tenant:write")
    public ApiResponse<List<IndustryManifest>> registered() {
        return ApiResponse.success(registry.catalog());
    }

    public record Registration(IndustryManifest manifest, @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY) String secret) {
        @Override public String toString() { return "Industry application registration"; }
    }

    @PostMapping("/registry")
    @RequirePlatformTenant
    @RequirePermission("tenant:write")
    public ApiResponse<Void> publish(@RequestBody Registration request) {
        workspace.publish(request.manifest(),request.secret());
        return ApiResponse.success(null);
    }

    @GetMapping("/workspace")
    public ApiResponse<List<IndustryWorkspace.Entry>> list(@RequestParam String terminal) {
        return ApiResponse.success(workspace.entries(terminal));
    }

    @PostMapping("/{code}/actions/{action}")
    public ApiResponse<JsonNode> invoke(@PathVariable String code,@PathVariable String action,
            @RequestParam String terminal,@RequestBody IndustryWorkspace.Command command) {
        return ApiResponse.success(workspace.invoke(code,action,terminal,command));
    }

    @ExceptionHandler(IndustryWorkspace.ServiceUnavailable.class)
    public ResponseEntity<ApiResponse<Void>> unavailable() {
        return ResponseEntity.status(503).body(ApiResponse.failure("INDUSTRY_UNAVAILABLE","该行业服务暂时不可用，请稍后重试"));
    }
}
