package com.saasbasics.platform.modules.system.devops.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.system.devops.dto.AppArtifactDTO;
import com.saasbasics.platform.modules.system.devops.dto.AppBuildRecordDTO;
import com.saasbasics.platform.modules.system.devops.dto.AppBuildTriggerRequest;
import com.saasbasics.platform.modules.system.devops.dto.AppServiceActionResult;
import com.saasbasics.platform.modules.system.devops.dto.AppServiceDTO;
import com.saasbasics.platform.modules.system.devops.dto.AppServiceSaveRequest;
import com.saasbasics.platform.modules.system.devops.dto.PortCheckResultDTO;
import com.saasbasics.platform.modules.system.devops.entity.AppBuildRecordEntity;
import com.saasbasics.platform.modules.system.devops.service.AppBuildPipelineService;
import com.saasbasics.platform.modules.system.devops.service.AppLogStreamService;
import com.saasbasics.platform.modules.system.devops.service.AppServiceLifecycleManager;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/infra/devops")
public class AppServiceDevopsController {

    private final AppServiceLifecycleManager lifecycleManager;
    private final AppBuildPipelineService buildPipelineService;
    private final AppLogStreamService logStreamService;

    public AppServiceDevopsController(AppServiceLifecycleManager lifecycleManager,
                                     AppBuildPipelineService buildPipelineService,
                                     AppLogStreamService logStreamService) {
        this.lifecycleManager = lifecycleManager;
        this.buildPipelineService = buildPipelineService;
        this.logStreamService = logStreamService;
    }

    @GetMapping("/services")
    @RequirePermission("system:config:query")
    public ApiResponse<List<AppServiceDTO>> listServices() {
        return ApiResponse.success(lifecycleManager.listServices());
    }

    @GetMapping("/services/check-port")
    @RequirePermission("system:config:query")
    public ApiResponse<PortCheckResultDTO> checkPort(@RequestParam int port,
                                                     @RequestParam(required = false) Long excludeServiceId) {
        return ApiResponse.success(lifecycleManager.checkPortStatus(port, excludeServiceId));
    }

    @GetMapping("/services/{id}")
    @RequirePermission("system:config:query")
    public ApiResponse<AppServiceDTO> getService(@PathVariable Long id) {
        return ApiResponse.success(lifecycleManager.getService(id));
    }

    @PostMapping("/services")
    @RequirePermission("system:config:update")
    public ApiResponse<Long> createService(@RequestBody AppServiceSaveRequest req) {
        return ApiResponse.success(lifecycleManager.saveService(req, null));
    }

    @PutMapping("/services/{id}")
    @RequirePermission("system:config:update")
    public ApiResponse<Long> updateService(@PathVariable Long id, @RequestBody AppServiceSaveRequest req) {
        return ApiResponse.success(lifecycleManager.saveService(req, id));
    }

    @DeleteMapping("/services/{id}")
    @RequirePermission("system:config:update")
    public ApiResponse<Void> deleteService(@PathVariable Long id) {
        lifecycleManager.deleteService(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/services/{id}/start")
    @RequirePermission("system:config:update")
    public ApiResponse<AppServiceActionResult> startService(@PathVariable Long id) {
        return ApiResponse.success(lifecycleManager.startService(id));
    }

    @PostMapping("/services/{id}/stop")
    @RequirePermission("system:config:update")
    public ApiResponse<AppServiceActionResult> stopService(@PathVariable Long id,
                                                          @RequestParam(defaultValue = "15") int timeoutSeconds) {
        return ApiResponse.success(lifecycleManager.stopService(id, timeoutSeconds));
    }

    @PostMapping("/services/{id}/restart")
    @RequirePermission("system:config:update")
    public ApiResponse<AppServiceActionResult> restartService(@PathVariable Long id,
                                                            @RequestParam(defaultValue = "15") int timeoutSeconds) {
        return ApiResponse.success(lifecycleManager.restartService(id, timeoutSeconds));
    }

    @PostMapping("/services/{id}/kill")
    @RequirePermission("system:config:update")
    public ApiResponse<AppServiceActionResult> killService(@PathVariable Long id) {
        return ApiResponse.success(lifecycleManager.killService(id));
    }

    @PostMapping("/services/{id}/build")
    @RequirePermission("system:config:update")
    public ApiResponse<AppBuildRecordEntity> triggerBuild(@PathVariable Long id,
                                                          @RequestBody(required = false) AppBuildTriggerRequest req) {
        String cmd = req != null ? req.getCustomBuildCmd() : null;
        return ApiResponse.success(buildPipelineService.triggerBuild(id, cmd, "MANUAL", "admin"));
    }

    @GetMapping("/services/{id}/build-records")
    @RequirePermission("system:config:query")
    public ApiResponse<List<AppBuildRecordDTO>> listBuildRecords(@PathVariable Long id) {
        return ApiResponse.success(lifecycleManager.listBuildRecords(id));
    }

    @GetMapping("/build-records/{recordId}/logs")
    @RequirePermission("system:config:query")
    public ApiResponse<List<String>> getBuildLogs(@PathVariable Long recordId) {
        return ApiResponse.success(buildPipelineService.getLiveBuildLogs(recordId));
    }

    @GetMapping("/services/{id}/logs/recent")
    @RequirePermission("system:config:query")
    public ApiResponse<List<String>> getRecentLogs(@PathVariable Long id,
                                                   @RequestParam(defaultValue = "300") int lines) {
        return ApiResponse.success(logStreamService.getRecentLogs(id, lines));
    }

    @GetMapping(value = "/services/{id}/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLogs(@PathVariable Long id,
                                 @RequestParam(value = "history", defaultValue = "false") boolean history) {
        return logStreamService.createLogEmitter(id, history);
    }

    @GetMapping("/services/{id}/artifacts")
    @RequirePermission("system:config:query")
    public ApiResponse<List<AppArtifactDTO>> listArtifacts(@PathVariable Long id) {
        return ApiResponse.success(lifecycleManager.listArtifacts(id));
    }

    @PostMapping(value = "/services/{id}/upload-artifact", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("system:config:update")
    public ApiResponse<AppArtifactDTO> uploadArtifact(@PathVariable Long id,
                                                      @RequestParam("file") MultipartFile file,
                                                      @RequestParam(value = "remark", required = false) String remark) throws Exception {
        return ApiResponse.success(lifecycleManager.uploadArtifact(id, file, remark));
    }

    @PostMapping("/services/{id}/artifacts/{artifactId}/rollback")
    @RequirePermission("system:config:update")
    public ApiResponse<AppServiceActionResult> rollbackArtifact(@PathVariable Long id,
                                                                @PathVariable Long artifactId) throws Exception {
        return ApiResponse.success(lifecycleManager.rollbackArtifact(id, artifactId));
    }
}
