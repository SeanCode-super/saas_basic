package com.saasbasics.platform.modules.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.file.dto.FileUploadRouteResponse;
import com.saasbasics.platform.modules.file.entity.FileUploadRouteEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FileUploadRouteMapper extends BaseMapper<FileUploadRouteEntity> {

    @Select("""
            SELECT
              id,
              tenant_id AS tenantId,
              storage_id AS storageId,
              biz_type AS bizType,
              route_name AS routeName,
              upload_mode AS uploadMode,
              target_dir AS targetDir,
              path_pattern AS pathPattern,
              max_file_mb AS maxFileMb,
              allowed_exts AS allowedExts,
              priority,
              status,
              remark
            FROM file_upload_route
            WHERE deleted = 0
            ORDER BY priority ASC, id DESC
            """)
    List<FileUploadRouteResponse> selectRouteList();

    @Select("""
            SELECT
              id,
              tenant_id AS tenantId,
              storage_id AS storageId,
              biz_type AS bizType,
              route_name AS routeName,
              upload_mode AS uploadMode,
              target_dir AS targetDir,
              path_pattern AS pathPattern,
              max_file_mb AS maxFileMb,
              allowed_exts AS allowedExts,
              priority,
              status,
              remark
            FROM file_upload_route
            WHERE id = #{id}
              AND deleted = 0
            """)
    FileUploadRouteResponse selectRouteById(@Param("id") Long id);
}
