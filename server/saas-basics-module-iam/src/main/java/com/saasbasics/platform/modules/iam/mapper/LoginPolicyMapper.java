package com.saasbasics.platform.modules.iam.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.iam.dto.LoginPolicyResponse;
import com.saasbasics.platform.modules.iam.entity.LoginPolicyEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LoginPolicyMapper extends BaseMapper<LoginPolicyEntity> {

    default LoginPolicyEntity selectActivePolicy(Long tenantId) {
        return selectOne(new LambdaQueryWrapper<LoginPolicyEntity>()
                .in(LoginPolicyEntity::getTenantId, 0L, tenantId)
                .eq(LoginPolicyEntity::getStatus, "ENABLED")
                .orderByDesc(LoginPolicyEntity::getTenantId)
                .orderByDesc(LoginPolicyEntity::getId)
                .last("LIMIT 1"));
    }

    List<LoginPolicyResponse> selectLoginPolicyList();

    LoginPolicyResponse selectLoginPolicyById(@Param("id") Long id);
}
