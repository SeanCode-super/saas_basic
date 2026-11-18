package com.saasbasics.platform.modules.file.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saasbasics.platform.common.exception.BizException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AwsSigV4SignerTest {

    @Test
    void generatesDeterministicSigV4HeadersAndPresignedUrls() {
        URI uri = URI.create("https://s3.example.com/my-bucket/docs/report.pdf");
        Instant fixed = Instant.parse("2026-11-18T00:00:00Z");

        Map<String, String> headers = AwsSigV4Signer.signHeaders(
                "PUT",
                uri,
                Map.of("Content-Type", "application/pdf"),
                "hello-s3".getBytes(StandardCharsets.UTF_8),
                "AKIAEXAMPLE",
                "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
                "us-east-1",
                fixed
        );

        assertThat(headers.get("x-amz-date")).isEqualTo("20261118T000000Z");
        assertThat(headers.get("x-amz-content-sha256")).hasSize(64);
        assertThat(headers.get("Authorization"))
                .startsWith("AWS4-HMAC-SHA256 Credential=AKIAEXAMPLE/20261118/us-east-1/s3/aws4_request, SignedHeaders=")
                .contains("Signature=");

        String presigned = AwsSigV4Signer.presignUrl(
                "PUT", uri, "AKIAEXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY", "ap-southeast-1", 900, fixed);
        assertThat(presigned)
                .contains("X-Amz-Algorithm=AWS4-HMAC-SHA256")
                .contains("X-Amz-Expires=900")
                .contains("X-Amz-Signature=");
    }

    @Test
    void s3ClientSendsSigV4HeadersAndFailsClosedOnHttpError() throws Exception {
        AtomicReference<String> capturedAuth = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/test-bucket/ok.txt", exchange -> {
            capturedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/test-bucket/forbidden.txt", exchange -> {
            exchange.sendResponseHeaders(403, -1);
            exchange.close();
        });
        server.start();
        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
            S3CompatibleFileClient client = new S3CompatibleFileClient(
                    endpoint, "test-bucket", "AKID_DEMO", "SECRET_DEMO", null);

            String url = client.upload("signed-payload".getBytes(StandardCharsets.UTF_8), "ok.txt", "text/plain");
            assertThat(url).isEqualTo(endpoint + "/test-bucket/ok.txt");
            assertThat(capturedAuth.get()).startsWith("AWS4-HMAC-SHA256 Credential=AKID_DEMO/");

            assertThatThrownBy(() -> client.upload("bad".getBytes(StandardCharsets.UTF_8), "forbidden.txt", "text/plain"))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getCode())
                    .isEqualTo("FILE_STORAGE_UPLOAD_FAILED");
        } finally {
            server.stop(0);
        }
    }
}
