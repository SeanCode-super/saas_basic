package com.saasbasics.platform.modules.iam.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.iam.dto.AuthCurrentUserResponse;
import com.saasbasics.platform.modules.iam.dto.AuthContextSelectionRequest;
import com.saasbasics.platform.modules.organization.api.OrganizationDirectory;
import com.saasbasics.platform.modules.iam.dto.AuthLoginRequest;
import com.saasbasics.platform.modules.iam.dto.AuthLoginResponse;
import com.saasbasics.platform.modules.iam.dto.AuthRefreshTokenRequest;
import com.saasbasics.platform.modules.iam.dto.OnlineSessionResponse;
import com.saasbasics.platform.modules.iam.dto.SsoCallbackRequest;
import com.saasbasics.platform.modules.iam.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @com.saasbasics.platform.ratelimit.RateLimiter(count = 20, time = 60, limitType = com.saasbasics.platform.ratelimit.LimitType.IP, message = "登录尝试过于频繁，IP已被临时锁定防护，请1分钟后再试")
    @com.saasbasics.platform.repeat.RepeatSubmit(interval = 1000, message = "登录凭证校验中，请勿重复点击")
    public ApiResponse<AuthLoginResponse> login(@Valid @RequestBody AuthLoginRequest request,
                                                HttpServletRequest httpServletRequest) {
        String loginIp = httpServletRequest.getRemoteAddr();
        String userAgent = httpServletRequest.getHeader("User-Agent");
        return ApiResponse.success(authService.login(request, loginIp, userAgent));
    }

    @PostMapping("/refresh-token")
    public ApiResponse<AuthLoginResponse> refreshToken(@Valid @RequestBody AuthRefreshTokenRequest request,
                                                       HttpServletRequest httpServletRequest) {
        String loginIp = httpServletRequest.getRemoteAddr();
        String userAgent = httpServletRequest.getHeader("User-Agent");
        return ApiResponse.success(authService.refreshToken(request, loginIp, userAgent));
    }

    @GetMapping("/sso/authorize-url")
    public ApiResponse<Map<String, String>> getSsoAuthorizeUrl(@RequestParam(required = false, defaultValue = "WECOM") String channel,
                                                               @RequestParam(required = false) String redirectUri) {
        return ApiResponse.success(authService.getSsoAuthorizeUrl(channel, redirectUri));
    }

    @PostMapping("/sso/callback")
    public ApiResponse<AuthLoginResponse> ssoCallback(@RequestBody SsoCallbackRequest request,
                                                      HttpServletRequest httpServletRequest) {
        String loginIp = httpServletRequest.getRemoteAddr();
        String userAgent = httpServletRequest.getHeader("User-Agent");
        return ApiResponse.success(authService.loginBySso(request, loginIp, userAgent));
    }

    @GetMapping("/online-sessions")
    @RequirePermission("iam:user:query")
    public ApiResponse<List<OnlineSessionResponse>> listOnlineSessions() {
        return ApiResponse.success(authService.listOnlineSessions());
    }

    @DeleteMapping("/online-sessions/{sessionNo}")
    @RequirePermission("iam:user:write")
    public ApiResponse<Void> kickoutSession(@PathVariable String sessionNo) {
        authService.kickoutSession(sessionNo);
        return ApiResponse.success(null);
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
