package com.saasbasics.platform.modules.scheduler.service;

import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.scheduler.dto.JobAlarmResponse;
import com.saasbasics.platform.modules.scheduler.dto.JobAlarmSaveRequest;
import com.saasbasics.platform.modules.scheduler.dto.JobLogResponse;
import com.saasbasics.platform.modules.scheduler.dto.JobRetryRequest;
import com.saasbasics.platform.modules.scheduler.dto.JobResponse;
import com.saasbasics.platform.modules.scheduler.dto.JobSaveRequest;
import com.saasbasics.platform.modules.scheduler.dto.JobStatusUpdateRequest;
import com.saasbasics.platform.modules.scheduler.dto.JobTriggerRequest;
import com.saasbasics.platform.modules.scheduler.engine.JobExecutionEngine;
import com.saasbasics.platform.modules.scheduler.entity.JobAlarmEntity;
import com.saasbasics.platform.modules.scheduler.entity.JobEntity;
import com.saasbasics.platform.modules.scheduler.entity.JobLogEntity;
import com.saasbasics.platform.modules.scheduler.entity.JobTriggerEntity;
import com.saasbasics.platform.modules.scheduler.mapper.JobAlarmMapper;
import com.saasbasics.platform.modules.scheduler.mapper.JobMapper;
import com.saasbasics.platform.modules.scheduler.mapper.JobLogMapper;
import com.saasbasics.platform.modules.scheduler.mapper.JobTriggerMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class SchedulerService {

    private final ObjectProvider<JobMapper> jobMapperProvider;
    private final ObjectProvider<JobLogMapper> jobLogMapperProvider;
    private final ObjectProvider<JobAlarmMapper> jobAlarmMapperProvider;
    private final ObjectProvider<JobTriggerMapper> jobTriggerMapperProvider;
    private final JobExecutionEngine jobExecutionEngine;

    public SchedulerService(ObjectProvider<JobMapper> jobMapperProvider,
                            ObjectProvider<JobLogMapper> jobLogMapperProvider,
                            ObjectProvider<JobAlarmMapper> jobAlarmMapperProvider,
                            ObjectProvider<JobTriggerMapper> jobTriggerMapperProvider,
                            JobExecutionEngine jobExecutionEngine) {
        this.jobMapperProvider = jobMapperProvider;
        this.jobLogMapperProvider = jobLogMapperProvider;
        this.jobAlarmMapperProvider = jobAlarmMapperProvider;
        this.jobTriggerMapperProvider = jobTriggerMapperProvider;
        this.jobExecutionEngine = jobExecutionEngine;
    }

    public List<JobResponse> listJobs() {
        return requiredMapper().selectJobList();
    }

    public JobResponse getJob(Long id) {
        JobResponse job = requiredMapper().selectJobById(id);
        if (job == null) {
            throw new BizException("SCHED_JOB_NOT_FOUND", "Scheduler job not found");
        }
        return job;
    }

    public JobResponse createJob(JobSaveRequest request) {
        if (request.handlerName() != null && !jobExecutionEngine.hasHandler(request.handlerName())) {
            throw new BizException("HANDLER_NOT_FOUND", "指定的任务处理器 [" + request.handlerName() + "] 不存在，系统注册的可用处理器: " + jobExecutionEngine.getAvailableHandlerNames());
        }
        JobEntity entity = new JobEntity();
        apply(entity, request);
        requiredMapper().insert(entity);
        return getJob(entity.getId());
    }

    public JobResponse updateJob(Long id, JobSaveRequest request) {
        JobMapper mapper = requiredMapper();
        JobEntity entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1) {
            throw new BizException("SCHED_JOB_NOT_FOUND", "Scheduler job not found");
        }
        if (request.handlerName() != null && !jobExecutionEngine.hasHandler(request.handlerName())) {
            throw new BizException("HANDLER_NOT_FOUND", "指定的任务处理器 [" + request.handlerName() + "] 不存在，系统注册的可用处理器: " + jobExecutionEngine.getAvailableHandlerNames());
        }
        apply(entity, request);
        mapper.updateById(entity);
        return getJob(id);
    }

    public JobResponse updateJobStatus(Long id, JobStatusUpdateRequest request) {
        JobMapper mapper = requiredMapper();
        JobEntity entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1) {
            throw new BizException("SCHED_JOB_NOT_FOUND", "Scheduler job not found");
        }
        entity.setTenantId(request.tenantId());
        entity.setStatus(request.status());
        mapper.updateById(entity);

        if ("ENABLED".equalsIgnoreCase(request.status())) {
            String cron = "0 */5 * * * ?";
            JobTriggerMapper triggerMapper = jobTriggerMapperProvider.getIfAvailable();
            if (triggerMapper != null) {
                JobTriggerEntity trigger = triggerMapper.selectPrimaryTriggerByJobId(id);
                if (trigger != null && trigger.getCronExpr() != null && !trigger.getCronExpr().isBlank()) {
                    cron = trigger.getCronExpr();
                }
            }
            jobExecutionEngine.scheduleJob(entity, cron);
        } else {
            jobExecutionEngine.cancelScheduledJob(id);
        }

        return getJob(id);
    }

    public List<JobLogResponse> listLogs() {
        return requiredLogMapper().selectLogList();
    }

    public JobLogResponse triggerJob(Long id, JobTriggerRequest request) {
        JobMapper jobMapper = requiredMapper();
        JobEntity job = jobMapper.selectById(id);
        if (job == null || job.getDeleted() != null && job.getDeleted() == 1) {
            throw new BizException("SCHED_JOB_NOT_FOUND", "Scheduler job not found");
        }

        String executionNo = "MANUAL-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        JobLogEntity logEntity = jobExecutionEngine.executeJobSynchronously(job, "MANUAL", executionNo, 0, request.remark());

        return listLogs().stream()
                .filter(item -> item.id().equals(logEntity.getId()))
                .findFirst()
                .orElse(new JobLogResponse(
                        logEntity.getId(),
                        logEntity.getTenantId(),
                        logEntity.getJobId(),
                        logEntity.getExecutionNo(),
                        logEntity.getExecutorId(),
                        logEntity.getStartedAt(),
                        logEntity.getFinishedAt(),
                        logEntity.getRunStatus(),
                        logEntity.getSuccess(),
                        logEntity.getRetryCount(),
                        logEntity.getErrorMessage(),
                        logEntity.getTraceId()
                ));
    }

    public JobLogResponse retryJob(Long id, JobRetryRequest request) {
        JobMapper jobMapper = requiredMapper();
        JobEntity job = jobMapper.selectById(id);
        if (job == null || job.getDeleted() != null && job.getDeleted() == 1) {
            throw new BizException("SCHED_JOB_NOT_FOUND", "Scheduler job not found");
        }

        int retryCount = (int) listLogs().stream()
                .filter(item -> item.jobId().equals(id))
                .mapToInt(item -> item.retryCount() == null ? 0 : item.retryCount())
                .max()
                .orElse(0) + 1;

        String executionNo = "RETRY-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        JobLogEntity logEntity = jobExecutionEngine.executeJobSynchronously(job, "RETRY", executionNo, retryCount,
                request.remark() == null || request.remark().isBlank() ? "手动补偿触发重试" : request.remark());

        return listLogs().stream()
                .filter(item -> item.id().equals(logEntity.getId()))
                .findFirst()
                .orElse(new JobLogResponse(
                        logEntity.getId(),
                        logEntity.getTenantId(),
                        logEntity.getJobId(),
                        logEntity.getExecutionNo(),
                        logEntity.getExecutorId(),
                        logEntity.getStartedAt(),
                        logEntity.getFinishedAt(),
                        logEntity.getRunStatus(),
                        logEntity.getSuccess(),
                        logEntity.getRetryCount(),
                        logEntity.getErrorMessage(),
                        logEntity.getTraceId()
                ));
    }

    public List<JobAlarmResponse> listAlarms() {
        return requiredAlarmMapper().selectAlarmList();
    }

    public JobAlarmResponse createAlarm(JobAlarmSaveRequest request) {
        JobAlarmEntity entity = new JobAlarmEntity();
        apply(entity, request);
        requiredAlarmMapper().insert(entity);
        return getAlarm(entity.getId());
    }

    public JobAlarmResponse updateAlarm(Long id, JobAlarmSaveRequest request) {
        JobAlarmMapper mapper = requiredAlarmMapper();
        JobAlarmEntity entity = mapper.selectById(id);
        if (entity == null || Objects.equals(entity.getDeleted(), 1)) {
            throw new BizException("SCHED_JOB_ALARM_NOT_FOUND", "Scheduler alarm not found");
        }
        apply(entity, request);
        mapper.updateById(entity);
        return getAlarm(id);
    }

    private void apply(JobEntity entity, JobSaveRequest request) {
        entity.setTenantId(request.tenantId());
        entity.setJobCode(request.code());
        entity.setJobName(request.name());
        entity.setJobType(request.jobType());
        entity.setExecutorId(request.executorId());
        entity.setHandlerName(request.handlerName());
        entity.setStatus(request.status());
        entity.setRemark(request.remark());
    }

    private void apply(JobAlarmEntity entity, JobAlarmSaveRequest request) {
        entity.setTenantId(request.tenantId());
        entity.setJobId(request.jobId());
        entity.setAlarmCode(request.alarmCode());
        entity.setAlarmName(request.alarmName());
        entity.setChannelType(request.channelType());
        entity.setTriggerRule(request.triggerRule());
        entity.setReceiverJson(request.receiverJson());
        entity.setTemplateCode(request.templateCode());
        entity.setSilenceMinutes(request.silenceMinutes());
        entity.setStatus(request.status());
        entity.setRemark(request.remark());
    }

    private JobAlarmResponse getAlarm(Long id) {
        JobAlarmResponse alarm = requiredAlarmMapper().selectAlarmById(id);
        if (alarm == null) {
            throw new BizException("SCHED_JOB_ALARM_NOT_FOUND", "Scheduler alarm not found");
        }
        return alarm;
    }

    private JobMapper requiredMapper() {
        JobMapper mapper = jobMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "Write operations require the db profile and MySQL connection");
        }
        return mapper;
    }

    private JobLogMapper requiredLogMapper() {
        JobLogMapper mapper = jobLogMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "Write operations require the db profile and MySQL connection");
        }
        return mapper;
    }

    private JobAlarmMapper requiredAlarmMapper() {
        JobAlarmMapper mapper = jobAlarmMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "Write operations require the db profile and MySQL connection");
        }
        return mapper;
    }
}
