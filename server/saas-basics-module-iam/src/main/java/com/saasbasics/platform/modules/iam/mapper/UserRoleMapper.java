package com.saasbasics.platform.modules.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.iam.entity.UserRoleEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserRoleMapper extends BaseMapper<UserRoleEntity> {

    List<Long> selectRoleIdsByUserId(@Param("tenantId") Long tenantId,
                                     @Param("userId") Long userId,
                                     @Param("now") LocalDateTime now);

    int deleteAssignments(@Param("tenantId") Long tenantId, @Param("userId") Long userId, @Param("actorId") Long actorId);

    List<Long> selectUserIdsByRoleId(@Param("tenantId") Long tenantId,
                                     @Param("roleId") Long roleId,
                                     @Param("now") LocalDateTime now);

    int deleteRoleAssignments(@Param("tenantId") Long tenantId, @Param("roleId") Long roleId, @Param("actorId") Long actorId);
}
