package com.saasbasics.platform.modules.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.common.auth.DataScope;
import com.saasbasics.platform.modules.file.dto.FileObjectResponse;
import com.saasbasics.platform.modules.file.entity.FileObjectEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@DataScope(resource = "file:object", orgColumn = "", unitColumn = "", userColumn = "created_by")
public interface FileObjectMapper extends BaseMapper<FileObjectEntity> {

    List<FileObjectResponse> selectObjectList();
}
