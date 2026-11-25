package com.saasbasics.platform.modules.iam.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DynamicMenuGrantMapper {
    int registerActions(@Param("tenantId") long tenantId, @Param("code") String code,
                        @Param("name") String name, @Param("actorId") long actorId);

    int grantToTenantAdministrators(@Param("tenantId") long tenantId, @Param("menuId") long menuId,
                                    @Param("actorId") long actorId);
}
