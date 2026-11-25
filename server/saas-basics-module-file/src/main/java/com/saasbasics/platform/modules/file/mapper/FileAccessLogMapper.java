package com.saasbasics.platform.modules.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.file.dto.FileAccessLogResponse;
import com.saasbasics.platform.modules.file.entity.FileAccessLogEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FileAccessLogMapper extends BaseMapper<FileAccessLogEntity> {

    List<FileAccessLogResponse> selectLogList();
}
