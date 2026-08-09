package com.saasbasics.platform.modules.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.iam.dto.DataPermissionRuleResponse;
import com.saasbasics.platform.modules.iam.entity.DataPermissionRuleEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DataPermissionRuleMapper extends BaseMapper<DataPermissionRuleEntity> {

    @Select("""
            SELECT
              id,
              tenant_id AS tenantId,
              resource_code AS resourceCode,
              resource_name AS resourceName,
              subject_type AS subjectType,
              subject_value AS subjectValue,
              scope_type AS scopeType,
              CAST(config_json AS CHAR) AS configJson,
              status,
              remark
            FROM iam_data_permission_rule
            WHERE deleted = 0
            ORDER BY id DESC
            """)
    List<DataPermissionRuleResponse> selectRuleList();

    @Select("""
            SELECT
              id,
              tenant_id AS tenantId,
              resource_code AS resourceCode,
              resource_name AS resourceName,
              subject_type AS subjectType,
              subject_value AS subjectValue,
              scope_type AS scopeType,
              CAST(config_json AS CHAR) AS configJson,
              status,
              remark
            FROM iam_data_permission_rule
            WHERE id = #{id}
              AND deleted = 0
            """)
    DataPermissionRuleResponse selectRuleById(@Param("id") Long id);
}
