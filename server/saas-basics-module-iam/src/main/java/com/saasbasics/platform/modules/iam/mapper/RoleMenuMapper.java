package com.saasbasics.platform.modules.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.iam.entity.RoleMenuEntity;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RoleMenuMapper extends BaseMapper<RoleMenuEntity> {

    Set<Long> selectMenuIdsByRoleIds(@Param("tenantId") Long tenantId, @Param("roleIds") Collection<Long> roleIds);

    List<String> selectButtonCodesByRoleIds(@Param("tenantId") Long tenantId, @Param("roleIds") Collection<Long> roleIds);

    List<Long> selectMenuIdsByRoleId(@Param("tenantId") Long tenantId, @Param("roleId") Long roleId);

    int revokeByRoleId(@Param("tenantId") Long tenantId, @Param("roleId") Long roleId, @Param("actorId") Long actorId);
}
