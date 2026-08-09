package com.saasbasics.platform.modules.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.iam.entity.UserRoleEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserRoleMapper extends BaseMapper<UserRoleEntity> {

    @Select("""
            SELECT role_id
            FROM iam_user_role
            WHERE tenant_id = #{tenantId}
              AND user_id = #{userId}
              AND deleted = 0
              AND (expire_at IS NULL OR expire_at > #{now})
            ORDER BY id DESC
            """)
    List<Long> selectRoleIdsByUserId(@Param("tenantId") Long tenantId,
                                     @Param("userId") Long userId,
                                     @Param("now") LocalDateTime now);

    @Delete("""
            DELETE FROM iam_user_role
            WHERE tenant_id = #{tenantId}
              AND user_id = #{userId}
            """)
    int deleteAssignments(@Param("tenantId") Long tenantId, @Param("userId") Long userId);
}
