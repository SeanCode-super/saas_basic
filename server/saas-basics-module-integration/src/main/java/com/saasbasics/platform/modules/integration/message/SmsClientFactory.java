package com.saasbasics.platform.modules.integration.message;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 短信客户端工厂。
 * 支持根据 channelCode 动态获取对应的 SmsClient 实现，默认回退到沙箱 Mock。
 */
@Component
public class SmsClientFactory {

    private final Map<String, SmsClient> clientMap = new ConcurrentHashMap<>();
    private final MockSmsClient defaultMockClient;

    public SmsClientFactory(List<SmsClient> clients, MockSmsClient defaultMockClient) {
        this.defaultMockClient = defaultMockClient;
        for (SmsClient client : clients) {
            clientMap.put(client.getChannelCode().toUpperCase(), client);
        }
    }

    public SmsClient getClient(String channelCode) {
        if (channelCode == null || channelCode.isBlank()) {
            return defaultMockClient;
        }
        return clientMap.getOrDefault(channelCode.trim().toUpperCase(), defaultMockClient);
    }

    public List<Map<String, String>> listChannels() {
        List<Map<String, String>> list = new ArrayList<>();
        for (SmsClient client : clientMap.values()) {
            Map<String, String> item = new HashMap<>();
            item.put("channelCode", client.getChannelCode());
            item.put("channelName", client.getChannelName());
            item.put("status", "ACTIVE");
            list.add(item);
        }
        return list;
    }
}
