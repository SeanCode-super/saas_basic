package com.saasbasics.platform.modules.ai.web;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.modules.ai.application.dto.AiChatRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiChatResponse;
import com.saasbasics.platform.modules.ai.application.service.AiChatGatewayService;
import com.saasbasics.platform.ratelimit.RateLimit;
import com.saasbasics.platform.ratelimit.RateLimitType;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiChatGatewayService aiGatewayService;

    public AiController(AiChatGatewayService aiGatewayService) {
        this.aiGatewayService = aiGatewayService;
    }

    @GetMapping("/models")
    public ApiResponse<List<Map<String, Object>>> getModels() {
        return ApiResponse.success(aiGatewayService.getModels());
    }

    @PostMapping("/chat")
    @RateLimit(type = RateLimitType.TENANT, permits = 60, windowSeconds = 60,
            message = "AI 对话调用频次超出租户每分钟保护上限，请稍后再试")
    public ApiResponse<AiChatResponse> chat(@RequestBody AiChatRequest request) {
        return ApiResponse.success(aiGatewayService.chat(request));
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimit(type = RateLimitType.TENANT, permits = 60, windowSeconds = 60,
            message = "AI 流式输出频次超出租户每分钟保护上限，请稍后再试")
    public SseEmitter chatStream(@RequestBody AiChatRequest request) {
        return aiGatewayService.chatStream(request);
    }
}
