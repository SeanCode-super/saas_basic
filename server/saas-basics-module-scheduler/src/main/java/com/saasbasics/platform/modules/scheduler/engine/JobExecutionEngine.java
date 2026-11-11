package com.saasbasics.platform.modules.scheduler.engine;

import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.common.tenant.TenantIgnore;
import com.saasbasics.platform.modules.scheduler.entity.JobEntity;
import com.saasbasics.platform.modules.scheduler.entity.JobLogEntity;
import com.saasbasics.platform.modules.scheduler.entity.JobTriggerEntity;
import com.saasbasics.platform.modules.scheduler.handler.JobHandler;
import com.saasbasics.platform.modules.scheduler.mapper.JobLogMapper;
import com.saasbasics.platform.modules.scheduler.mapper.JobMapper;
import com.saasbasics.platform.modules.scheduler.mapper.JobTriggerMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 工业级动态任务调度引擎。
 * 驱动真实 Spring Bean/JobHandler 执行，动态管理 Cron 任务周期，捕获实际执行耗时与异常堆栈。
 */
@Component
public class JobExecutionEngine {

    private static final Logger log = LoggerFactory.getLogger(JobExecutionEngine.class);
    private static final String SCHED_LOCK_PREFIX = "saas:sched:job:lock:";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final ObjectProvider<JobMapper> jobMapperProvider;
    private final ObjectProvider<JobLogMapper> jobLogMapperProvider;
    private final ObjectProvider<JobTriggerMapper> triggerMapperProvider;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ApplicationContext applicationContext;
    private final Map<String, JobHandler> handlerRegistry = new ConcurrentHashMap<>();
    private final Map<Long, ScheduledFuture<?>> activeSchedules = new ConcurrentHashMap<>();
    private final ThreadPoolTaskScheduler taskScheduler;

    public JobExecutionEngine(
            ObjectProvider<JobMapper> jobMapperProvider,
            ObjectProvider<JobLogMapper> jobLogMapperProvider,
            ApplicationContext applicationContext,
            List<JobHandler> handlers
    ) {
        this(jobMapperProvider, jobLogMapperProvider, null, null, applicationContext, handlers);
    }

    @Autowired
    public JobExecutionEngine(
            ObjectProvider<JobMapper> jobMapperProvider,
            ObjectProvider<JobLogMapper> jobLogMapperProvider,
            ObjectProvider<JobTriggerMapper> triggerMapperProvider,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            ApplicationContext applicationContext,
            List<JobHandler> handlers
    ) {
        this.jobMapperProvider = jobMapperProvider;
        this.jobLogMapperProvider = jobLogMapperProvider;
        this.triggerMapperProvider = triggerMapperProvider;
        this.redisTemplateProvider = redisTemplateProvider;
        this.applicationContext = applicationContext;

        if (handlers != null) {
            for (JobHandler handler : handlers) {
                handlerRegistry.put(handler.getHandlerName(), handler);
            }
        }

        this.taskScheduler = new ThreadPoolTaskScheduler();
        this.taskScheduler.setPoolSize(10);
        this.taskScheduler.setThreadNamePrefix("saas-sched-worker-");
        this.taskScheduler.initialize();
    }

