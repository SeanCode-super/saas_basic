package com.saasbasics.platform.modules.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.iam.dto.ApiResourceResponse;
import com.saasbasics.platform.modules.iam.entity.ApiResourceEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ApiResourceMapper extends BaseMapper<ApiResourceEntity> {

    @Select("""
            SELECT
              id,
              tenant_id AS tenantId,
              resource_code AS resourceCode,
              resource_name AS resourceName,
              http_method AS httpMethod,
              url_pattern AS urlPattern,
              auth_required AS authRequired,
              status,
              remark
            FROM iam_api_resource
            WHERE deleted = 0
            ORDER BY id DESC
            """)
    List<ApiResourceResponse> selectApiResourceList();

    @Select("""
            SELECT
              id,
              tenant_id AS tenantId,
              resource_code AS resourceCode,
              resource_name AS resourceName,
              http_method AS httpMethod,
              url_pattern AS urlPattern,
              auth_required AS authRequired,
              status,
              remark
            FROM iam_api_resource
            WHERE id = #{id}
              AND deleted = 0
            """)
    ApiResourceResponse selectApiResourceById(@Param("id") Long id);
}
