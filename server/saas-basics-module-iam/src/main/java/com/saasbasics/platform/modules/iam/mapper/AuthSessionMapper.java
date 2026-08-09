package com.saasbasics.platform.modules.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.iam.entity.AuthSessionEntity;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AuthSessionMapper extends BaseMapper<AuthSessionEntity> {

    @Select("""
            SELECT *
            FROM iam_session
            WHERE access_token_hash = #{tokenHash}
              AND status = 'ONLINE'
              AND deleted = 0
              AND expire_at > #{now}
            LIMIT 1
            """)
    AuthSessionEntity selectOnlineByTokenHash(@Param("tokenHash") String tokenHash, @Param("now") LocalDateTime now);
}
