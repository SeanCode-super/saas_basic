package com.saasbasics.platform.modules.iam.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.iam.dto.LoginPolicyResponse;
import com.saasbasics.platform.modules.iam.dto.LoginPolicySaveRequest;
import com.saasbasics.platform.modules.iam.dto.PasswordPolicyResponse;
import com.saasbasics.platform.modules.iam.dto.PasswordPolicySaveRequest;
import com.saasbasics.platform.modules.iam.dto.StatusUpdateRequest;
import com.saasbasics.platform.modules.iam.service.LoginPolicyService;
import com.saasbasics.platform.modules.iam.service.PasswordPolicyService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/iam")
public class PolicyController {

    private final LoginPolicyService loginPolicyService;
    private final PasswordPolicyService passwordPolicyService;

    public PolicyController(LoginPolicyService loginPolicyService,
                            PasswordPolicyService passwordPolicyService) {
        this.loginPolicyService = loginPolicyService;
        this.passwordPolicyService = passwordPolicyService;
    }

    @GetMapping("/login-policies")
    @RequirePermission("iam:login-policy:query")
    public ApiResponse<List<LoginPolicyResponse>> listLoginPolicies() {
        return ApiResponse.success(loginPolicyService.listPolicies());
    }

    @PostMapping("/login-policies")
    @RequirePermission("iam:login-policy:write")
    public ApiResponse<LoginPolicyResponse> createLoginPolicy(@Valid @RequestBody LoginPolicySaveRequest request) {
        return ApiResponse.success(loginPolicyService.createPolicy(request));
    }

    @PutMapping("/login-policies/{id}")
    @RequirePermission("iam:login-policy:write")
    public ApiResponse<LoginPolicyResponse> updateLoginPolicy(@PathVariable Long id,
                                                              @Valid @RequestBody LoginPolicySaveRequest request) {
        return ApiResponse.success(loginPolicyService.updatePolicy(id, request));
    }

    @PatchMapping("/login-policies/{id}/status")
    @RequirePermission("iam:login-policy:write")
    public ApiResponse<LoginPolicyResponse> updateLoginPolicyStatus(@PathVariable Long id,
                                                                    @Valid @RequestBody StatusUpdateRequest request) {
        return ApiResponse.success(loginPolicyService.updateStatus(id, request));
    }

    @GetMapping("/password-policies")
    @RequirePermission("iam:password-policy:query")
    public ApiResponse<List<PasswordPolicyResponse>> listPasswordPolicies() {
        return ApiResponse.success(passwordPolicyService.listPolicies());
    }

    @PostMapping("/password-policies")
    @RequirePermission("iam:password-policy:write")
    public ApiResponse<PasswordPolicyResponse> createPasswordPolicy(@Valid @RequestBody PasswordPolicySaveRequest request) {
        return ApiResponse.success(passwordPolicyService.createPolicy(request));
    }

    @PutMapping("/password-policies/{id}")
    @RequirePermission("iam:password-policy:write")
    public ApiResponse<PasswordPolicyResponse> updatePasswordPolicy(@PathVariable Long id,
                                                                    @Valid @RequestBody PasswordPolicySaveRequest request) {
        return ApiResponse.success(passwordPolicyService.updatePolicy(id, request));
    }

    @PatchMapping("/password-policies/{id}/status")
    @RequirePermission("iam:password-policy:write")
    public ApiResponse<PasswordPolicyResponse> updatePasswordPolicyStatus(@PathVariable Long id,
                                                                          @Valid @RequestBody StatusUpdateRequest request) {
        return ApiResponse.success(passwordPolicyService.updateStatus(id, request));
    }
}
