package com.saasbasics.platform.modules.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.file.entity.ExportTaskEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ExportTaskMapper extends BaseMapper<ExportTaskEntity> {
    void notifyCompleted(@Param("tenantId") Long tenantId, @Param("userId") Long userId, @Param("title") String title,
                         @Param("content") String content, @Param("taskId") String taskId);
}
