package com.saasbasics.platform.modules.iam.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.modules.iam.dto.UserPasswordUpdateRequest;
import com.saasbasics.platform.modules.iam.dto.UserProfileResponse;
import com.saasbasics.platform.modules.iam.dto.UserProfileUpdateRequest;
import com.saasbasics.platform.modules.iam.service.UserProfileService;
import com.saasbasics.platform.ratelimit.LimitType;
import com.saasbasics.platform.ratelimit.RateLimiter;
import com.saasbasics.platform.repeat.RepeatSubmit;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/iam/profile")
public class UserProfileController {

    private final UserProfileService userProfileService;

    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping
    public ApiResponse<UserProfileResponse> getProfile() {
        return ApiResponse.success(userProfileService.getCurrentProfile());
    }

    @PutMapping
    @RepeatSubmit(interval = 2000, message = "资料保存中，请勿重复点击")
    public ApiResponse<UserProfileResponse> updateProfile(@Valid @RequestBody UserProfileUpdateRequest request) {
        return ApiResponse.success(userProfileService.updateProfile(request));
    }

    @PutMapping("/password")
    @RepeatSubmit(interval = 3000, message = "密码修改处理中，请勿频繁提交")
    @RateLimiter(count = 5, time = 60, limitType = LimitType.USER, message = "修改密码尝试过于频繁，请稍后再试")
    public ApiResponse<Void> updatePassword(@Valid @RequestBody UserPasswordUpdateRequest request) {
        userProfileService.updatePassword(request);
        return ApiResponse.success(null);
    }
}
