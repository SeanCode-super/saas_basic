package com.saasbasics.platform.modules.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.iam.dto.RoleResponse;
import com.saasbasics.platform.modules.iam.entity.RoleEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RoleMapper extends BaseMapper<RoleEntity> {

    List<RoleResponse> selectRoleList();

    RoleResponse selectRoleById(@Param("id") Long id);

    RoleEntity selectEntityById(@Param("id") Long id);
}
