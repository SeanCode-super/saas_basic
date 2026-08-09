package com.saasbasics.platform.modules.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.file.dto.FileUploadSessionResponse;
import com.saasbasics.platform.modules.file.entity.FileUploadSessionEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FileUploadSessionMapper extends BaseMapper<FileUploadSessionEntity> {

    @Select("""
            SELECT
              id,
              tenant_id AS tenantId,
              bucket_id AS bucketId,
              storage_id AS storageId,
              session_code AS sessionCode,
              object_key AS objectKey,
              file_name AS fileName,
              file_size AS fileSize,
              upload_mode AS uploadMode,
              part_count AS partCount,
              owner_user_id AS ownerUserId,
              status,
              expire_at AS expireAt,
              completed_at AS completedAt
            FROM file_upload_session
            WHERE deleted = 0
            ORDER BY created_at DESC, id DESC
            """)
    List<FileUploadSessionResponse> selectSessionList();
}
