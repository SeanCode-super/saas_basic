package com.saasbasics.platform.modules.file.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.modules.file.dto.ExportDtos.ExportApprovalRequest;
import com.saasbasics.platform.modules.file.dto.ExportDtos.ExportCreateRequest;
import com.saasbasics.platform.modules.file.dto.ExportDtos.ExportDatasetInfoDTO;
import com.saasbasics.platform.modules.file.dto.ExportDtos.ExportStatsDTO;
import com.saasbasics.platform.modules.file.dto.ExportDtos.ExportTaskDTO;
import com.saasbasics.platform.modules.file.dto.ExportDtos.ExportTemplateDTO;
import com.saasbasics.platform.modules.file.dto.ExportDtos.ExportTemplateSaveRequest;
import com.saasbasics.platform.modules.file.dto.ExportDtos.ReportScheduleDTO;
import com.saasbasics.platform.modules.file.dto.ExportDtos.ReportScheduleSaveRequest;
import com.saasbasics.platform.modules.file.service.EnterpriseExportService;
import com.saasbasics.platform.modules.file.service.EnterpriseExportService.DownloadedExportFile;
import com.saasbasics.platform.modules.file.service.EnterpriseExportService.SyncExportPayload;
import com.saasbasics.platform.modules.file.service.ReportScheduleService;
import com.saasbasics.platform.ratelimit.RateLimit;
import com.saasbasics.platform.ratelimit.RateLimitType;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/exports")
public class ExportController {

    private final EnterpriseExportService exportService;
    private final ReportScheduleService reportScheduleService;

    public ExportController(EnterpriseExportService exportService, ReportScheduleService reportScheduleService) {
        this.exportService = exportService;
        this.reportScheduleService = reportScheduleService;
    }

    @GetMapping("/datasets")
    public ApiResponse<List<ExportDatasetInfoDTO>> listDatasets() {
        return ApiResponse.success(exportService.listDatasets());
    }

    @PostMapping("/sync")
    @RateLimit(type = RateLimitType.TENANT, permits = 60, windowSeconds = 60,
            message = "同步导出调用频次过高，请使用异步导出或稍后再试")
    public ResponseEntity<byte[]> exportSync(@RequestBody ExportCreateRequest request) {
        SyncExportPayload payload = exportService.exportSync(request);
        String encodedName = URLEncoder.encode(payload.fileName(), StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=utf-8''" + encodedName)
                .header("Access-Control-Expose-Headers", "Content-Disposition, X-Export-Trace-Code, X-Export-Sha256")
                .header("X-Export-Trace-Code", payload.traceCode())
                .header("X-Export-Sha256", payload.sha256())
                .contentType(MediaType.parseMediaType(payload.contentType()))
                .contentLength(payload.bytes().length)
                .body(payload.bytes());
    }

    @PostMapping("/async")
    @RateLimit(type = RateLimitType.TENANT, permits = 30, windowSeconds = 60,
            message = "异步导出任务提交过于频繁，请稍后再试")
    public ApiResponse<ExportTaskDTO> submitAsync(@RequestBody ExportCreateRequest request) {
        return ApiResponse.success(exportService.submitAsyncExport(request));
    }

    @GetMapping("/tasks")
    public ApiResponse<List<ExportTaskDTO>> listTasks() {
        return ApiResponse.success(exportService.listTasks());
    }

    @GetMapping("/stats")
    public ApiResponse<ExportStatsDTO> getStats() {
        return ApiResponse.success(exportService.getStats());
    }

    @GetMapping("/tasks/{id}/download")
    public ResponseEntity<byte[]> downloadTask(@PathVariable("id") Long id) {
        DownloadedExportFile file = exportService.downloadTaskFile(id);
        String encodedName = URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=utf-8''" + encodedName)
                .header("Access-Control-Expose-Headers", "Content-Disposition, X-Export-Trace-Code")
                .header("X-Export-Trace-Code", file.traceCode())
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.bytes().length)
                .body(file.bytes());
    }

    @DeleteMapping("/tasks/{id}")
    public ApiResponse<Void> deleteTask(@PathVariable("id") Long id) {
        exportService.deleteTask(id);
        return ApiResponse.success(null);
    }

    @GetMapping("/templates")
    public ApiResponse<List<ExportTemplateDTO>> listTemplates(
            @RequestParam(name = "sourceCode", required = false) String sourceCode) {
        return ApiResponse.success(exportService.listTemplates(sourceCode));
    }

    @PostMapping("/templates")
    public ApiResponse<ExportTemplateDTO> saveTemplate(@RequestBody ExportTemplateSaveRequest request) {
        return ApiResponse.success(exportService.saveTemplate(request));
    }

    @DeleteMapping("/templates/{id}")
    public ApiResponse<Void> deleteTemplate(@PathVariable("id") Long id) {
        exportService.deleteTemplate(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/tasks/{id}/approve")
    public ApiResponse<ExportTaskDTO> approveTask(
            @PathVariable("id") Long id,
            @RequestBody(required = false) ExportApprovalRequest request) {
        return ApiResponse.success(exportService.approveExportTask(id, request != null ? request.reason() : null));
    }

    @PostMapping("/tasks/{id}/reject")
    public ApiResponse<ExportTaskDTO> rejectTask(
            @PathVariable("id") Long id,
            @RequestBody(required = false) ExportApprovalRequest request) {
        return ApiResponse.success(exportService.rejectExportTask(id, request != null ? request.reason() : null));
    }

    @GetMapping("/schedules")
    public ApiResponse<List<ReportScheduleDTO>> listSchedules() {
        return ApiResponse.success(reportScheduleService.listSchedules());
    }

    @PostMapping("/schedules")
    public ApiResponse<ReportScheduleDTO> saveSchedule(@RequestBody ReportScheduleSaveRequest request) {
        return ApiResponse.success(reportScheduleService.saveSchedule(request));
    }

    @DeleteMapping("/schedules/{id}")
    public ApiResponse<Void> deleteSchedule(@PathVariable("id") Long id) {
        reportScheduleService.deleteSchedule(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/schedules/{id}/trigger")
    public ApiResponse<ExportTaskDTO> triggerSchedule(@PathVariable("id") Long id) {
        return ApiResponse.success(reportScheduleService.triggerNow(id));
    }
}
