package com.saasbasics.platform.modules.iam.dto;

import com.saasbasics.platform.common.mask.DataMaskType;
import com.saasbasics.platform.mask.DataMask;

public record UserResponse(
        Long id,
        java.util.UUID publicId,
        Long tenantId,
        String userCode,
        String username,
        String nickname,
        Long employeeId,
        String userType,
        String status,
        @DataMask(type = DataMaskType.MOBILE)
        String mobile,
        @DataMask(type = DataMaskType.EMAIL)
        String email,
        String remark
) {
}
