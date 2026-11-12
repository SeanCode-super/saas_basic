package com.saasbasics.platform.modules.ai.web;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.modules.ai.application.dto.AiCommandDtos.AiCommandHistoryDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiCommandDtos.AiCommandRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiCommandDtos.AiCommandResponse;
import com.saasbasics.platform.modules.ai.application.dto.AiCommandDtos.Nl2SqlQueryRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiCommandDtos.Nl2SqlQueryResponse;
import com.saasbasics.platform.modules.ai.application.dto.AiCommandDtos.Nl2SqlSchemaTableDTO;
import com.saasbasics.platform.modules.ai.application.service.AiCommandRouterService;
import com.saasbasics.platform.modules.ai.application.service.AiSchemaCatalogService;
import com.saasbasics.platform.modules.ai.application.service.Nl2SqlService;
import com.saasbasics.platform.ratelimit.RateLimit;
import com.saasbasics.platform.ratelimit.RateLimitType;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiCommandController {

    private final AiCommandRouterService commandRouterService;
    private final Nl2SqlService nl2SqlService;
    private final AiSchemaCatalogService schemaCatalogService;

    public AiCommandController(
            AiCommandRouterService commandRouterService,
            Nl2SqlService nl2SqlService,
            AiSchemaCatalogService schemaCatalogService
    ) {
        this.commandRouterService = commandRouterService;
        this.nl2SqlService = nl2SqlService;
        this.schemaCatalogService = schemaCatalogService;
    }

    @PostMapping("/command/interpret")
    @RateLimit(type = RateLimitType.TENANT, permits = 120, windowSeconds = 60,
            message = "⌘K AI 指令解析频次过高，请稍后再试")
    public ApiResponse<AiCommandResponse> interpretCommand(@RequestBody AiCommandRequest request) {
        return ApiResponse.success(commandRouterService.interpret(request));
    }

    @GetMapping("/command/history")
    public ApiResponse<List<AiCommandHistoryDTO>> listHistory(
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return ApiResponse.success(commandRouterService.listHistory(limit));
    }

    @PostMapping("/nl2sql/query")
    @RateLimit(type = RateLimitType.TENANT, permits = 60, windowSeconds = 60,
            message = "NL2SQL 数据查询频次超出租户保护阈值")
    public ApiResponse<Nl2SqlQueryResponse> nl2SqlQuery(@RequestBody Nl2SqlQueryRequest request) {
        return ApiResponse.success(nl2SqlService.executeNaturalQuery(request));
    }

    @GetMapping("/nl2sql/schemas")
    public ApiResponse<List<Nl2SqlSchemaTableDTO>> listSchemas() {
        AuthPrincipal principal = AuthContext.get();
        Long tenantId = principal != null && principal.tenantId() != null ? principal.tenantId() : 1L;
        return ApiResponse.success(schemaCatalogService.listAllowedSchemas(tenantId));
    }
}
