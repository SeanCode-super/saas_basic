package com.saasbasics.platform.modules.iam.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

public final class SelfServiceAccountDtos {

    private SelfServiceAccountDtos() {
    }

    public record PasswordResetSendCodeRequest(
            @NotBlank(message = "用户名不能为空")
            String username,
            String tenantCode,
            @NotBlank(message = "绑定邮箱或手机号不能为空")
            String contact
    ) {
    }

    public record PasswordResetSendCodeResponse(
            boolean accepted,
            int expireSeconds,
            String deliveryChannel,
            String sandboxOtpCode
    ) {
    }

    public record PasswordResetConfirmRequest(
            @NotBlank(message = "用户名不能为空")
            String username,
            String tenantCode,
            @NotBlank(message = "验证码不能为空")
            String otpCode,
            @NotBlank(message = "新密码不能为空")
            String newPassword
    ) {
    }

    public record TenantInviteCreateRequest(
            String invitedEmail,
            Long roleId,
            Integer maxUses,
            Integer expireHours
    ) {
    }

    public record TenantInviteResponse(
            Long id,
            String inviteToken,
            String invitePath,
            String tenantCode,
            String tenantName,
            String invitedEmail,
            Integer maxUses,
            Integer usedCount,
            String status,
            LocalDateTime expireAt
    ) {
    }

    public record TenantInviteAcceptRequest(
            @NotBlank(message = "用户名不能为空")
            String username,
            @NotBlank(message = "昵称不能为空")
            String nickname,
            @NotBlank(message = "密码不能为空")
            String password,
            String email,
            String mobile
    ) {
    }
}
