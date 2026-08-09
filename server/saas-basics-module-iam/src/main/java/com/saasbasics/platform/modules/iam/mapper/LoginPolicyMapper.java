package com.saasbasics.platform.modules.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.iam.dto.LoginPolicyResponse;
import com.saasbasics.platform.modules.iam.entity.LoginPolicyEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LoginPolicyMapper extends BaseMapper<LoginPolicyEntity> {

    @Select("""
            SELECT *
            FROM iam_login_policy
            WHERE tenant_id IN (0, #{tenantId})
              AND status = 'ENABLED'
              AND deleted = 0
            ORDER BY tenant_id DESC, id DESC
            LIMIT 1
            """)
    LoginPolicyEntity selectActivePolicy(@Param("tenantId") Long tenantId);

    @Select("""
            SELECT
              id,
              tenant_id AS tenantId,
              policy_code AS policyCode,
              policy_name AS policyName,
              allow_password_login AS allowPasswordLogin,
              allow_sms_login AS allowSmsLogin,
              allow_email_login AS allowEmailLogin,
              allow_social_login AS allowSocialLogin,
              force_mfa AS forceMfa,
              session_timeout_minutes AS sessionTimeoutMinutes,
              max_failed_count AS maxFailedCount,
              lock_minutes AS lockMinutes,
              CAST(ip_allowlist_json AS CHAR) AS ipAllowlistJson,
              device_trust_days AS deviceTrustDays,
              status,
              remark
            FROM iam_login_policy
            WHERE deleted = 0
            ORDER BY tenant_id DESC, id ASC
            """)
    List<LoginPolicyResponse> selectLoginPolicyList();

    @Select("""
            SELECT
              id,
              tenant_id AS tenantId,
              policy_code AS policyCode,
              policy_name AS policyName,
              allow_password_login AS allowPasswordLogin,
              allow_sms_login AS allowSmsLogin,
              allow_email_login AS allowEmailLogin,
              allow_social_login AS allowSocialLogin,
              force_mfa AS forceMfa,
              session_timeout_minutes AS sessionTimeoutMinutes,
              max_failed_count AS maxFailedCount,
              lock_minutes AS lockMinutes,
              CAST(ip_allowlist_json AS CHAR) AS ipAllowlistJson,
              device_trust_days AS deviceTrustDays,
              status,
              remark
            FROM iam_login_policy
            WHERE id = #{id}
              AND deleted = 0
            """)
    LoginPolicyResponse selectLoginPolicyById(@Param("id") Long id);
}
