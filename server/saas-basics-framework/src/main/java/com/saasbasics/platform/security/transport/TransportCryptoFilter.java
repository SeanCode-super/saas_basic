package com.saasbasics.platform.security.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.api.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * 可控全链路传输报文加解密过滤器。
 * <p>
 * 执行顺序位于 {@code TraceFilter} 之后、{@code AuthContextFilter} 之前，从而可覆盖登录/刷新令牌、
 * 错误响应以及全部业务 JSON API；并原生兼容 Query 参数加密与多版本策略响应头自动同步。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class TransportCryptoFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TransportCryptoFilter.class);

    public static final String HEADER_ENCRYPTED = "X-Transport-Encrypted";
    public static final String HEADER_MODE = "X-Transport-Mode";
    public static final String HEADER_POLICY_VERSION = "X-Transport-Policy-Version";
    public static final String HEADER_ALG = "X-Transport-Alg";
    public static final String HEADER_KID = "X-Enc-Kid";
    public static final String HEADER_EPK = "X-Enc-Epk";
    public static final String HEADER_TS = "X-Enc-Ts";
    public static final String HEADER_NONCE = "X-Enc-Nonce";
    public static final String HEADER_ENC_QUERY = "X-Enc-Query";

    private final TransportCryptoPolicyService policyService;
    private final TransportCryptoService cryptoService;
    private final ObjectMapper objectMapper;

    public TransportCryptoFilter(TransportCryptoPolicyService policyService,
                                 TransportCryptoService cryptoService,
                                 ObjectMapper objectMapper) {
        this.policyService = policyService;
        this.cryptoService = cryptoService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        TransportCryptoPolicySnapshot policy = policyService.getCurrentPolicy();
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/api/")) {
            response.setHeader(HEADER_MODE, policy.mode().name());
            response.setHeader(HEADER_POLICY_VERSION, String.valueOf(policy.policyVersion()));
            response.setHeader(HEADER_ALG, policy.algorithm());
        }

        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || uri == null || !uri.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        TransportCryptoPolicyService.EndpointAction action = policyService.resolveEndpointAction(request);
        boolean clientSentEncrypted = "1".equals(request.getHeader(HEADER_ENCRYPTED))
                || "true".equalsIgnoreCase(request.getHeader(HEADER_ENCRYPTED));

        if (!clientSentEncrypted) {
            if (action == TransportCryptoPolicyService.EndpointAction.REQUIRED) {
                policyService.recordOutcome("rejected_plaintext");
                writeCryptoError(response, 428, "TRANSPORT_ENCRYPTION_REQUIRED",
                        "系统安全策略已强制开启接口传输加密，请刷新配置或使用受控客户端加密调用");
                return;
            }
            if (action != TransportCryptoPolicyService.EndpointAction.BYPASS) {
                policyService.recordOutcome("plaintext");
            }
            filterChain.doFilter(request, response);
            return;
        }

        // 客户端发送了密文请求，即使刚切换为 OFF 也平滑完成本次解密与协商同步
        TransportCryptoService.DerivedKeySession session;
        DecryptedHttpServletRequestWrapper wrappedRequest;
        try {
            byte[] rawRequestBody = StreamUtils.copyToByteArray(request.getInputStream());
            String rawBodyStr = rawRequestBody.length > 0 ? new String(rawRequestBody, StandardCharsets.UTF_8).trim() : "";

            TransportCryptoEnvelope.RequestEnvelope bodyEnvelope = null;
            if (!rawBodyStr.isEmpty() && rawBodyStr.startsWith("{")) {
                try {
                    TransportCryptoEnvelope.RequestEnvelope parsed = objectMapper.readValue(
                            rawBodyStr, TransportCryptoEnvelope.RequestEnvelope.class);
                    if (parsed != null && parsed.cipherText() != null && !parsed.cipherText().isBlank()) {
                        bodyEnvelope = parsed;
                    }
                } catch (Exception ignored) {
                    // 非加密信封 JSON，后续按策略决定是否允许明文请求体透传
                }
            }

            if (bodyEnvelope == null && !rawBodyStr.isEmpty() && policy.encryptRequest()
                    && action == TransportCryptoPolicyService.EndpointAction.REQUIRED) {
                throw new TransportCryptoException(428, "TRANSPORT_ENCRYPTION_REQUIRED",
                        "安全策略要求请求报文体必须加密传输");
            }

            String kid = firstNonBlank(request.getHeader(HEADER_KID), bodyEnvelope != null ? bodyEnvelope.kid() : null);
            String epk = firstNonBlank(request.getHeader(HEADER_EPK), bodyEnvelope != null ? bodyEnvelope.epk() : null);
            Long ts = parseTimestamp(request.getHeader(HEADER_TS), bodyEnvelope != null ? bodyEnvelope.ts() : null);
            String nonce = firstNonBlank(request.getHeader(HEADER_NONCE), bodyEnvelope != null ? bodyEnvelope.nonce() : null);

            session = cryptoService.establishSession(
                    kid,
                    epk,
                    ts,
                    nonce,
                    policy.replayProtection(),
                    policy.timestampSkewSeconds()
            );

            byte[] decryptedBody = rawRequestBody;
            if (bodyEnvelope != null) {
                String reqAad = TransportCryptoService.buildAad(
                        "REQ", request.getMethod(), uri, session.keyId(), session.timestamp(), session.nonce());
                decryptedBody = cryptoService.decryptAesGcm(
                        session.requestKey(), bodyEnvelope.iv(), bodyEnvelope.cipherText(), reqAad);
            }

            Map<String, String[]> decryptedQueryParams = decodeEncryptedQuery(
                    request.getHeader(HEADER_ENC_QUERY),
                    session,
                    request.getMethod(),
                    uri
            );

            wrappedRequest = new DecryptedHttpServletRequestWrapper(request, decryptedBody, decryptedQueryParams);
        } catch (TransportCryptoException ex) {
            if ("TRANSPORT_REPLAY_DETECTED".equals(ex.getCode()) || "TRANSPORT_TIMESTAMP_EXPIRED".equals(ex.getCode())) {
                policyService.recordOutcome("rejected_replay");
            } else {
                policyService.recordOutcome("decrypt_error");
            }
            writeCryptoError(response, ex.getHttpStatus(), ex.getCode(), ex.getMessage());
            return;
        } catch (Exception ex) {
            log.warn("[TransportCrypto] 请求解析或解密异常: uri={}, err={}", uri, ex.getMessage());
            policyService.recordOutcome("decrypt_error");
            writeCryptoError(response, 400, "TRANSPORT_DECRYPT_FAILED", "加密请求报文格式无效或解密失败");
            return;
        }

        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        try {
            policyService.recordOutcome("encrypted");
            filterChain.doFilter(wrappedRequest, wrappedResponse);
            writeEncryptedOrPlainResponse(request, wrappedResponse, session, policy);
        } finally {
            session.zeroize();
        }
    }

    private void writeEncryptedOrPlainResponse(HttpServletRequest request,
                                               ContentCachingResponseWrapper wrappedResponse,
                                               TransportCryptoService.DerivedKeySession session,
                                               TransportCryptoPolicySnapshot policy) throws IOException {
        byte[] rawResp = wrappedResponse.getContentAsByteArray();
        String respContentType = wrappedResponse.getContentType();
        boolean isStream = respContentType != null && respContentType.toLowerCase().contains("text/event-stream");
        boolean isJsonLike = respContentType == null
                || respContentType.toLowerCase().contains("json")
                || respContentType.toLowerCase().startsWith("text/plain");

        if (!policy.encryptResponse() || rawResp.length == 0 || isStream || !isJsonLike) {
            wrappedResponse.copyBodyToResponse();
            return;
        }

        TransportCryptoEnvelope.ResponseEnvelope encryptedEnvelope = cryptoService.encryptResponse(
                session,
                request.getMethod(),
                request.getRequestURI(),
                rawResp
        );
        byte[] envelopeBytes = objectMapper.writeValueAsBytes(encryptedEnvelope);
        wrappedResponse.resetBuffer();
        wrappedResponse.setHeader(HEADER_ENCRYPTED, "1");
        wrappedResponse.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
        wrappedResponse.setContentLength(envelopeBytes.length);
        wrappedResponse.getOutputStream().write(envelopeBytes);
        wrappedResponse.copyBodyToResponse();
    }

    private Map<String, String[]> decodeEncryptedQuery(String headerValue,
                                                       TransportCryptoService.DerivedKeySession session,
                                                       String method,
                                                       String uri) {
        if (headerValue == null || headerValue.isBlank()) {
            return Collections.emptyMap();
        }
        int dotIdx = headerValue.indexOf('.');
        if (dotIdx <= 0 || dotIdx >= headerValue.length() - 1) {
            throw new TransportCryptoException(400, "TRANSPORT_DECRYPT_FAILED", "加密查询参数信封格式错误");
        }
        String iv = headerValue.substring(0, dotIdx);
        String cipherText = headerValue.substring(dotIdx + 1);
        String qryAad = TransportCryptoService.buildAad("QRY", method, uri, session.keyId(), session.timestamp(), session.nonce());
        byte[] queryBytes = cryptoService.decryptAesGcm(session.requestKey(), iv, cipherText, qryAad);
        String queryString = new String(queryBytes, StandardCharsets.UTF_8);

        Map<String, String[]> parsed = new LinkedHashMap<>();
        for (String pair : queryString.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eqIdx = pair.indexOf('=');
            String k = URLDecoder.decode(eqIdx >= 0 ? pair.substring(0, eqIdx) : pair, StandardCharsets.UTF_8);
            String v = eqIdx >= 0 ? URLDecoder.decode(pair.substring(eqIdx + 1), StandardCharsets.UTF_8) : "";
            parsed.merge(k, new String[]{v}, (oldArr, newArr) -> {
                String[] merged = new String[oldArr.length + 1];
                System.arraycopy(oldArr, 0, merged, 0, oldArr.length);
                merged[oldArr.length] = newArr[0];
                return merged;
            });
        }
        return parsed;
    }

    private void writeCryptoError(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setHeader(HEADER_ENCRYPTED, "0");
        response.setContentType("application/json;charset=UTF-8");
        ApiResponse<Void> payload = ApiResponse.failure(code, message);
        response.getWriter().write(objectMapper.writeValueAsString(payload));
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        if (b != null && !b.isBlank()) {
            return b.trim();
        }
        return null;
    }

    private static Long parseTimestamp(String headerTs, Long bodyTs) {
        if (headerTs != null && !headerTs.isBlank()) {
            try {
                return Long.parseLong(headerTs.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return bodyTs;
    }

    /**
     * 解密后的请求包装器：替换请求体字节流与 Query 参数映射表，让 Controller @RequestBody 与 @RequestParam 零改动工作。
     */
    private static final class DecryptedHttpServletRequestWrapper extends HttpServletRequestWrapper {

        private final byte[] body;
        private final Map<String, String[]> params;
        private final String queryString;

        DecryptedHttpServletRequestWrapper(HttpServletRequest request,
                                           byte[] body,
                                           Map<String, String[]> extraDecryptedParams) {
            super(request);
            this.body = body != null ? body : new byte[0];

            Map<String, String[]> merged = new LinkedHashMap<>();
            String rawQuery = request.getQueryString();
            if (rawQuery != null && !rawQuery.isBlank()) {
                for (String pair : rawQuery.split("&")) {
                    if (pair.isEmpty()) {
                        continue;
                    }
                    int eq = pair.indexOf('=');
                    String k = URLDecoder.decode(eq >= 0 ? pair.substring(0, eq) : pair, StandardCharsets.UTF_8);
                    String v = eq >= 0 ? URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8) : "";
                    appendParam(merged, k, v);
                }
            }
            if (extraDecryptedParams != null) {
                for (Map.Entry<String, String[]> entry : extraDecryptedParams.entrySet()) {
                    for (String val : entry.getValue()) {
                        appendParam(merged, entry.getKey(), val);
                    }
                }
            }
            this.params = Collections.unmodifiableMap(merged);
            if (extraDecryptedParams != null && !extraDecryptedParams.isEmpty()) {
                StringBuilder qs = new StringBuilder();
                for (Map.Entry<String, String[]> entry : merged.entrySet()) {
                    for (String val : entry.getValue()) {
                        if (qs.length() > 0) {
                            qs.append('&');
                        }
                        qs.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                                .append('=')
                                .append(URLEncoder.encode(val, StandardCharsets.UTF_8));
                    }
                }
                this.queryString = qs.toString();
            } else {
                this.queryString = rawQuery;
            }
        }

        private static void appendParam(Map<String, String[]> target, String key, String value) {
            target.merge(key, new String[]{value}, (a, b) -> {
                String[] res = new String[a.length + b.length];
                System.arraycopy(a, 0, res, 0, a.length);
                System.arraycopy(b, 0, res, a.length, b.length);
                return res;
            });
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream bais = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return bais.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    // 同步缓冲流无需异步 ReadListener
                }

                @Override
                public int read() {
                    return bais.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }

        @Override
        public String getHeader(String name) {
            if ("Content-Length".equalsIgnoreCase(name)) {
                return String.valueOf(body.length);
            }
            if ("Content-Type".equalsIgnoreCase(name) && body.length > 0) {
                String orig = super.getHeader(name);
                return (orig == null || orig.isBlank()) ? MediaType.APPLICATION_JSON_VALUE : orig;
            }
            return super.getHeader(name);
        }

        @Override
        public String getContentType() {
            if (body.length > 0) {
                String orig = super.getContentType();
                return (orig == null || orig.isBlank()) ? MediaType.APPLICATION_JSON_VALUE : orig;
            }
            return super.getContentType();
        }

        @Override
        public String getQueryString() {
            return queryString;
        }

        @Override
        public String getParameter(String name) {
            String[] values = params.get(name);
            return (values != null && values.length > 0) ? values[0] : null;
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            return params;
        }

        @Override
        public Enumeration<String> getParameterNames() {
            return Collections.enumeration(params.keySet());
        }

        @Override
        public String[] getParameterValues(String name) {
            return params.get(name);
        }
    }
}
