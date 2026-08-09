package com.saasbasics.platform.modules.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.iam.entity.PortalClientEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PortalClientMapper extends BaseMapper<PortalClientEntity> {

    @Select("""
            SELECT *
            FROM iam_portal_client
            WHERE client_id = #{clientId}
              AND deleted = 0
            LIMIT 1
            """)
    PortalClientEntity selectByClientId(@Param("clientId") String clientId);
}
