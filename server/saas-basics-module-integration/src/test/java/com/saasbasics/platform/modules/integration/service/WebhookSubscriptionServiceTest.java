package com.saasbasics.platform.modules.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.modules.integration.dto.WebhookDtos.WebhookDeliveryResponse;
import com.saasbasics.platform.modules.integration.dto.WebhookDtos.WebhookSubscriptionResponse;
import com.saasbasics.platform.modules.integration.dto.WebhookDtos.WebhookSubscriptionSaveRequest;
import com.saasbasics.platform.modules.integration.entity.WebhookDeliveryLogEntity;
import com.saasbasics.platform.modules.integration.entity.WebhookSubscriptionEntity;
import com.saasbasics.platform.modules.integration.mapper.WebhookDeliveryLogMapper;
import com.saasbasics.platform.modules.integration.mapper.WebhookSubscriptionMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class WebhookSubscriptionServiceTest {

    private static <T> ObjectProvider<T> providerOf(T instance) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) { return instance; }
            @Override
            public T getIfAvailable() { return instance; }
            @Override
            public T getIfUnique() { return instance; }
            @Override
            public T getObject() { return instance; }
        };
    }

    @Test
    void shouldDeliverSignedCloudEventAndSupportReplayAfterFailure() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);
        AtomicReference<String> receivedBody = new AtomicReference<>();
        AtomicReference<String> receivedSignature = new AtomicReference<>();
        AtomicReference<String> receivedSpecVersion = new AtomicReference<>();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hooks/events", exchange -> {
            byte[] bytes = exchange.getRequestBody().readAllBytes();
            receivedBody.set(new String(bytes, StandardCharsets.UTF_8));
            receivedSignature.set(exchange.getRequestHeaders().getFirst("X-Webhook-Signature-256"));
            receivedSpecVersion.set(exchange.getRequestHeaders().getFirst("ce-specversion"));
            int status = callCount.incrementAndGet() == 1 ? 503 : 200;
            byte[] resp = (status == 200 ? "ok" : "unavailable").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        server.start();

        try {
            WebhookSubscriptionMapper subMapper = mock(WebhookSubscriptionMapper.class);
            WebhookDeliveryLogMapper logMapper = mock(WebhookDeliveryLogMapper.class);
            AtomicReference<WebhookSubscriptionEntity> storedSub = new AtomicReference<>();
            AtomicReference<WebhookDeliveryLogEntity> storedLog = new AtomicReference<>();

            doAnswer(inv -> {
                WebhookSubscriptionEntity entity = inv.getArgument(0);
                entity.setId(101L);
                storedSub.set(entity);
                return 1;
            }).when(subMapper).insert(any(WebhookSubscriptionEntity.class));

            doAnswer(inv -> {
                WebhookDeliveryLogEntity logEntity = inv.getArgument(0);
                logEntity.setId(501L);
                storedLog.set(logEntity);
                return 1;
            }).when(logMapper).insert(any(WebhookDeliveryLogEntity.class));

            when(subMapper.selectOne(any())).thenAnswer(inv -> storedSub.get());
            when(logMapper.selectOne(any())).thenAnswer(inv -> storedLog.get());

            ObjectMapper om = new ObjectMapper();
            WebhookSubscriptionService service = new WebhookSubscriptionService(
                    providerOf(subMapper),
                    providerOf(logMapper),
                    providerOf(null),
                    om,
                    null
            );

            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/hooks/events";
            WebhookSubscriptionResponse subResp = service.createSubscription(new WebhookSubscriptionSaveRequest(
                    "Order Events",
                    endpoint,
                    "crm.customer.created",
                    "whsec_unit_test_secret_1234",
                    3000,
                    3
            ));
            assertThat(subResp.rawSecretOnce()).isEqualTo("whsec_unit_test_secret_1234");
            assertThat(subResp.maskedSecret()).startsWith("whsec_").contains("****");

            WebhookDeliveryResponse first = service.sendTestEvent(101L, "crm.customer.created");
            assertThat(first.status()).isEqualTo("FAILED");
            assertThat(first.httpStatus()).isEqualTo(503);
            assertThat(receivedSpecVersion.get()).isEqualTo("1.0");
            assertThat(receivedSignature.get()).isEqualTo(
                    WebhookSubscriptionService.computeHmacSha256Signature("whsec_unit_test_secret_1234", receivedBody.get())
            );
            JsonNode envelopeJson = om.readTree(receivedBody.get());
            assertThat(envelopeJson.path("specversion").asText()).isEqualTo("1.0");
            assertThat(envelopeJson.path("type").asText()).isEqualTo("crm.customer.created");

            WebhookDeliveryResponse replayed = service.replayDelivery(501L);
            assertThat(replayed.status()).isEqualTo("SUCCESS");
            assertThat(replayed.httpStatus()).isEqualTo(200);
            assertThat(replayed.retryCount()).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }
}
