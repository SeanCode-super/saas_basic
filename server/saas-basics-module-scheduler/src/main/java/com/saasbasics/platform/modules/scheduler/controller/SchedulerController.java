package com.saasbasics.platform.modules.scheduler.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.scheduler.dto.JobAlarmResponse;
import com.saasbasics.platform.modules.scheduler.dto.JobAlarmSaveRequest;
import com.saasbasics.platform.modules.scheduler.dto.JobLogResponse;
import com.saasbasics.platform.modules.scheduler.dto.JobResponse;
import com.saasbasics.platform.modules.scheduler.dto.JobRetryRequest;
import com.saasbasics.platform.modules.scheduler.dto.JobSaveRequest;
import com.saasbasics.platform.modules.scheduler.dto.JobStatusUpdateRequest;
import com.saasbasics.platform.modules.scheduler.dto.JobTriggerRequest;
import com.saasbasics.platform.modules.scheduler.service.SchedulerService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scheduler")
public class SchedulerController {

    private final SchedulerService schedulerService;

    public SchedulerController(SchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    @GetMapping("/jobs")
    @RequirePermission("scheduler:job:query")
    public ApiResponse<List<JobResponse>> list() {
        return ApiResponse.success(schedulerService.listJobs());
    }

    @GetMapping("/jobs/{id}")
    @RequirePermission("scheduler:job:query")
    public ApiResponse<JobResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(schedulerService.getJob(id));
    }

    @PostMapping("/jobs")
    @RequirePermission("scheduler:job:write")
    public ApiResponse<JobResponse> create(@Valid @RequestBody JobSaveRequest request) {
        return ApiResponse.success(schedulerService.createJob(request));
    }

    @PutMapping("/jobs/{id}")
    @RequirePermission("scheduler:job:write")
    public ApiResponse<JobResponse> update(@PathVariable Long id, @Valid @RequestBody JobSaveRequest request) {
        return ApiResponse.success(schedulerService.updateJob(id, request));
    }

    @PatchMapping("/jobs/{id}/status")
    @RequirePermission("scheduler:job:write")
    public ApiResponse<JobResponse> updateStatus(@PathVariable Long id, @Valid @RequestBody JobStatusUpdateRequest request) {
        return ApiResponse.success(schedulerService.updateJobStatus(id, request));
    }

    @GetMapping("/logs")
    @RequirePermission("scheduler:job:query")
    public ApiResponse<List<JobLogResponse>> logs() {
        return ApiResponse.success(schedulerService.listLogs());
    }

    @PostMapping("/jobs/{id}/trigger")
    @RequirePermission("scheduler:job:write")
    public ApiResponse<JobLogResponse> trigger(@PathVariable Long id, @Valid @RequestBody JobTriggerRequest request) {
        return ApiResponse.success(schedulerService.triggerJob(id, request));
    }

    @PostMapping("/jobs/{id}/retry")
    @RequirePermission("scheduler:job:write")
    public ApiResponse<JobLogResponse> retry(@PathVariable Long id, @Valid @RequestBody JobRetryRequest request) {
        return ApiResponse.success(schedulerService.retryJob(id, request));
    }

    @GetMapping("/alarms")
    @RequirePermission("scheduler:job:query")
    public ApiResponse<List<JobAlarmResponse>> alarms() {
        return ApiResponse.success(schedulerService.listAlarms());
    }

    @PostMapping("/alarms")
    @RequirePermission("scheduler:job:write")
    public ApiResponse<JobAlarmResponse> createAlarm(@Valid @RequestBody JobAlarmSaveRequest request) {
        return ApiResponse.success(schedulerService.createAlarm(request));
    }

    @PutMapping("/alarms/{id}")
    @RequirePermission("scheduler:job:write")
    public ApiResponse<JobAlarmResponse> updateAlarm(@PathVariable Long id,
                                                     @Valid @RequestBody JobAlarmSaveRequest request) {
        return ApiResponse.success(schedulerService.updateAlarm(id, request));
    }
}
