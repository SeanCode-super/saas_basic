package com.saasbasics.platform.modules.iam.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.iam.dto.SelfServiceAccountDtos.PasswordResetConfirmRequest;
import com.saasbasics.platform.modules.iam.dto.SelfServiceAccountDtos.PasswordResetSendCodeRequest;
import com.saasbasics.platform.modules.iam.dto.SelfServiceAccountDtos.PasswordResetSendCodeResponse;
import com.saasbasics.platform.modules.iam.dto.SelfServiceAccountDtos.TenantInviteAcceptRequest;
import com.saasbasics.platform.modules.iam.dto.SelfServiceAccountDtos.TenantInviteCreateRequest;
import com.saasbasics.platform.modules.iam.dto.SelfServiceAccountDtos.TenantInviteResponse;
import com.saasbasics.platform.modules.iam.service.SelfServiceAccountService;
import com.saasbasics.platform.ratelimit.LimitType;
import com.saasbasics.platform.ratelimit.RateLimiter;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 租户用户自助密码找回 (OTP) 与成员邀请入驻控制器。
 */
@RestController
public class SelfServiceAccountController {

    private final SelfServiceAccountService selfServiceAccountService;

    public SelfServiceAccountController(SelfServiceAccountService selfServiceAccountService) {
        this.selfServiceAccountService = selfServiceAccountService;
    }

    @PostMapping("/api/auth/password-reset/send-code")
    @RateLimiter(count = 10, time = 60, limitType = LimitType.IP, message = "验证码请求过于频繁，请稍后再试")
    public ApiResponse<PasswordResetSendCodeResponse> sendPasswordResetCode(
            @Valid @RequestBody PasswordResetSendCodeRequest request
    ) {
        return ApiResponse.success(selfServiceAccountService.sendPasswordResetCode(request));
    }

    @PostMapping("/api/auth/password-reset/confirm")
    @RateLimiter(count = 15, time = 60, limitType = LimitType.IP, message = "重置尝试过于频繁，请稍后再试")
    public ApiResponse<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        selfServiceAccountService.confirmPasswordReset(request);
        return ApiResponse.success(null);
    }

    @GetMapping("/api/auth/invites/{token}")
    public ApiResponse<TenantInviteResponse> previewInvite(@PathVariable String token) {
        return ApiResponse.success(selfServiceAccountService.previewInvite(token));
    }

    @PostMapping("/api/auth/invites/{token}/accept")
    @RateLimiter(count = 15, time = 60, limitType = LimitType.IP, message = "注册请求过于频繁，请稍后再试")
    public ApiResponse<TenantInviteResponse> acceptInvite(
            @PathVariable String token,
            @Valid @RequestBody TenantInviteAcceptRequest request
    ) {
        return ApiResponse.success(selfServiceAccountService.acceptInvite(token, request));
    }

    @PostMapping("/api/iam/invites")
    @RequirePermission("iam:user:write")
    public ApiResponse<TenantInviteResponse> createInvite(@RequestBody(required = false) TenantInviteCreateRequest request) {
        return ApiResponse.success(selfServiceAccountService.createInvite(request));
    }

    @GetMapping("/api/iam/invites")
    @RequirePermission("iam:user:query")
    public ApiResponse<List<TenantInviteResponse>> listInvites() {
        return ApiResponse.success(selfServiceAccountService.listInvites());
    }
}
