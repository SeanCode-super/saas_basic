package com.saasbasics.platform.modules.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.iam.entity.UserPersonBindingEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserPersonBindingMapper extends BaseMapper<UserPersonBindingEntity> {

    UserPersonBindingEntity selectEffectiveByUser(@Param("tenantId") Long tenantId,
                                                  @Param("userId") Long userId,
                                                  @Param("at") LocalDateTime at);

    List<UserPersonBindingEntity> selectEffectiveByPerson(@Param("tenantId") Long tenantId,
                                                           @Param("personPublicId") String personPublicId,
                                                           @Param("at") LocalDateTime at);
}
