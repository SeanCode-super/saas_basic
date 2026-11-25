package com.saasbasics.platform.modules.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.iam.dto.MenuPermissionResponse;
import com.saasbasics.platform.modules.iam.entity.MenuPermissionEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MenuPermissionMapper extends BaseMapper<MenuPermissionEntity> {

    int deleteSubjectGrants(@Param("tenantId") Long tenantId,
                            @Param("subjectType") String subjectType,
                            @Param("subjectValue") String subjectValue, @Param("actorId") Long actorId);

    List<MenuPermissionResponse> selectPermissionList();

    MenuPermissionResponse selectPermissionById(@Param("id") Long id);
}
