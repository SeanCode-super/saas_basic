package com.saasbasics.platform.modules.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.iam.dto.PasswordPolicyResponse;
import com.saasbasics.platform.modules.iam.entity.PasswordPolicyEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PasswordPolicyMapper extends BaseMapper<PasswordPolicyEntity> {

    @Select("""
            SELECT *
            FROM iam_password_policy
            WHERE tenant_id IN (0, #{tenantId})
              AND status = 'ENABLED'
              AND deleted = 0
            ORDER BY tenant_id DESC, id DESC
            LIMIT 1
            """)
    PasswordPolicyEntity selectActivePolicy(@Param("tenantId") Long tenantId);

    @Select("""
            SELECT
              id,
              tenant_id AS tenantId,
              policy_code AS policyCode,
              policy_name AS policyName,
              min_length AS minLength,
              max_length AS maxLength,
              require_uppercase AS requireUppercase,
              require_lowercase AS requireLowercase,
              require_number AS requireNumber,
              require_special AS requireSpecial,
              password_history_limit AS passwordHistoryLimit,
              password_expire_days AS passwordExpireDays,
              temp_password_expire_hours AS tempPasswordExpireHours,
              status,
              remark
            FROM iam_password_policy
            WHERE deleted = 0
            ORDER BY tenant_id DESC, id ASC
            """)
    List<PasswordPolicyResponse> selectPasswordPolicyList();

    @Select("""
            SELECT
              id,
              tenant_id AS tenantId,
              policy_code AS policyCode,
              policy_name AS policyName,
              min_length AS minLength,
              max_length AS maxLength,
              require_uppercase AS requireUppercase,
              require_lowercase AS requireLowercase,
              require_number AS requireNumber,
              require_special AS requireSpecial,
              password_history_limit AS passwordHistoryLimit,
              password_expire_days AS passwordExpireDays,
              temp_password_expire_hours AS tempPasswordExpireHours,
              status,
              remark
            FROM iam_password_policy
            WHERE id = #{id}
              AND deleted = 0
            """)
    PasswordPolicyResponse selectPasswordPolicyById(@Param("id") Long id);
}
