package com.saasbasics.platform.modules.system.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.system.dto.SequenceNextResponse;
import com.saasbasics.platform.modules.system.dto.SequenceRuleResponse;
import com.saasbasics.platform.modules.system.dto.SequenceRuleSaveRequest;
import com.saasbasics.platform.modules.system.service.SequenceRuleService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/sequences")
public class SequenceRuleController {

    private final SequenceRuleService sequenceRuleService;

    public SequenceRuleController(SequenceRuleService sequenceRuleService) {
        this.sequenceRuleService = sequenceRuleService;
    }

    @GetMapping
    @RequirePermission("system:config:query")
    public ApiResponse<List<SequenceRuleResponse>> list() {
        return ApiResponse.success(sequenceRuleService.listRules());
    }

    @PutMapping("/{id}")
    @RequirePermission("system:config:write")
    public ApiResponse<SequenceRuleResponse> update(@PathVariable Long id,
                                                    @Valid @RequestBody SequenceRuleSaveRequest request) {
        return ApiResponse.success(sequenceRuleService.updateRule(id, request));
    }

    @PostMapping("/{code}/next")
    @RequirePermission("system:config:write")
    public ApiResponse<SequenceNextResponse> next(@PathVariable String code) {
        return ApiResponse.success(sequenceRuleService.nextValue(code));
    }

    @PostMapping("/{code}/reset")
    @RequirePermission("system:config:write")
    public ApiResponse<SequenceRuleResponse> reset(@PathVariable String code) {
        return ApiResponse.success(sequenceRuleService.resetRule(code));
    }
}
