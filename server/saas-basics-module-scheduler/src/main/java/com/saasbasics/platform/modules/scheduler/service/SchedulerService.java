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
import com.saasbasics.platform.modules.scheduler.entity.JobAlarmEntity;
import com.saasbasics.platform.modules.scheduler.entity.JobEntity;
import com.saasbasics.platform.modules.scheduler.entity.JobLogEntity;
import com.saasbasics.platform.modules.scheduler.mapper.JobAlarmMapper;
import com.saasbasics.platform.modules.scheduler.mapper.JobMapper;
import com.saasbasics.platform.modules.scheduler.mapper.JobLogMapper;
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

    public SchedulerService(ObjectProvider<JobMapper> jobMapperProvider,
                            ObjectProvider<JobLogMapper> jobLogMapperProvider,
                            ObjectProvider<JobAlarmMapper> jobAlarmMapperProvider) {
        this.jobMapperProvider = jobMapperProvider;
        this.jobLogMapperProvider = jobLogMapperProvider;
        this.jobAlarmMapperProvider = jobAlarmMapperProvider;
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

        JobLogMapper logMapper = requiredLogMapper();
        JobLogEntity entity = new JobLogEntity();
        entity.setTenantId(request.tenantId());
        entity.setJobId(id);
        entity.setTriggerId(0L);
        entity.setExecutionNo("MANUAL-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        entity.setExecutorId(job.getExecutorId());
        entity.setStartedAt(LocalDateTime.now());
        entity.setFinishedAt(LocalDateTime.now());
        entity.setRunStatus("SUCCESS");
        entity.setSuccess(true);
        entity.setRetryCount(0);
        entity.setErrorMessage(request.remark());
        entity.setTraceId("manual-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        logMapper.insert(entity);
        return listLogs().stream()
                .filter(item -> item.id().equals(entity.getId()))
                .findFirst()
                .orElseThrow(() -> new BizException("SCHED_JOB_LOG_NOT_FOUND", "Scheduler log not found"));
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

        JobLogEntity entity = new JobLogEntity();
        entity.setTenantId(request.tenantId());
        entity.setJobId(id);
        entity.setTriggerId(0L);
        entity.setExecutionNo("RETRY-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        entity.setExecutorId(job.getExecutorId());
        entity.setStartedAt(LocalDateTime.now());
        entity.setFinishedAt(LocalDateTime.now());
        entity.setRunStatus("RETRY_SUCCESS");
        entity.setSuccess(true);
        entity.setRetryCount(retryCount);
        entity.setErrorMessage(request.remark() == null || request.remark().isBlank() ? "手动重试成功" : request.remark());
        entity.setTraceId("retry-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        requiredLogMapper().insert(entity);

        return listLogs().stream()
                .filter(item -> item.id().equals(entity.getId()))
                .findFirst()
                .orElseThrow(() -> new BizException("SCHED_JOB_LOG_NOT_FOUND", "Scheduler log not found"));
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
