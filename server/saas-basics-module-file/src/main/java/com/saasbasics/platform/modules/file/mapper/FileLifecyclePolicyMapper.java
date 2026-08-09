package com.saasbasics.platform.modules.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.file.dto.FileLifecyclePolicyResponse;
import com.saasbasics.platform.modules.file.entity.FileLifecyclePolicyEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FileLifecyclePolicyMapper extends BaseMapper<FileLifecyclePolicyEntity> {

    @Select("""
            SELECT
              id,
              tenant_id AS tenantId,
              policy_code AS policyCode,
              policy_name AS policyName,
              file_scope AS fileScope,
              retention_days AS retentionDays,
              archive_after_days AS archiveAfterDays,
              delete_after_days AS deleteAfterDays,
              deduplicate_enabled AS deduplicateEnabled,
              version_retention_count AS versionRetentionCount,
              status,
              remark
            FROM file_lifecycle_policy
            WHERE deleted = 0
            ORDER BY id DESC
            """)
    List<FileLifecyclePolicyResponse> selectPolicyList();

    @Select("""
            SELECT
              id,
              tenant_id AS tenantId,
              policy_code AS policyCode,
              policy_name AS policyName,
              file_scope AS fileScope,
              retention_days AS retentionDays,
              archive_after_days AS archiveAfterDays,
              delete_after_days AS deleteAfterDays,
              deduplicate_enabled AS deduplicateEnabled,
              version_retention_count AS versionRetentionCount,
              status,
              remark
            FROM file_lifecycle_policy
            WHERE id = #{id}
              AND deleted = 0
            """)
    FileLifecyclePolicyResponse selectPolicyById(@Param("id") Long id);
}
