package com.saasbasics.platform.modules.scheduler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.scheduler.dto.JobLogResponse;
import com.saasbasics.platform.modules.scheduler.entity.JobLogEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface JobLogMapper extends BaseMapper<JobLogEntity> {

    @Select("""
            SELECT
              id,
              tenant_id AS tenantId,
              job_id AS jobId,
              execution_no AS executionNo,
              executor_id AS executorId,
              started_at AS startedAt,
              finished_at AS finishedAt,
              run_status AS runStatus,
              success,
              retry_count AS retryCount,
              error_message AS errorMessage,
              trace_id AS traceId
            FROM sched_job_log
            ORDER BY id DESC
            """)
    List<JobLogResponse> selectLogList();
}
