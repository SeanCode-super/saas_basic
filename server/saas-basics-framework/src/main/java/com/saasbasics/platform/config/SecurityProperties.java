package com.saasbasics.platform.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "saas-basics.security")
public class SecurityProperties {

    private List<String> corsAllowedOrigins = new ArrayList<>();
    private AuthProperties auth = new AuthProperties();

    public List<String> getCorsAllowedOrigins() {
        return corsAllowedOrigins;
    }

    public void setCorsAllowedOrigins(List<String> corsAllowedOrigins) {
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    public AuthProperties getAuth() {
        return auth;
    }

    public void setAuth(AuthProperties auth) {
        this.auth = auth;
    }

    public static class AuthProperties {

        private long sessionTtlMinutes = 480;
        private boolean strictServerCaptcha = false;
        private boolean strictSsoValidation = false;

        public long getSessionTtlMinutes() {
            return sessionTtlMinutes;
        }

        public void setSessionTtlMinutes(long sessionTtlMinutes) {
            this.sessionTtlMinutes = sessionTtlMinutes;
        }

        public boolean isStrictServerCaptcha() {
            return strictServerCaptcha;
        }

        public void setStrictServerCaptcha(boolean strictServerCaptcha) {
            this.strictServerCaptcha = strictServerCaptcha;
        }

        public boolean isStrictSsoValidation() {
            return strictSsoValidation;
        }

        public void setStrictSsoValidation(boolean strictSsoValidation) {
            this.strictSsoValidation = strictSsoValidation;
        }
    }
}
