package com.saasbasics.platform.modules.file.storage;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Pure Java AWS Signature Version 4 (AWS4-HMAC-SHA256) request & presigned URL signer
 * compatible with AWS S3, MinIO, Cloudflare R2, and S3-compatible object storage gateways.
 */
public final class AwsSigV4Signer {

    private static final String ALGORITHM = "AWS4-HMAC-SHA256";
    private static final String SERVICE = "s3";
    private static final DateTimeFormatter AMZ_DATE_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_STAMP_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private AwsSigV4Signer() {
    }

    public static Map<String, String> signHeaders(
            String method,
            URI uri,
            Map<String, String> baseHeaders,
            byte[] payload,
            String accessKey,
            String secretKey,
            String region
    ) {
        return signHeaders(method, uri, baseHeaders, payload, accessKey, secretKey, region, Instant.now());
    }

    public static Map<String, String> signHeaders(
            String method,
            URI uri,
            Map<String, String> baseHeaders,
            byte[] payload,
            String accessKey,
            String secretKey,
            String region,
            Instant now
    ) {
        String effectiveRegion = (region == null || region.isBlank()) ? "us-east-1" : region.trim();
        String amzDate = AMZ_DATE_FMT.format(now);
        String dateStamp = DATE_STAMP_FMT.format(now);
        String payloadHash = sha256Hex(payload != null ? payload : new byte[0]);
        String hostHeader = formatHostHeader(uri);

        TreeMap<String, String> canonicalHeadersMap = new TreeMap<>();
        canonicalHeadersMap.put("host", hostHeader);
        canonicalHeadersMap.put("x-amz-content-sha256", payloadHash);
        canonicalHeadersMap.put("x-amz-date", amzDate);
        if (baseHeaders != null) {
            baseHeaders.forEach((k, v) -> {
                if (k != null && v != null && !k.equalsIgnoreCase("authorization")) {
                    canonicalHeadersMap.put(k.toLowerCase(Locale.ROOT).trim(), v.trim());
                }
            });
        }

        StringBuilder canonicalHeaders = new StringBuilder();
        StringBuilder signedHeaders = new StringBuilder();
        for (Map.Entry<String, String> entry : canonicalHeadersMap.entrySet()) {
            canonicalHeaders.append(entry.getKey()).append(':').append(entry.getValue()).append('\n');
            if (signedHeaders.length() > 0) {
                signedHeaders.append(';');
            }
            signedHeaders.append(entry.getKey());
        }

        String canonicalUri = normalizePath(uri.getRawPath());
        String canonicalQuery = buildCanonicalQuery(uri.getRawQuery());
        String canonicalRequest = method.toUpperCase(Locale.ROOT) + "\n"
                + canonicalUri + "\n"
                + canonicalQuery + "\n"
                + canonicalHeaders + "\n"
                + signedHeaders + "\n"
                + payloadHash;

        String scope = dateStamp + "/" + effectiveRegion + "/" + SERVICE + "/aws4_request";
        String stringToSign = ALGORITHM + "\n" + amzDate + "\n" + scope + "\n" + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));
        byte[] signingKey = deriveSigningKey(secretKey, dateStamp, effectiveRegion, SERVICE);
        String signature = hmacSha256Hex(signingKey, stringToSign);

        String authorization = ALGORITHM + " Credential=" + accessKey + "/" + scope
                + ", SignedHeaders=" + signedHeaders
                + ", Signature=" + signature;

        Map<String, String> out = new LinkedHashMap<>();
        out.put("Host", hostHeader);
        out.put("x-amz-date", amzDate);
        out.put("x-amz-content-sha256", payloadHash);
        out.put("Authorization", authorization);
        return out;
    }

    public static String presignUrl(
            String method,
            URI uri,
            String accessKey,
            String secretKey,
            String region,
            int expireSeconds
    ) {
        return presignUrl(method, uri, accessKey, secretKey, region, expireSeconds, Instant.now());
    }

    public static String presignUrl(
            String method,
            URI uri,
            String accessKey,
            String secretKey,
            String region,
            int expireSeconds,
            Instant now
    ) {
        return presignUrl(method, uri, accessKey, secretKey, region, expireSeconds, now, null);
    }

    static String presignUrl(String method, URI uri, String accessKey, String secretKey, String region,
                             int expireSeconds, Instant now, Long contentLength) {
        String effectiveRegion = (region == null || region.isBlank()) ? "us-east-1" : region.trim();
        int safeExpires = Math.min(Math.max(expireSeconds, 60), 604800);
        String amzDate = AMZ_DATE_FMT.format(now);
        String dateStamp = DATE_STAMP_FMT.format(now);
        String hostHeader = formatHostHeader(uri);
        String scope = dateStamp + "/" + effectiveRegion + "/" + SERVICE + "/aws4_request";

        TreeMap<String, String> queryParams = new TreeMap<>();
        if (uri.getRawQuery() != null && !uri.getRawQuery().isBlank()) {
            for (String pair : uri.getRawQuery().split("&")) {
                int idx = pair.indexOf('=');
                if (idx > 0) {
                    queryParams.put(pair.substring(0, idx), pair.substring(idx + 1));
                }
            }
        }
        queryParams.put("X-Amz-Algorithm", urlEncode(ALGORITHM));
        queryParams.put("X-Amz-Credential", urlEncode(accessKey + "/" + scope));
        queryParams.put("X-Amz-Date", urlEncode(amzDate));
        queryParams.put("X-Amz-Expires", String.valueOf(safeExpires));
        String signedHeaders = contentLength == null ? "host" : "content-length;host";
        queryParams.put("X-Amz-SignedHeaders", urlEncode(signedHeaders));

        StringBuilder canonicalQuery = new StringBuilder();
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            if (canonicalQuery.length() > 0) {
                canonicalQuery.append('&');
            }
            canonicalQuery.append(entry.getKey()).append('=').append(entry.getValue());
        }

        String canonicalRequest = method.toUpperCase(Locale.ROOT) + "\n"
                + normalizePath(uri.getRawPath()) + "\n"
                + canonicalQuery + "\n"
                + (contentLength == null ? "" : "content-length:" + contentLength + "\n")
                + "host:" + hostHeader + "\n\n"
                + signedHeaders + "\n"
                + "UNSIGNED-PAYLOAD";

        String stringToSign = ALGORITHM + "\n" + amzDate + "\n" + scope + "\n"
                + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));
        byte[] signingKey = deriveSigningKey(secretKey, dateStamp, effectiveRegion, SERVICE);
        String signature = hmacSha256Hex(signingKey, stringToSign);

        String baseUrl = uri.getScheme() + "://" + uri.getRawAuthority() + normalizePath(uri.getRawPath());
        return baseUrl + "?" + canonicalQuery + "&X-Amz-Signature=" + signature;
    }

    private static String formatHostHeader(URI uri) {
        int port = uri.getPort();
        if (port == -1 || ("http".equalsIgnoreCase(uri.getScheme()) && port == 80)
                || ("https".equalsIgnoreCase(uri.getScheme()) && port == 443)) {
            return uri.getHost();
        }
        return uri.getHost() + ":" + port;
    }

    private static String normalizePath(String rawPath) {
        if (rawPath == null || rawPath.isEmpty()) {
            return "/";
        }
        return rawPath.startsWith("/") ? rawPath : "/" + rawPath;
    }

    private static String buildCanonicalQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return "";
        }
        TreeMap<String, String> sorted = new TreeMap<>();
        for (String part : rawQuery.split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                sorted.put(part.substring(0, eq), part.substring(eq + 1));
            } else if (!part.isEmpty()) {
                sorted.put(part, "");
            }
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return sb.toString();
    }

    private static byte[] deriveSigningKey(String secretKey, String dateStamp, String region, String service) {
        byte[] kSecret = ("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8);
        byte[] kDate = hmacSha256Raw(kSecret, dateStamp);
        byte[] kRegion = hmacSha256Raw(kDate, region);
        byte[] kService = hmacSha256Raw(kRegion, service);
        return hmacSha256Raw(kService, "aws4_request");
    }

    private static byte[] hmacSha256Raw(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("HmacSHA256 calculation failed", ex);
        }
    }

    private static String hmacSha256Hex(byte[] key, String data) {
        return HexFormat.of().formatHex(hmacSha256Raw(key, data));
    }

    public static String sha256Hex(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 calculation failed", ex);
        }
    }

    private static String urlEncode(String val) {
        return URLEncoder.encode(val, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }
}
