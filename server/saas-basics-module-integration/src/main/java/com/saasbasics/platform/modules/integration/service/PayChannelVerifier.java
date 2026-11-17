package com.saasbasics.platform.modules.integration.service;

import com.saasbasics.platform.common.exception.BizException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * 支付异步回调验签器：支持 RSA2 (SHA256withRSA) 平台公钥验签与 HMAC-SHA256 签名验证，
 * 生产环境严禁缺少秘钥回退默认值。
 */
@Component
public class PayChannelVerifier {

    private static final String DEV_FALLBACK_WEBHOOK_SECRET = "saas-dev-pay-webhook-secret";

    private final Environment environment;

    public PayChannelVerifier(Environment environment) {
        this.environment = environment;
    }

    public void verifyOrThrow(String channel, String rawBody, String signature, String timestamp) {
        if (rawBody == null || rawBody.isBlank()) {
            throw new BizException("PAY_NOTIFY_INVALID", "支付回调报文不能为空");
        }
        if (signature == null || signature.isBlank()) {
            throw new BizException("PAY_NOTIFY_SIGN_MISSING", "缺少支付回调签名 Header (X-Pay-Signature)");
        }
        String ch = channel != null ? channel.trim().toUpperCase(Locale.ROOT) : "SANDBOX";
        String rsaPubPem = environment != null
                ? environment.getProperty("saas.pay." + ch.toLowerCase(Locale.ROOT) + ".public-key", "")
                : "";
        if (!rsaPubPem.isBlank()) {
            String message = (timestamp != null && !timestamp.isBlank())
                    ? timestamp.trim() + "\n" + rawBody
                    : rawBody;
            if (!verifyRsaSha256(rsaPubPem, message, signature.trim())) {
                throw new BizException("PAY_NOTIFY_SIGN_INVALID", ch + " RSA2 支付回调验签失败");
            }
            return;
        }

        String secret = environment != null ? environment.getProperty("saas.pay.webhook-secret", "") : "";
        boolean isProd = environment != null && environment.acceptsProfiles(Profiles.of("prod"));
        if (secret.isBlank()) {
            if (isProd) {
                throw new BizException("PAY_WEBHOOK_SECRET_MISSING", "生产环境必须配置 saas.pay.webhook-secret 或渠道公钥");
            }
            secret = DEV_FALLBACK_WEBHOOK_SECRET;
        }
        String signingInput = (timestamp != null && !timestamp.isBlank()) ? timestamp.trim() + "." + rawBody : rawBody;
        String expectedHex = hmacSha256Hex(secret, signingInput);
        if (!MessageDigest.isEqual(
                expectedHex.getBytes(StandardCharsets.UTF_8),
                signature.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8))) {
            throw new BizException("PAY_NOTIFY_SIGN_INVALID", "支付回调 HMAC-SHA256 签名校验未通过");
        }
    }

    public static String hmacSha256Hex(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", ex);
        }
    }

    private static boolean verifyRsaSha256(String pem, String message, String base64Sig) {
        try {
            String clean = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] keyBytes = Base64.getDecoder().decode(clean);
            PublicKey pub = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(pub);
            sig.update(message.getBytes(StandardCharsets.UTF_8));
            return sig.verify(Base64.getDecoder().decode(base64Sig));
        } catch (Exception ex) {
            return false;
        }
    }
}
