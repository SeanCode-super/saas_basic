package com.saasbasics.platform.modules.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.iam.entity.PortalTerminalEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PortalTerminalMapper extends BaseMapper<PortalTerminalEntity> {

    @Select("""
            SELECT *
            FROM iam_portal_terminal
            WHERE portal_client_id = #{portalClientId}
              AND terminal_code = #{terminalCode}
              AND deleted = 0
            LIMIT 1
            """)
    PortalTerminalEntity selectByClientAndTerminalCode(@Param("portalClientId") Long portalClientId,
                                                       @Param("terminalCode") String terminalCode);

    @Select("""
            SELECT *
            FROM iam_portal_terminal
            WHERE portal_client_id = #{portalClientId}
              AND is_default = 1
              AND deleted = 0
            ORDER BY id ASC
            LIMIT 1
            """)
    PortalTerminalEntity selectDefaultTerminal(@Param("portalClientId") Long portalClientId);

    @Select("""
            SELECT *
            FROM iam_portal_terminal
            WHERE deleted = 0
            ORDER BY portal_client_id ASC, is_default DESC, id ASC
            """)
    java.util.List<PortalTerminalEntity> selectTerminalList();
}
