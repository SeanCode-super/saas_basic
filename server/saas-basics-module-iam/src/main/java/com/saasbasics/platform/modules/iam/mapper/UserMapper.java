package com.saasbasics.platform.modules.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.common.auth.DataPermissionSqlSpec;
import com.saasbasics.platform.common.auth.DataScope;
import com.saasbasics.platform.modules.iam.dto.UserResponse;
import com.saasbasics.platform.modules.iam.entity.UserEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {

    UserEntity selectByTenantAndPublicId(@Param("tenantId") Long tenantId, @Param("publicId") String publicId);

    @DataScope(ignore = true)
    List<UserResponse> selectUserList(@Param("spec") DataPermissionSqlSpec spec);

    UserResponse selectUserById(@Param("id") Long id);

    UserEntity selectByTenantAndUsername(@Param("tenantId") Long tenantId, @Param("username") String username);

    List<String> selectRoleNamesByUserId(@Param("userId") Long userId);

    List<String> selectRoleNamesByUserIdAndTenantId(@Param("userId") Long userId, @Param("tenantId") Long tenantId);
}
