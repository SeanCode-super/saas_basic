package com.saasbasics.platform.modules.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.iam.entity.UserPersonBindingEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserPersonBindingMapper extends BaseMapper<UserPersonBindingEntity> {

    @Select("""
            SELECT * FROM iam_user_person_binding
            WHERE tenant_id = #{tenantId} AND user_id = #{userId}
              AND status = 'ACTIVE' AND deleted = 0
              AND valid_from <= #{at}
              AND (valid_to IS NULL OR valid_to > #{at})
            ORDER BY valid_from DESC, id DESC LIMIT 1
            """)
    UserPersonBindingEntity selectEffectiveByUser(@Param("tenantId") Long tenantId,
                                                  @Param("userId") Long userId,
                                                  @Param("at") LocalDateTime at);

    @Select("""
            SELECT * FROM iam_user_person_binding
            WHERE tenant_id = #{tenantId} AND person_public_id = #{personPublicId}
              AND status = 'ACTIVE' AND deleted = 0
              AND valid_from <= #{at}
              AND (valid_to IS NULL OR valid_to > #{at})
            ORDER BY valid_from DESC, id DESC
            """)
    List<UserPersonBindingEntity> selectEffectiveByPerson(@Param("tenantId") Long tenantId,
                                                           @Param("personPublicId") String personPublicId,
                                                           @Param("at") LocalDateTime at);
}
