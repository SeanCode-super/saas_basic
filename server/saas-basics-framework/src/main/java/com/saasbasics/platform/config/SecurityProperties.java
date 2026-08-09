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
        private String mockTenantCode = "platform";
        private String mockUsername = "platform.admin";
        private String mockPassword = "Admin@123456";

        public long getSessionTtlMinutes() {
            return sessionTtlMinutes;
        }

        public void setSessionTtlMinutes(long sessionTtlMinutes) {
            this.sessionTtlMinutes = sessionTtlMinutes;
        }

        public String getMockTenantCode() {
            return mockTenantCode;
        }

        public void setMockTenantCode(String mockTenantCode) {
            this.mockTenantCode = mockTenantCode;
        }

        public String getMockUsername() {
            return mockUsername;
        }

        public void setMockUsername(String mockUsername) {
            this.mockUsername = mockUsername;
        }

        public String getMockPassword() {
            return mockPassword;
        }

        public void setMockPassword(String mockPassword) {
            this.mockPassword = mockPassword;
        }
    }
}
