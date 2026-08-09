package com.saasbasics.platform.modules.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.file.dto.FileStorageResponse;
import com.saasbasics.platform.modules.file.entity.FileStorageEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FileStorageMapper extends BaseMapper<FileStorageEntity> {

    @Select("""
            SELECT
              id,
              tenant_id AS tenantId,
              storage_code AS storageCode,
              storage_name AS storageName,
              storage_type AS storageType,
              endpoint,
              bucket_default AS bucketDefault,
              public_base_url AS publicBaseUrl,
              status,
              remark
            FROM file_storage
            WHERE deleted = 0
            ORDER BY id DESC
            """)
    List<FileStorageResponse> selectStorageList();

    @Select("""
            SELECT
              id,
              tenant_id AS tenantId,
              storage_code AS storageCode,
              storage_name AS storageName,
              storage_type AS storageType,
              endpoint,
              bucket_default AS bucketDefault,
              public_base_url AS publicBaseUrl,
              status,
              remark
            FROM file_storage
            WHERE id = #{id}
              AND deleted = 0
            """)
    FileStorageResponse selectStorageById(@Param("id") Long id);
}
