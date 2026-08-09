package com.saasbasics.platform.modules.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.iam.entity.PasswordHistoryEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PasswordHistoryMapper extends BaseMapper<PasswordHistoryEntity> {
}
