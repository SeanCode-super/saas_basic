package com.saasbasics.platform.modules.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.iam.entity.RoleApiEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RoleApiMapper extends BaseMapper<RoleApiEntity> {

    List<Long> selectApiResourceIdsByRoleId(@Param("tenantId") Long tenantId, @Param("roleId") Long roleId);

    List<String> selectPermissionCodesByUserId(@Param("tenantId") Long tenantId,
                                               @Param("userId") Long userId,
                                               @Param("now") LocalDateTime now);

    int deleteAssignments(@Param("tenantId") Long tenantId, @Param("roleId") Long roleId, @Param("actorId") Long actorId);
}
