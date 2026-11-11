package com.saasbasics.platform.security.transport;

/**
 * 请求与响应传输密文信封数据模型。
 */
public final class TransportCryptoEnvelope {

    private TransportCryptoEnvelope() {
    }

    public record RequestEnvelope(
            String alg,
            String kid,
            String epk,
            String iv,
            Long ts,
            String nonce,
            String cipherText
    ) {
    }

    public record ResponseEnvelope(
            boolean encrypted,
            String alg,
            String kid,
            String iv,
            long ts,
            String cipherText
    ) {
    }
}
