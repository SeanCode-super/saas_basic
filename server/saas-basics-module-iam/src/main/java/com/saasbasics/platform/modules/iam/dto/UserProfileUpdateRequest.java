package com.saasbasics.platform.modules.iam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserProfileUpdateRequest(
        @NotBlank(message = "昵称不能为空")
        @Size(max = 64, message = "昵称不能超过64字符")
        String nickname,
        String mobile,
        String email
) {
}
