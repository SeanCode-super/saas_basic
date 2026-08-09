package com.saasbasics.platform.modules.iam.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequireDataPermission;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.iam.dto.UserResponse;
import com.saasbasics.platform.modules.iam.dto.UserSaveRequest;
import com.saasbasics.platform.modules.iam.dto.UserStatusUpdateRequest;
import com.saasbasics.platform.modules.iam.service.UserService;
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
@RequestMapping("/api/iam/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @RequirePermission("iam:user:query")
    @RequireDataPermission("iam:user:list")
    public ApiResponse<List<UserResponse>> list() {
        return ApiResponse.success(userService.listUsers());
    }

    @GetMapping("/{id}")
    @RequirePermission("iam:user:query")
    public ApiResponse<UserResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(userService.getUser(id));
    }

    @PostMapping
    @RequirePermission("iam:user:write")
    public ApiResponse<UserResponse> create(@Valid @RequestBody UserSaveRequest request) {
        return ApiResponse.success(userService.createUser(request));
    }

    @PutMapping("/{id}")
    @RequirePermission("iam:user:write")
    public ApiResponse<UserResponse> update(@PathVariable Long id, @Valid @RequestBody UserSaveRequest request) {
        return ApiResponse.success(userService.updateUser(id, request));
    }

    @PatchMapping("/{id}/status")
    @RequirePermission("iam:user:write")
    public ApiResponse<UserResponse> updateStatus(@PathVariable Long id,
                                                  @Valid @RequestBody UserStatusUpdateRequest request) {
        return ApiResponse.success(userService.updateStatus(id, request));
    }
}
