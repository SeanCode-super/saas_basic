package com.saasbasics.platform.modules.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.iam.dto.DataScopeResponse;
import com.saasbasics.platform.modules.iam.entity.DataScopeEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DataScopeMapper extends BaseMapper<DataScopeEntity> {

    @Select("""
            SELECT
              id,
              tenant_id AS tenantId,
              scope_code AS scopeCode,
              scope_name AS scopeName,
              scope_type AS scopeType,
              CAST(scope_rule_json AS CHAR) AS scopeRuleJson,
              status,
              remark
            FROM iam_data_scope
            WHERE deleted = 0
            ORDER BY id ASC
            """)
    List<DataScopeResponse> selectDataScopeList();

    @Select("""
            SELECT
              id,
              tenant_id AS tenantId,
              scope_code AS scopeCode,
              scope_name AS scopeName,
              scope_type AS scopeType,
              CAST(scope_rule_json AS CHAR) AS scopeRuleJson,
              status,
              remark
            FROM iam_data_scope
            WHERE id = #{id}
              AND deleted = 0
            """)
    DataScopeResponse selectDataScopeById(@Param("id") Long id);
}