    @EventListener(ApplicationReadyEvent.class)
    @TenantIgnore(reason = "Bootstrap scheduled jobs across all tenants on startup")
    public void onApplicationReady() {
        JobMapper mapper = jobMapperProvider.getIfAvailable();
        if (mapper == null) {
            log.info("[JobExecutionEngine] JobMapper not available, skipping scheduler initialization");
            return;
        }

        try {
            List<JobEntity> enabledJobs = mapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<JobEntity>()
                            .eq(JobEntity::getStatus, "ENABLED")
                            .eq(JobEntity::getDeleted, 0)
            );

            log.info("[JobExecutionEngine] 启动扫描，待加载活动定时任务数: {}", enabledJobs.size());
            JobTriggerMapper triggerMapper = triggerMapperProvider != null ? triggerMapperProvider.getIfAvailable() : null;
            for (JobEntity job : enabledJobs) {
                JobTriggerEntity trigger = triggerMapper != null ? triggerMapper.selectPrimaryTriggerByJobId(job.getId()) : null;
                if (trigger != null && trigger.getStatus() != null && !"ENABLED".equalsIgnoreCase(trigger.getStatus())) {
                    log.info("[JobExecutionEngine] 跳过触发器已禁用的任务: jobId={}, code={}", job.getId(), job.getJobCode());
                    continue;
                }
                if (trigger != null && trigger.getCronExpr() != null && !trigger.getCronExpr().isBlank()) {
                    scheduleJob(job, trigger.getCronExpr().trim());
                } else if (trigger != null && trigger.getFixedIntervalSeconds() != null && trigger.getFixedIntervalSeconds() > 0) {
                    scheduleJobFixedInterval(job, trigger.getFixedIntervalSeconds());
                } else {
                    scheduleJob(job, "0 */5 * * * ?");
                }
            }
        } catch (Exception ex) {
            log.warn("[JobExecutionEngine] 初始化定时任务调度异常: {}", ex.getMessage());
        }
    }

    public boolean hasHandler(String handlerName) {
        return handlerRegistry.containsKey(handlerName);
    }

    public List<String> getAvailableHandlerNames() {
        return new ArrayList<>(handlerRegistry.keySet());
    }

    public synchronized void scheduleJob(JobEntity job, String cronExpr) {
        if (job == null || job.getId() == null) {
            return;
        }

        cancelScheduledJob(job.getId());

        try {
            CronTrigger trigger = new CronTrigger(cronExpr);
            ScheduledFuture<?> future = taskScheduler.schedule(() -> executeScheduledWithDistributedLock(job, cronExpr), trigger);

            activeSchedules.put(job.getId(), future);
            log.info("[JobExecutionEngine] 成功注册 Cron 定时任务: jobId={}, code={}, cron={}", job.getId(), job.getJobCode(), cronExpr);
        } catch (Exception ex) {
            log.error("[JobExecutionEngine] 注册任务调度失败: jobId={}, error={}", job.getId(), ex.getMessage());
        }
    }

    public synchronized void scheduleJobFixedInterval(JobEntity job, int intervalSeconds) {
        if (job == null || job.getId() == null || intervalSeconds <= 0) {
            return;
        }
        cancelScheduledJob(job.getId());
        ScheduledFuture<?> future = taskScheduler.scheduleAtFixedRate(
                () -> executeScheduledWithDistributedLock(job, "fixed=" + intervalSeconds + "s"),
                Duration.ofSeconds(intervalSeconds)
        );
        activeSchedules.put(job.getId(), future);
        log.info("[JobExecutionEngine] 成功注册固定频率任务: jobId={}, code={}, interval={}s", job.getId(), job.getJobCode(), intervalSeconds);
    }

    public JobLogEntity executeScheduledWithDistributedLock(JobEntity job, String triggerDesc) {
        String lockKey = SCHED_LOCK_PREFIX + job.getId();
        String lockToken = UUID.randomUUID().toString();
        StringRedisTemplate redis = redisTemplateProvider != null ? redisTemplateProvider.getIfAvailable() : null;
        boolean acquiredRedis = false;
        if (redis != null) {
            try {
                Boolean locked = redis.opsForValue().setIfAbsent(lockKey, lockToken, Duration.ofSeconds(120));
                if (!Boolean.TRUE.equals(locked)) {
                    log.debug("[JobExecutionEngine] 任务已被集群其他实例抢占执行，本节点跳过: jobId={}, code={}", job.getId(), job.getJobCode());
                    return null;
                }
                acquiredRedis = true;
            } catch (Exception ex) {
                log.warn("[JobExecutionEngine] Redis 分布式锁不可用，降级本地执行: {}", ex.getMessage());
            }
        }
        long effectiveTenantId = job.getTenantId() != null && job.getTenantId() > 0 ? job.getTenantId() : 1L;
        try (TenantAccessContextHolder.Scope ignored = TenantAccessContextHolder.openTenant(
                effectiveTenantId, "tenant-" + effectiveTenantId, 0L)) {
            String executionNo = "AUTO-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
            return runJobInternal(job, "SCHEDULED", executionNo, 0, "分布式调度触发 (" + triggerDesc + ")");
        } finally {
            if (acquiredRedis && redis != null) {
                try {
                    redis.execute(UNLOCK_SCRIPT, List.of(lockKey), lockToken);
                } catch (Exception ignored) {
                }
            }
        }
    }

    public synchronized void cancelScheduledJob(Long jobId) {
        ScheduledFuture<?> future = activeSchedules.remove(jobId);
        if (future != null) {
            future.cancel(false);
            log.info("[JobExecutionEngine] 已注销定时任务: jobId={}", jobId);
        }
    }

    /**
     * 真实执行任务，捕获耗时、异常并持久化标准 JobLog
     */
    public JobLogEntity executeJobSynchronously(JobEntity job, String executionType, String executionNo, int retryCount, String remark) {
        return runJobInternal(job, executionType, executionNo, retryCount, remark);
    }

    private JobLogEntity runJobInternal(JobEntity job, String executionType, String executionNo, int retryCount, String remark) {
        JobLogMapper logMapper = jobLogMapperProvider.getIfAvailable();
        LocalDateTime startTime = LocalDateTime.now();
        String traceId = "tr-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        JobLogEntity logEntity = new JobLogEntity();
        logEntity.setTenantId(job.getTenantId() != null ? job.getTenantId() : 1L);
        logEntity.setJobId(job.getId());
        logEntity.setTriggerId(0L);
        logEntity.setExecutionNo(executionNo);
        logEntity.setExecutorId(job.getExecutorId() != null ? job.getExecutorId() : 0L);
        logEntity.setStartedAt(startTime);
        logEntity.setRetryCount(retryCount);
        logEntity.setTraceId(traceId);

        boolean success = false;
        String errorMessage = null;

        try {
            JobHandler handler = resolveHandler(job.getHandlerName());
            if (handler == null) {
                throw new IllegalStateException("未找到已注册的任务处理器: " + job.getHandlerName());
            }

            log.info("[JobExecutionEngine] 开始真实执行任务 [{} - {}], executionNo={}, traceId={}",
                    job.getJobCode(), job.getJobName(), executionNo, traceId);

            handler.execute(job.getRemark() != null ? job.getRemark() : remark);
            success = true;
            log.info("[JobExecutionEngine] 任务执行成功 [{}], executionNo={}", job.getJobCode(), executionNo);
        } catch (Throwable ex) {
            success = false;
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            String fullStack = sw.toString();
            errorMessage = fullStack.length() > 1900 ? fullStack.substring(0, 1900) : fullStack;
            log.error("[JobExecutionEngine] 任务执行发生异常 [{}], executionNo={}, error={}",
                    job.getJobCode(), executionNo, ex.getMessage());
        }

        LocalDateTime finishedTime = LocalDateTime.now();
        logEntity.setFinishedAt(finishedTime);
        logEntity.setSuccess(success);
        logEntity.setRunStatus(success ? ("RETRY".equals(executionType) ? "RETRY_SUCCESS" : "SUCCESS") : "FAILED");
        logEntity.setErrorMessage(errorMessage != null ? errorMessage : (remark != null && !remark.isBlank() ? remark : "执行成功"));

        if (logMapper != null) {
            try {
                logMapper.insert(logEntity);
            } catch (Exception e) {
                log.error("[JobExecutionEngine] 持久化任务执行日志失败: {}", e.getMessage());
            }
        }

        return logEntity;
    }

    private JobHandler resolveHandler(String handlerName) {
        if (handlerName == null || handlerName.isBlank()) {
            return null;
        }

        JobHandler direct = handlerRegistry.get(handlerName);
        if (direct != null) {
            return direct;
        }

        try {
            Object bean = applicationContext.getBean(handlerName);
            if (bean instanceof JobHandler jh) {
                handlerRegistry.put(handlerName, jh);
                return jh;
            }
        } catch (Exception ignored) {
        }

        return null;
    }
}
