package com.saasbasics.platform.modules.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.file.dto.FileAccessLogResponse;
import com.saasbasics.platform.modules.file.entity.FileAccessLogEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FileAccessLogMapper extends BaseMapper<FileAccessLogEntity> {

    @Select("""
            SELECT
              id,
              tenant_id AS tenantId,
              file_id AS fileId,
              access_type AS accessType,
              operator_user_id AS operatorUserId,
              operator_ip AS operatorIp,
              success,
              occurred_at AS occurredAt,
              remark
            FROM file_access_log
            ORDER BY occurred_at DESC, id DESC
            LIMIT 200
            """)
    List<FileAccessLogResponse> selectLogList();
}
