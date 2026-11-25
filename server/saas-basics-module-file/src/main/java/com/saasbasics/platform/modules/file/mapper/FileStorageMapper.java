package com.saasbasics.platform.modules.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.file.dto.FileStorageResponse;
import com.saasbasics.platform.modules.file.entity.FileStorageEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FileStorageMapper extends BaseMapper<FileStorageEntity> {

    List<FileStorageResponse> selectStorageList();

    FileStorageResponse selectStorageById(@Param("id") Long id);
}
