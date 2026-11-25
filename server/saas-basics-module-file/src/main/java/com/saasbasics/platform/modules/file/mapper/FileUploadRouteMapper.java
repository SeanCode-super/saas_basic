package com.saasbasics.platform.modules.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.file.dto.FileUploadRouteResponse;
import com.saasbasics.platform.modules.file.entity.FileUploadRouteEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FileUploadRouteMapper extends BaseMapper<FileUploadRouteEntity> {

    List<FileUploadRouteResponse> selectRouteList();

    FileUploadRouteResponse selectRouteById(@Param("id") Long id);
}
