package com.saasbasics.platform.modules.iam.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.iam.entity.PortalTerminalEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PortalTerminalMapper extends BaseMapper<PortalTerminalEntity> {

    default PortalTerminalEntity selectByClientAndTerminalCode(Long portalClientId,
                                                       String terminalCode) {
        return selectOne(new LambdaQueryWrapper<PortalTerminalEntity>()
                .eq(PortalTerminalEntity::getPortalClientId, portalClientId)
                .eq(PortalTerminalEntity::getTerminalCode, terminalCode)
                .last("LIMIT 1"));
    }

    default PortalTerminalEntity selectDefaultTerminal(Long portalClientId) {
        return selectOne(new LambdaQueryWrapper<PortalTerminalEntity>()
                .eq(PortalTerminalEntity::getPortalClientId, portalClientId)
                .eq(PortalTerminalEntity::getIsDefault, true)
                .orderByAsc(PortalTerminalEntity::getId)
                .last("LIMIT 1"));
    }

    default java.util.List<PortalTerminalEntity> selectTerminalList() {
        return selectList(new LambdaQueryWrapper<PortalTerminalEntity>()
                .orderByAsc(PortalTerminalEntity::getPortalClientId)
                .orderByDesc(PortalTerminalEntity::getIsDefault)
                .orderByAsc(PortalTerminalEntity::getId));
    }
}
