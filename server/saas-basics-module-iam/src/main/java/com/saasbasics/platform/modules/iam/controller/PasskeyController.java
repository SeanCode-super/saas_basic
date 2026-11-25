package com.saasbasics.platform.modules.iam.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.modules.iam.dto.AuthLoginResponse;
import com.saasbasics.platform.modules.iam.service.PasskeyService;
import com.saasbasics.platform.modules.iam.service.PasskeyService.AuthenticationBeginRequest;
import com.saasbasics.platform.modules.iam.service.PasskeyService.AuthenticationFinishRequest;
import com.saasbasics.platform.modules.iam.service.PasskeyService.AuthenticationOptions;
import com.saasbasics.platform.modules.iam.service.PasskeyService.PasskeySummary;
import com.saasbasics.platform.modules.iam.service.PasskeyService.RegistrationBeginRequest;
import com.saasbasics.platform.modules.iam.service.PasskeyService.RegistrationFinishRequest;
import com.saasbasics.platform.modules.iam.service.PasskeyService.RegistrationOptions;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PasskeyController {

    private final PasskeyService passkeyService;

    public PasskeyController(PasskeyService passkeyService) {
        this.passkeyService = passkeyService;
    }

    @GetMapping("/api/iam/passkeys")
    public ApiResponse<List<PasskeySummary>> listMyPasskeys() {
        return ApiResponse.success(passkeyService.listMyPasskeys());
    }

    @PostMapping("/api/iam/passkeys/register/begin")
    public ApiResponse<RegistrationOptions> beginRegistration(@RequestBody(required = false) RegistrationBeginRequest request) {
        return ApiResponse.success(passkeyService.beginRegistration(request));
    }

    @PostMapping("/api/iam/passkeys/register/finish")
    public ApiResponse<PasskeySummary> finishRegistration(@RequestBody RegistrationFinishRequest request) {
        return ApiResponse.success(passkeyService.finishRegistration(request));
    }

    @DeleteMapping("/api/iam/passkeys/{publicId}")
    public ApiResponse<Void> revokePasskey(@PathVariable UUID publicId) {
        passkeyService.revokePasskey(publicId);
        return ApiResponse.success(null);
    }

    @PostMapping("/api/auth/passkey/begin")
    public ApiResponse<AuthenticationOptions> beginAuthentication(@RequestBody(required = false) AuthenticationBeginRequest request) {
        return ApiResponse.success(passkeyService.beginAuthentication(request));
    }

    @PostMapping("/api/auth/passkey/finish")
    public ApiResponse<AuthLoginResponse> finishAuthentication(
            @RequestBody AuthenticationFinishRequest request,
            HttpServletRequest httpRequest
    ) {
        String ip = httpRequest != null ? httpRequest.getRemoteAddr() : "127.0.0.1";
        String ua = httpRequest != null ? httpRequest.getHeader("User-Agent") : "unknown";
        return ApiResponse.success(passkeyService.finishAuthentication(request, ip, ua));
    }
}
