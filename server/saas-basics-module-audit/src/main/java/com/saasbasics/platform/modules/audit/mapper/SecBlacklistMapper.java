package com.saasbasics.platform.modules.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.audit.entity.SecBlacklistEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SecBlacklistMapper extends BaseMapper<SecBlacklistEntity> {
}
