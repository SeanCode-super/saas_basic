package com.saasbasics.platform.modules.workflow.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 工作流单体嵌入运行模式健康探针控制器。
 * 在单体模式下提供与独立部署模式完全一致的 /api/workflow/health 契约，
 * 使前端探针、运维心跳与微前端路由无感平滑切换。
 */
@RestController
@RequestMapping("/api/workflow")
@ConditionalOnProperty(name = "saas.workflow.mode", havingValue = "embedded", matchIfMissing = true)
public class WorkflowEmbeddedHealthController {

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.success(Map.of(
                "status", "UP",
                "mode", "EMBEDDED",
                "message", "Workflow engine running embedded within monolithic application"
        ));
    }
}
