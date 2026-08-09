package com.saasbasics.platform.modules.iam.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.iam.dto.MenuResponse;
import com.saasbasics.platform.modules.iam.dto.MenuSaveRequest;
import com.saasbasics.platform.modules.iam.dto.StatusUpdateRequest;
import com.saasbasics.platform.modules.iam.service.MenuService;
import com.saasbasics.platform.modules.iam.service.MenuPermissionService;
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
@RequestMapping("/api/iam/menus")
public class MenuController {

    private final MenuService menuService;
    private final MenuPermissionService menuPermissionService;

    public MenuController(MenuService menuService, MenuPermissionService menuPermissionService) {
        this.menuService = menuService;
        this.menuPermissionService = menuPermissionService;
    }

    @GetMapping("/current")
    public ApiResponse<List<MenuResponse>> currentMenus() {
        if (AuthContext.get() == null) {
            return ApiResponse.success(List.of());
        }
        return ApiResponse.success(menuPermissionService.listCurrentMenus(AuthContext.get()));
    }

    @GetMapping
    @RequirePermission("iam:menu:query")
    public ApiResponse<List<MenuResponse>> list() {
        return ApiResponse.success(menuService.listMenus());
    }

    @GetMapping("/{id}")
    @RequirePermission("iam:menu:query")
    public ApiResponse<MenuResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(menuService.getMenu(id));
    }

    @PostMapping
    @RequirePermission("iam:menu:write")
    public ApiResponse<MenuResponse> create(@Valid @RequestBody MenuSaveRequest request) {
        return ApiResponse.success(menuService.createMenu(request));
    }

    @PutMapping("/{id}")
    @RequirePermission("iam:menu:write")
    public ApiResponse<MenuResponse> update(@PathVariable Long id, @Valid @RequestBody MenuSaveRequest request) {
        return ApiResponse.success(menuService.updateMenu(id, request));
    }

    @PatchMapping("/{id}/status")
    @RequirePermission("iam:menu:write")
    public ApiResponse<MenuResponse> updateStatus(@PathVariable Long id,
                                                  @Valid @RequestBody StatusUpdateRequest request) {
        return ApiResponse.success(menuService.updateStatus(id, request));
    }
}
