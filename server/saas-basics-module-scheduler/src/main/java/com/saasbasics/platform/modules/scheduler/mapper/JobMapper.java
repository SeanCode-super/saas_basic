package com.saasbasics.platform.modules.scheduler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.scheduler.dto.JobResponse;
import com.saasbasics.platform.modules.scheduler.entity.JobEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface JobMapper extends BaseMapper<JobEntity> {

    @Select("""
            SELECT
              id,
              tenant_id AS tenantId,
              job_code AS code,
              job_name AS name,
              job_type AS jobType,
              executor_id AS executorId,
              handler_name AS handlerName,
              status,
              remark
            FROM sched_job
            WHERE deleted = 0
            ORDER BY id DESC
            """)
    List<JobResponse> selectJobList();

    @Select("""
            SELECT
              id,
              tenant_id AS tenantId,
              job_code AS code,
              job_name AS name,
              job_type AS jobType,
              executor_id AS executorId,
              handler_name AS handlerName,
              status,
              remark
            FROM sched_job
            WHERE id = #{id}
              AND deleted = 0
            """)
    JobResponse selectJobById(@Param("id") Long id);
}
