package com.saasbasics.platform.modules.iam.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.modules.iam.dto.AuthCurrentUserResponse;
import com.saasbasics.platform.modules.iam.dto.AuthContextSelectionRequest;
import com.saasbasics.platform.modules.organization.api.OrganizationDirectory;
import com.saasbasics.platform.modules.iam.dto.AuthLoginRequest;
import com.saasbasics.platform.modules.iam.dto.AuthLoginResponse;
import com.saasbasics.platform.modules.iam.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<AuthLoginResponse> login(@Valid @RequestBody AuthLoginRequest request,
                                                HttpServletRequest httpServletRequest) {
        String loginIp = httpServletRequest.getRemoteAddr();
        String userAgent = httpServletRequest.getHeader("User-Agent");
        return ApiResponse.success(authService.login(request, loginIp, userAgent));
    }

    @GetMapping("/me")
    public ApiResponse<AuthCurrentUserResponse> me() {
        return ApiResponse.success(authService.currentUser());
    }

    @GetMapping("/context-options")
    public ApiResponse<java.util.List<OrganizationDirectory.AssignmentSummary>> contextOptions() {
        return ApiResponse.success(authService.contextOptions());
    }

    @PutMapping("/context")
    public ApiResponse<AuthCurrentUserResponse> selectContext(
            @Valid @RequestBody AuthContextSelectionRequest request) {
        return ApiResponse.success(authService.selectContext(request));
    }

    @DeleteMapping("/context")
    public ApiResponse<AuthCurrentUserResponse> clearContext() {
        return ApiResponse.success(authService.clearContext());
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.logout(extractAccessToken(authorization));
        return ApiResponse.success(null);
    }

    private String extractAccessToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        if (authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return authorization;
    }
}
