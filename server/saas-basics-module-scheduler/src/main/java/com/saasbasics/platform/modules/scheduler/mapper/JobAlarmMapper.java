package com.saasbasics.platform.modules.scheduler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.scheduler.dto.JobAlarmResponse;
import com.saasbasics.platform.modules.scheduler.entity.JobAlarmEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface JobAlarmMapper extends BaseMapper<JobAlarmEntity> {

    @Select("""
            SELECT
              id,
              tenant_id AS tenantId,
              job_id AS jobId,
              alarm_code AS alarmCode,
              alarm_name AS alarmName,
              channel_type AS channelType,
              trigger_rule AS triggerRule,
              CAST(receiver_json AS CHAR) AS receiverJson,
              template_code AS templateCode,
              silence_minutes AS silenceMinutes,
              status,
              remark
            FROM sched_job_alarm
            WHERE deleted = 0
            ORDER BY job_id ASC, id ASC
            """)
    List<JobAlarmResponse> selectAlarmList();

    @Select("""
            SELECT
              id,
              tenant_id AS tenantId,
              job_id AS jobId,
              alarm_code AS alarmCode,
              alarm_name AS alarmName,
              channel_type AS channelType,
              trigger_rule AS triggerRule,
              CAST(receiver_json AS CHAR) AS receiverJson,
              template_code AS templateCode,
              silence_minutes AS silenceMinutes,
              status,
              remark
            FROM sched_job_alarm
            WHERE id = #{id}
              AND deleted = 0
            """)
    JobAlarmResponse selectAlarmById(@Param("id") Long id);
}
