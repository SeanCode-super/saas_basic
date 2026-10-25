package com.saasbasics.platform.modules.integration.message;

import com.saasbasics.platform.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 多渠道统一消息网关控制器。
 */
@RestController
@RequestMapping("/api/message")
public class MessageGatewayController {

    private final MessageGatewayService messageGatewayService;

    public MessageGatewayController(MessageGatewayService messageGatewayService) {
        this.messageGatewayService = messageGatewayService;
    }

    @PostMapping("/sms/send")
    public ApiResponse<SmsSendResult> sendSms(@Valid @RequestBody SmsSendRequest request) {
        return ApiResponse.success(messageGatewayService.sendSms(request));
    }

    @PostMapping("/mail/send")
    public ApiResponse<MailSendResult> sendMail(@Valid @RequestBody MailSendRequest request) {
        return ApiResponse.success(messageGatewayService.sendMail(request));
    }

    @PostMapping("/bot/send")
    public ApiResponse<BotSendResult> sendBot(@Valid @RequestBody BotSendRequest request) {
        return ApiResponse.success(messageGatewayService.sendBot(request));
    }

    @GetMapping("/channels")
    public ApiResponse<Map<String, Object>> getChannels() {
        return ApiResponse.success(messageGatewayService.getChannels());
    }

    @GetMapping("/logs")
    public ApiResponse<List<MessageLogItem>> getLogs() {
        return ApiResponse.success(messageGatewayService.getLogs());
    }

    @DeleteMapping("/logs")
    public ApiResponse<Boolean> clearLogs() {
        messageGatewayService.clearLogs();
        return ApiResponse.success(true);
    }
}
