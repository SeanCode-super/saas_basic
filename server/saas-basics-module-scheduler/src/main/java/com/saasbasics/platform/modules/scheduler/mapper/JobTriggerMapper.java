package com.saasbasics.platform.modules.scheduler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.scheduler.entity.JobTriggerEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface JobTriggerMapper extends BaseMapper<JobTriggerEntity> {

    @Select("""
            SELECT *
            FROM sched_job_trigger
            WHERE job_id = #{jobId}
              AND deleted = 0
            ORDER BY id DESC
            LIMIT 1
            """)
    JobTriggerEntity selectPrimaryTriggerByJobId(@Param("jobId") Long jobId);
}
