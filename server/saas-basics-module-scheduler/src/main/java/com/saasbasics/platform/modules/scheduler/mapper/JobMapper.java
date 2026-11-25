package com.saasbasics.platform.modules.scheduler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.scheduler.dto.JobResponse;
import com.saasbasics.platform.modules.scheduler.entity.JobEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface JobMapper extends BaseMapper<JobEntity> {

    List<JobResponse> selectJobList();

    JobResponse selectJobById(@Param("id") Long id);
}
