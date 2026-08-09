package com.saasbasics.platform.modules.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.file.dto.FileObjectResponse;
import com.saasbasics.platform.modules.file.entity.FileObjectEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FileObjectMapper extends BaseMapper<FileObjectEntity> {

    @Select("""
            SELECT
              id,
              tenant_id AS tenantId,
              bucket_id AS bucketId,
              object_key AS objectKey,
              file_name AS fileName,
              file_ext AS fileExt,
              content_type AS contentType,
              file_size AS fileSize,
              visibility,
              biz_type AS bizType,
              version_no AS versionNo,
              status,
              storage_path AS storagePath,
              remark
            FROM file_object
            WHERE deleted = 0
            ORDER BY created_at DESC, id DESC
            """)
    List<FileObjectResponse> selectObjectList();
}
