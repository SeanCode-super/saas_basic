package com.saasbasics.platform.modules.scheduler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.scheduler.dto.JobLogResponse;
import com.saasbasics.platform.modules.scheduler.entity.JobLogEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface JobLogMapper extends BaseMapper<JobLogEntity> {

    List<JobLogResponse> selectLogList();
}
