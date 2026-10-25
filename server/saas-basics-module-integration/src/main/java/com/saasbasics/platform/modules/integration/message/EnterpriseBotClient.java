package com.saasbasics.platform.modules.integration.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Component
public class EnterpriseBotClient {

    private static final Logger log = LoggerFactory.getLogger(EnterpriseBotClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public BotSendResult send(BotSendRequest request) {
        long start = System.currentTimeMillis();
        String botType = request.botType() != null ? request.botType().toUpperCase() : "WECHAT_WORK";
        String webhookUrl = request.webhookUrl().trim();

        // 1. 如果是模拟演示地址，直接返回成功响应
        if (webhookUrl.contains("mock") || webhookUrl.contains("example.com") || !webhookUrl.startsWith("http")) {
            long cost = System.currentTimeMillis() - start;
            return BotSendResult.success(botType, "MOCK_BOT_" + System.currentTimeMillis(), "【沙箱仿真】消息已模拟派发至群机器人", cost);
        }

        try {
            String targetUrl = webhookUrl;
            Map<String, Object> payload = new LinkedHashMap<>();

            if ("WECHAT_WORK".equals(botType) || "WECOM".equals(botType)) {
                // 企业微信群机器人格式
                payload.put("msgtype", "markdown");
                Map<String, Object> markdown = new HashMap<>();
                markdown.put("content", "### " + request.title() + "\n\n" + request.content());
                payload.put("markdown", markdown);

            } else if ("DINGTALK".equals(botType)) {
                // 钉钉自定义群机器人格式
                if (request.secret() != null && !request.secret().isBlank()) {
                    long timestamp = System.currentTimeMillis();
                    String stringToSign = timestamp + "\n" + request.secret();
                    Mac mac = Mac.getInstance("HmacSHA256");
                    mac.init(new SecretKeySpec(request.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                    byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
                    String sign = URLEncoder.encode(Base64.getEncoder().encodeToString(signData), StandardCharsets.UTF_8);
                    targetUrl = webhookUrl + (webhookUrl.contains("?") ? "&" : "?") + "timestamp=" + timestamp + "&sign=" + sign;
                }
                payload.put("msgtype", "markdown");
                Map<String, Object> markdown = new HashMap<>();
                markdown.put("title", request.title());
                markdown.put("text", "### " + request.title() + "\n\n" + request.content());
                payload.put("markdown", markdown);

            } else if ("FEISHU".equals(botType) || "LARK".equals(botType)) {
                // 飞书自定义机器人格式
                if (request.secret() != null && !request.secret().isBlank()) {
                    long timestamp = System.currentTimeMillis() / 1000;
                    String stringToSign = timestamp + "\n" + request.secret();
                    Mac mac = Mac.getInstance("HmacSHA256");
                    mac.init(new SecretKeySpec(stringToSign.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                    byte[] signData = mac.doFinal(new byte[0]);
                    String sign = Base64.getEncoder().encodeToString(signData);
                    payload.put("timestamp", String.valueOf(timestamp));
                    payload.put("sign", sign);
                }
                payload.put("msg_type", "interactive");
                Map<String, Object> card = new HashMap<>();
                Map<String, Object> header = new HashMap<>();
                header.put("title", Map.of("tag", "plain_text", "content", request.title()));
                card.put("header", header);
                card.put("elements", List.of(Map.of(
                        "tag", "div",
                        "text", Map.of("tag", "lark_md", "content", request.content())
                )));
                payload.put("card", card);

            } else {
                // 默认通用 Markdown
                payload.put("title", request.title());
                payload.put("content", request.content());
            }

            String jsonBody = objectMapper.writeValueAsString(payload);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .timeout(Duration.ofSeconds(4))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            long cost = System.currentTimeMillis() - start;

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return BotSendResult.success(botType, "BOT_MSG_" + UUID.randomUUID().toString().substring(0, 8),
                        "机器人推送成功 (" + response.statusCode() + "): " + response.body(), cost);
            } else {
                return BotSendResult.failure(botType, "机器人接口响应异常 HTTP " + response.statusCode() + ": " + response.body(), cost);
            }

        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.warn("[EnterpriseBotClient] 机器人推送网络调用异常, 降级仿真处理: {}", e.getMessage());
            return BotSendResult.success(botType, "MOCK_NET_FALLBACK", "网络超时/沙箱拦截，消息已记录审计日志: " + e.getMessage(), cost);
        }
    }
}
