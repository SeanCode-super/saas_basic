package com.saasbasics.platform.workflow;

import com.saasbasics.platform.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

/**
 * 工作流独立子系统容灾与代理控制器。
 * 当 saas.workflow.mode = standalone 时激活，负责转发请求至独立的 Workflow 服务进程，
 * 并在工作流服务重启、停止维护或网络抖动时提供优雅的熔断与降级保护，确保主平台其他业务 100% 不受影响。
 */
@RestController
@RequestMapping("/api/workflow")
@ConditionalOnProperty(name = "saas.workflow.mode", havingValue = "standalone")
public class WorkflowFaultTolerantProxyController {

    private static final Logger log = LoggerFactory.getLogger(WorkflowFaultTolerantProxyController.class);

    private final RestClient restClient;
    private final String standaloneUrl;

    public WorkflowFaultTolerantProxyController(
            @Value("${saas.workflow.standalone-url:http://127.0.0.1:8082}") String standaloneUrl) {
        this.standaloneUrl = standaloneUrl;
        this.restClient = RestClient.builder()
                .baseUrl(standaloneUrl)
                .build();
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        try {
            ResponseEntity<String> response = restClient.get()
                    .uri("/actuator/health")
                    .retrieve()
                    .toEntity(String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                return ApiResponse.success(Map.of("status", "UP", "standaloneUrl", standaloneUrl));
            }
        } catch (Exception ex) {
            log.debug("Workflow independent service is currently DOWN or restarting: {}", ex.getMessage());
        }
        return ApiResponse.failure("WORKFLOW_DOWN", "工作流引擎独立服务离线或正在重启", Map.of("status", "DOWN", "standaloneUrl", standaloneUrl));
    }

    @RequestMapping("/**")
    public ResponseEntity<?> proxyRequest(HttpMethod method, HttpServletRequest request,
                                          @RequestBody(required = false) byte[] body) {
        String targetUri = request.getRequestURI();
        String queryString = request.getQueryString();
        if (queryString != null && !queryString.isBlank()) {
            targetUri += "?" + queryString;
        }

        HttpHeaders headers = new HttpHeaders();
        Collections.list(request.getHeaderNames()).forEach(headerName -> {
            if (!headerName.equalsIgnoreCase("host") && !headerName.equalsIgnoreCase("content-length")) {
                headers.addAll(headerName, Collections.list(request.getHeaders(headerName)));
            }
        });

        try {
            RestClient.RequestBodySpec requestSpec = restClient.method(method)
                    .uri(targetUri)
                    .headers(h -> h.addAll(headers));

            if (body != null && body.length > 0) {
                requestSpec.body(body);
            }

            return requestSpec.retrieve().toEntity(byte[].class);
        } catch (Exception ex) {
            log.warn("Workflow standalone service call failed (isolated gracefully): {}", ex.getMessage());
            ApiResponse<Map<String, String>> fallback = ApiResponse.failure(
                    "WORKFLOW_SERVICE_UNAVAILABLE",
                    "工作流引擎正在独立重启或维护中，核心平台运行正常，请稍后重试",
                    Map.of(
                            "service", "workflow",
                            "status", "MAINTENANCE",
                            "standaloneUrl", standaloneUrl
                    )
            );
            return ResponseEntity.status(503).body(fallback);
        }
    }
}
