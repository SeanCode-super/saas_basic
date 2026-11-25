package com.saasbasics.platform.modules.iam.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.iam.dto.PasswordPolicyResponse;
import com.saasbasics.platform.modules.iam.entity.PasswordPolicyEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PasswordPolicyMapper extends BaseMapper<PasswordPolicyEntity> {

    default PasswordPolicyEntity selectActivePolicy(Long tenantId) {
        return selectOne(new LambdaQueryWrapper<PasswordPolicyEntity>()
                .in(PasswordPolicyEntity::getTenantId, 0L, tenantId)
                .eq(PasswordPolicyEntity::getStatus, "ENABLED")
                .orderByDesc(PasswordPolicyEntity::getTenantId)
                .orderByDesc(PasswordPolicyEntity::getId)
                .last("LIMIT 1"));
    }

    List<PasswordPolicyResponse> selectPasswordPolicyList();

    PasswordPolicyResponse selectPasswordPolicyById(@Param("id") Long id);
}
