package com.saasbasics.platform.security.transport;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 传输报文加密启动期默认配置属性（运行期可通过 DB 持久化与控制台热更新覆盖）。
 */
@ConfigurationProperties(prefix = "saas-basics.security.transport")
public class TransportCryptoProperties {

    private boolean enabled = true;
    private TransportCryptoMode mode = TransportCryptoMode.OPTIONAL;
    private TransportScopeMode scopeMode = TransportScopeMode.ALL_API;
    private boolean encryptRequest = true;
    private boolean encryptResponse = true;
    private boolean encryptQuery = true;
    private boolean replayProtection = true;
    private int timestampSkewSeconds = 300;
    private int keyRotationHours = 24;
    private int maxGraceKeys = 3;

    private List<String> includePaths = new ArrayList<>(List.of("/api/**"));
    private List<String> excludePaths = new ArrayList<>(List.of(
            "/api/security/transport/policy",
            "/api/health/**",
            "/api/ws/**",
            "/api/file/download/**",
            "/api/file/preview/**",
            "/api/file/upload/**",
            "/actuator/**",
            "/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/doc.html",
            "/webjars/**"
    ));
    private List<String> sensitivePaths = new ArrayList<>(List.of(
            "/api/auth/login",
            "/api/auth/refresh-token",
            "/api/auth/sso/**",
            "/api/iam/**",
            "/api/system/**",
            "/api/ai/**",
            "/api/tenant/**"
    ));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public TransportCryptoMode getMode() {
        return mode;
    }

    public void setMode(TransportCryptoMode mode) {
        this.mode = mode;
    }

    public TransportScopeMode getScopeMode() {
        return scopeMode;
    }

    public void setScopeMode(TransportScopeMode scopeMode) {
        this.scopeMode = scopeMode;
    }

    public boolean isEncryptRequest() {
        return encryptRequest;
    }

    public void setEncryptRequest(boolean encryptRequest) {
        this.encryptRequest = encryptRequest;
    }

    public boolean isEncryptResponse() {
        return encryptResponse;
    }

    public void setEncryptResponse(boolean encryptResponse) {
        this.encryptResponse = encryptResponse;
    }

    public boolean isEncryptQuery() {
        return encryptQuery;
    }

    public void setEncryptQuery(boolean encryptQuery) {
        this.encryptQuery = encryptQuery;
    }

    public boolean isReplayProtection() {
        return replayProtection;
    }

    public void setReplayProtection(boolean replayProtection) {
        this.replayProtection = replayProtection;
    }

    public int getTimestampSkewSeconds() {
        return timestampSkewSeconds;
    }

    public void setTimestampSkewSeconds(int timestampSkewSeconds) {
        this.timestampSkewSeconds = timestampSkewSeconds;
    }

    public int getKeyRotationHours() {
        return keyRotationHours;
    }

    public void setKeyRotationHours(int keyRotationHours) {
        this.keyRotationHours = keyRotationHours;
    }

    public int getMaxGraceKeys() {
        return maxGraceKeys;
    }

    public void setMaxGraceKeys(int maxGraceKeys) {
        this.maxGraceKeys = maxGraceKeys;
    }

    public List<String> getIncludePaths() {
        return includePaths;
    }

    public void setIncludePaths(List<String> includePaths) {
        this.includePaths = includePaths;
    }

    public List<String> getExcludePaths() {
        return excludePaths;
    }

    public void setExcludePaths(List<String> excludePaths) {
        this.excludePaths = excludePaths;
    }

    public List<String> getSensitivePaths() {
        return sensitivePaths;
    }

    public void setSensitivePaths(List<String> sensitivePaths) {
        this.sensitivePaths = sensitivePaths;
    }
}
