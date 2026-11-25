package com.saasbasics.platform.modules.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.file.dto.FileUploadSessionResponse;
import com.saasbasics.platform.modules.file.entity.FileUploadSessionEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.saasbasics.platform.common.auth.DataScope;
import java.math.BigInteger;

@Mapper
public interface FileUploadSessionMapper extends BaseMapper<FileUploadSessionEntity> {

    @DataScope(ignore = true)
    BigInteger allocatedBytes(@Param("tenantId") Long tenantId);

    @DataScope(ignore = true)
    long claimedObjectKey(@Param("tenantId") Long tenantId, @Param("storageId") Long storageId,
                          @Param("objectKey") String objectKey);

    record RestoreCandidate(Long storageId, String objectKey, long fileSize, long billedExportBytes) {}

    @DataScope(ignore = true)
    RestoreCandidate restoreCandidate(@Param("tenantId") Long tenantId, @Param("fileId") Long fileId);

    record ExpiredSession(Long id, Long tenantId, String tenantCode) {}

    @DataScope(ignore = true)
    List<ExpiredSession> listExpiredSessions();

    List<FileUploadSessionResponse> selectSessionList();
}
