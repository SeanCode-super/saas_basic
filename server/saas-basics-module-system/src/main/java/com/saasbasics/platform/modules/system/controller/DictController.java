package com.saasbasics.platform.modules.system.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.system.dto.DictItemResponse;
import com.saasbasics.platform.modules.system.dto.DictItemSaveRequest;
import com.saasbasics.platform.modules.system.dto.DictOptionResponse;
import com.saasbasics.platform.modules.system.dto.DictStatusUpdateRequest;
import com.saasbasics.platform.modules.system.dto.DictTypeResponse;
import com.saasbasics.platform.modules.system.dto.DictTypeSaveRequest;
import com.saasbasics.platform.modules.system.service.DictService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/dicts")
public class DictController {

    private final DictService dictService;

    public DictController(DictService dictService) {
        this.dictService = dictService;
    }

    @GetMapping("/types")
    @RequirePermission("system:config:query")
    public ApiResponse<List<DictTypeResponse>> listTypes() {
        return ApiResponse.success(dictService.listTypes());
    }

    @PostMapping("/types")
    @RequirePermission("system:config:write")
    public ApiResponse<DictTypeResponse> createType(@Valid @RequestBody DictTypeSaveRequest request) {
        return ApiResponse.success(dictService.createType(request));
    }

    @PutMapping("/types/{id}")
    @RequirePermission("system:config:write")
    public ApiResponse<DictTypeResponse> updateType(@PathVariable Long id,
                                                    @Valid @RequestBody DictTypeSaveRequest request) {
        return ApiResponse.success(dictService.updateType(id, request));
    }

    @PatchMapping("/types/{id}/status")
    @RequirePermission("system:config:write")
    public ApiResponse<DictTypeResponse> updateTypeStatus(@PathVariable Long id,
                                                          @Valid @RequestBody DictStatusUpdateRequest request) {
        return ApiResponse.success(dictService.updateTypeStatus(id, request));
    }

    @GetMapping("/types/{dictTypeId}/items")
    @RequirePermission("system:config:query")
    public ApiResponse<List<DictItemResponse>> listItems(@PathVariable Long dictTypeId) {
        return ApiResponse.success(dictService.listItems(dictTypeId));
    }

    @PostMapping("/types/{dictTypeId}/items")
    @RequirePermission("system:config:write")
    public ApiResponse<DictItemResponse> createItem(@PathVariable Long dictTypeId,
                                                    @Valid @RequestBody DictItemSaveRequest request) {
        return ApiResponse.success(dictService.createItem(dictTypeId, request));
    }

    @PutMapping("/items/{id}")
    @RequirePermission("system:config:write")
    public ApiResponse<DictItemResponse> updateItem(@PathVariable Long id,
                                                    @Valid @RequestBody DictItemSaveRequest request) {
        return ApiResponse.success(dictService.updateItem(id, request));
    }

    @PatchMapping("/items/{id}/status")
    @RequirePermission("system:config:write")
    public ApiResponse<DictItemResponse> updateItemStatus(@PathVariable Long id,
                                                          @Valid @RequestBody DictStatusUpdateRequest request) {
        return ApiResponse.success(dictService.updateItemStatus(id, request));
    }

    @GetMapping("/options/{dictCode}")
    @RequirePermission("system:config:query")
    public ApiResponse<List<DictOptionResponse>> listOptions(@PathVariable String dictCode,
                                                             @RequestParam(required = false) Long tenantId) {
        return ApiResponse.success(dictService.listOptions(dictCode, resolveTenantId(tenantId)));
    }

    private Long resolveTenantId(Long tenantId) {
        if (tenantId != null) {
            return tenantId;
        }
        if (AuthContext.get() != null && AuthContext.get().tenantId() != null) {
            return AuthContext.get().tenantId();
        }
        return 1L;
    }
}
