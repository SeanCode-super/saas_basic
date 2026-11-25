package com.saasbasics.platform.modules.scheduler.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.scheduler.entity.JobTriggerEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface JobTriggerMapper extends BaseMapper<JobTriggerEntity> {

    default JobTriggerEntity selectPrimaryTriggerByJobId(Long jobId) {
        return selectOne(new LambdaQueryWrapper<JobTriggerEntity>()
                .eq(JobTriggerEntity::getJobId, jobId)
                .orderByDesc(JobTriggerEntity::getId)
                .last("LIMIT 1"));
    }
}
