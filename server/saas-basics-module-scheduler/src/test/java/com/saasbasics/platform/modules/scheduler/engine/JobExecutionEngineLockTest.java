package com.saasbasics.platform.modules.scheduler.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saasbasics.platform.modules.scheduler.entity.JobEntity;
import com.saasbasics.platform.modules.scheduler.entity.JobLogEntity;
import com.saasbasics.platform.modules.scheduler.handler.JobHandler;
import com.saasbasics.platform.modules.scheduler.mapper.JobLogMapper;
import com.saasbasics.platform.modules.scheduler.mapper.JobMapper;
import com.saasbasics.platform.modules.scheduler.mapper.JobTriggerMapper;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class JobExecutionEngineLockTest {

    @Test
    @SuppressWarnings("unchecked")
    void skipsExecutionWhenRedisClusterLockHeldByAnotherNode() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);

        AtomicInteger counter = new AtomicInteger();
        JobHandler handler = new JobHandler() {
            @Override
            public String getHandlerName() {
                return "demoHandler";
            }

            @Override
            public void execute(String params) {
                counter.incrementAndGet();
            }
        };

        StaticListableBeanFactory bf = new StaticListableBeanFactory();
        bf.addBean("redis", redis);

        JobExecutionEngine engine = new JobExecutionEngine(
                bf.getBeanProvider(JobMapper.class),
                bf.getBeanProvider(JobLogMapper.class),
                bf.getBeanProvider(JobTriggerMapper.class),
                bf.getBeanProvider(StringRedisTemplate.class),
                new StaticApplicationContext(),
                List.of(handler)
        );

        JobEntity job = new JobEntity();
        job.setId(99L);
        job.setTenantId(1L);
        job.setJobCode("DEMO_JOB");
        job.setJobName("Demo");
        job.setHandlerName("demoHandler");

        // Contended node: Redis returns false -> skipped
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(Boolean.FALSE);
        JobLogEntity skipped = engine.executeScheduledWithDistributedLock(job, "0/30 * * * * ?");
        assertThat(skipped).isNull();
        assertThat(counter.get()).isZero();

        // Lock acquired: executes and releases lock cleanly
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(Boolean.TRUE);
        JobLogEntity executed = engine.executeScheduledWithDistributedLock(job, "0/30 * * * * ?");
        assertThat(executed).isNotNull();
        assertThat(executed.getRunStatus()).isEqualTo("SUCCESS");
        assertThat(counter.get()).isEqualTo(1);
        verify(redis).execute(any(), anyList(), anyString());
    }
}
