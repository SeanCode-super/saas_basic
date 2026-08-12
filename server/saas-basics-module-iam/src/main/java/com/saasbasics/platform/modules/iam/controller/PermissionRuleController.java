package com.saasbasics.platform.modules.iam.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.iam.dto.DataPermissionRuleResponse;
import com.saasbasics.platform.modules.iam.dto.DataPermissionRuleSaveRequest;
import com.saasbasics.platform.modules.iam.dto.MenuPermissionResponse;
import com.saasbasics.platform.modules.iam.dto.MenuPermissionSaveRequest;
import com.saasbasics.platform.modules.iam.dto.MenuSubjectGrantSaveRequest;
import com.saasbasics.platform.modules.iam.dto.StatusUpdateRequest;
import com.saasbasics.platform.modules.iam.service.DataPermissionRuleService;
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
@RequestMapping("/api/iam")
public class PermissionRuleController {

    private final MenuPermissionService menuPermissionService;
    private final DataPermissionRuleService dataPermissionRuleService;

    public PermissionRuleController(MenuPermissionService menuPermissionService,
                                    DataPermissionRuleService dataPermissionRuleService) {
        this.menuPermissionService = menuPermissionService;
        this.dataPermissionRuleService = dataPermissionRuleService;
    }

    @GetMapping("/menu-permissions")
    @RequirePermission("iam:menu:query")
    public ApiResponse<List<MenuPermissionResponse>> listMenuPermissions() {
        return ApiResponse.success(menuPermissionService.listPermissions());
    }

    @PostMapping("/menu-permissions")
    @RequirePermission("iam:menu:write")
    public ApiResponse<MenuPermissionResponse> createMenuPermission(@Valid @RequestBody MenuPermissionSaveRequest request) {
        return ApiResponse.success(menuPermissionService.createPermission(request));
    }

    @PutMapping("/menu-permissions/{id}")
    @RequirePermission("iam:menu:write")
    public ApiResponse<MenuPermissionResponse> updateMenuPermission(@PathVariable Long id,
                                                                    @Valid @RequestBody MenuPermissionSaveRequest request) {
        return ApiResponse.success(menuPermissionService.updatePermission(id, request));
    }

    @PatchMapping("/menu-permissions/{id}/status")
    @RequirePermission("iam:menu:write")
    public ApiResponse<MenuPermissionResponse> updateMenuPermissionStatus(@PathVariable Long id,
                                                                          @Valid @RequestBody StatusUpdateRequest request) {
        return ApiResponse.success(menuPermissionService.updateStatus(id, request));
    }

    @PutMapping("/menu-permissions/subject-grants")
    @RequirePermission("iam:menu:write")
    public ApiResponse<List<MenuPermissionResponse>> replaceSubjectMenuGrants(
            @Valid @RequestBody MenuSubjectGrantSaveRequest request) {
        return ApiResponse.success(menuPermissionService.replaceSubjectGrants(request));
    }

    @GetMapping("/data-permission-rules")
    @RequirePermission("iam:data-scope:query")
    public ApiResponse<List<DataPermissionRuleResponse>> listDataPermissionRules() {
        return ApiResponse.success(dataPermissionRuleService.listRules());
    }

    @PostMapping("/data-permission-rules")
    @RequirePermission("iam:data-scope:write")
    public ApiResponse<DataPermissionRuleResponse> createDataPermissionRule(@Valid @RequestBody DataPermissionRuleSaveRequest request) {
        return ApiResponse.success(dataPermissionRuleService.createRule(request));
    }

    @PutMapping("/data-permission-rules/{id}")
    @RequirePermission("iam:data-scope:write")
    public ApiResponse<DataPermissionRuleResponse> updateDataPermissionRule(@PathVariable Long id,
                                                                            @Valid @RequestBody DataPermissionRuleSaveRequest request) {
        return ApiResponse.success(dataPermissionRuleService.updateRule(id, request));
    }

    @PatchMapping("/data-permission-rules/{id}/status")
    @RequirePermission("iam:data-scope:write")
    public ApiResponse<DataPermissionRuleResponse> updateDataPermissionRuleStatus(@PathVariable Long id,
                                                                                  @Valid @RequestBody StatusUpdateRequest request) {
        return ApiResponse.success(dataPermissionRuleService.updateStatus(id, request));
    }
}
