package com.saasbasics.platform.modules.iam.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.iam.dto.PortalClientSaveRequest;
import com.saasbasics.platform.modules.iam.dto.PortalEntryResponse;
import com.saasbasics.platform.modules.iam.dto.PortalClientResponse;
import com.saasbasics.platform.modules.iam.dto.PortalTerminalSaveRequest;
import com.saasbasics.platform.modules.iam.dto.PortalTerminalResponse;
import com.saasbasics.platform.modules.iam.service.PortalEntryService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portal")
public class PortalController {

    private final PortalEntryService portalEntryService;

    public PortalController(PortalEntryService portalEntryService) {
        this.portalEntryService = portalEntryService;
    }

    @GetMapping("/entry")
    public ApiResponse<PortalEntryResponse> entry(@RequestParam(required = false) String clientId,
                                                  @RequestParam(required = false) String terminalCode) {
        return ApiResponse.success(portalEntryService.resolveEntry(clientId, terminalCode));
    }

    @GetMapping("/clients")
    @RequirePermission("system:config:query")
    public ApiResponse<List<PortalClientResponse>> clients() {
        return ApiResponse.success(portalEntryService.listClients());
    }

    @PostMapping("/clients")
    @RequirePermission("system:config:write")
    public ApiResponse<PortalClientResponse> createClient(@Valid @RequestBody PortalClientSaveRequest request) {
        return ApiResponse.success(portalEntryService.createClient(request));
    }

    @PutMapping("/clients/{id}")
    @RequirePermission("system:config:write")
    public ApiResponse<PortalClientResponse> updateClient(@PathVariable Long id,
                                                          @Valid @RequestBody PortalClientSaveRequest request) {
        return ApiResponse.success(portalEntryService.updateClient(id, request));
    }

    @GetMapping("/terminals")
    @RequirePermission("system:config:query")
    public ApiResponse<List<PortalTerminalResponse>> terminals(@RequestParam(required = false) String clientId) {
        return ApiResponse.success(portalEntryService.listTerminals(clientId));
    }

    @PostMapping("/terminals")
    @RequirePermission("system:config:write")
    public ApiResponse<PortalTerminalResponse> createTerminal(@Valid @RequestBody PortalTerminalSaveRequest request) {
        return ApiResponse.success(portalEntryService.createTerminal(request));
    }

    @PutMapping("/terminals/{id}")
    @RequirePermission("system:config:write")
    public ApiResponse<PortalTerminalResponse> updateTerminal(@PathVariable Long id,
                                                              @Valid @RequestBody PortalTerminalSaveRequest request) {
        return ApiResponse.success(portalEntryService.updateTerminal(id, request));
    }
}
