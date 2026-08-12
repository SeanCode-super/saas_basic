package com.saasbasics.platform.modules.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.iam.entity.PortalClientEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    @Select("""
            SELECT *
            FROM iam_portal_client
            WHERE is_default = 1
              AND status = 'ENABLED'
              AND deleted = 0
            LIMIT 1
            """)
    PortalClientEntity selectDefaultClient();

    @Update("""
            UPDATE iam_portal_client
            SET is_default = 0
            WHERE is_default = 1
              AND deleted = 0
              AND (#{currentClientId} IS NULL OR id != #{currentClientId})
            """)
    int clearDefaultClients(@Param("currentClientId") Long currentClientId);
}
