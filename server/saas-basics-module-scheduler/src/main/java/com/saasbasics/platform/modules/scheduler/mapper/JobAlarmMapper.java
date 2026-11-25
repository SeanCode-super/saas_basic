package com.saasbasics.platform.modules.scheduler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.scheduler.dto.JobAlarmResponse;
import com.saasbasics.platform.modules.scheduler.entity.JobAlarmEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface JobAlarmMapper extends BaseMapper<JobAlarmEntity> {

    List<JobAlarmResponse> selectAlarmList();

    JobAlarmResponse selectAlarmById(@Param("id") Long id);
}
