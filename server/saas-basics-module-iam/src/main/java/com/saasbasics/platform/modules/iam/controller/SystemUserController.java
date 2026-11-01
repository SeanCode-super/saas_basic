package com.saasbasics.platform.modules.iam.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.modules.iam.dto.UserResponse;
import com.saasbasics.platform.modules.iam.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 组织通讯录与成员检索只读接口（面向当前租户已登录成员开放，受租户逻辑硬隔离与数据脱敏保护）
 */
@RestController
@RequestMapping({"/api/system/users", "/api/iam/users/contacts"})
public class SystemUserController {

    private final UserService userService;

    public SystemUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<List<UserResponse>> list() {
        return ApiResponse.success(userService.listUsers());
    }
}
