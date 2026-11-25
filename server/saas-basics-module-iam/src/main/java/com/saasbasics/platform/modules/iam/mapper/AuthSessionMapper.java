package com.saasbasics.platform.modules.iam.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.iam.entity.AuthSessionEntity;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthSessionMapper extends BaseMapper<AuthSessionEntity> {

    default AuthSessionEntity selectOnlineByTokenHash(String tokenHash, LocalDateTime now) {
        return selectOne(new LambdaQueryWrapper<AuthSessionEntity>()
                .eq(AuthSessionEntity::getAccessTokenHash, tokenHash)
                .eq(AuthSessionEntity::getStatus, "ONLINE")
                .gt(AuthSessionEntity::getExpireAt, now)
                .last("LIMIT 1"));
    }
}
