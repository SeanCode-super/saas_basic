package com.saasbasics.platform.modules.iam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserProfileUpdateRequest(
        @NotBlank(message = "昵称不能为空")
        @Size(max = 64, message = "昵称不能超过64字符")
        String nickname,
        @Size(max = 32, message = "手机号不能超过32字符")
        String mobile,
        @Size(max = 128, message = "邮箱不能超过128字符")
        String email,
        String gender,
        @Size(max = 500, message = "个人简介不能超过500字符")
        String bio,
        @Size(max = 128, message = "办公地点不能超过128字符")
        String workLocation,
        @Size(max = 128, message = "备用联系方式不能超过128字符")
        String extContact,
        String locale,
        String timezone,
        String confirmPassword
) {
}
