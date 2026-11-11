package com.saasbasics.platform.config;

import com.saasbasics.platform.ratelimit.GlobalApiRateLimitInterceptor;
import com.saasbasics.platform.security.DataPermissionInterceptor;
import com.saasbasics.platform.security.PermissionGuardInterceptor;
import com.saasbasics.platform.security.TenantFeatureGuardInterceptor;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class WebConfig implements WebMvcConfigurer {

    private final SecurityProperties securityProperties;
    private final DataPermissionInterceptor dataPermissionInterceptor;
    private final TenantFeatureGuardInterceptor tenantFeatureGuardInterceptor;
    private final PermissionGuardInterceptor permissionGuardInterceptor;
    private final GlobalApiRateLimitInterceptor globalApiRateLimitInterceptor;

    public WebConfig(SecurityProperties securityProperties,
                     DataPermissionInterceptor dataPermissionInterceptor,
                     TenantFeatureGuardInterceptor tenantFeatureGuardInterceptor,
                     PermissionGuardInterceptor permissionGuardInterceptor,
                     GlobalApiRateLimitInterceptor globalApiRateLimitInterceptor) {
        this.securityProperties = securityProperties;
        this.dataPermissionInterceptor = dataPermissionInterceptor;
        this.tenantFeatureGuardInterceptor = tenantFeatureGuardInterceptor;
        this.permissionGuardInterceptor = permissionGuardInterceptor;
        this.globalApiRateLimitInterceptor = globalApiRateLimitInterceptor;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = new ArrayList<>();
        if (securityProperties.getCorsAllowedOrigins() != null) {
            origins.addAll(securityProperties.getCorsAllowedOrigins());
        }
        // Always support local dev & mobile preview patterns
        origins.add("http://localhost:*");
        origins.add("http://*.localhost:*");
        origins.add("http://127.0.0.1:*");
        origins.add("http://[::1]:*");
        origins.add("https://localhost:*");
        origins.add("https://*.localhost:*");
        origins.add("https://127.0.0.1:*");
        origins.add("http://192.168.*.*:*");
        origins.add("http://10.*.*.*:*");

        registry.addMapping("/**")
                .allowedOriginPatterns(origins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders(
                        "X-Transport-Encrypted",
                        "X-Transport-Mode",
                        "X-Transport-Policy-Version",
                        "X-Transport-Alg",
                        "X-RateLimit-Limit",
                        "X-RateLimit-Remaining",
                        "X-RateLimit-Reset",
                        "X-Trace-Id",
                        "traceparent"
                )
                .allowCredentials(true);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(globalApiRateLimitInterceptor).addPathPatterns("/api/**");
        registry.addInterceptor(dataPermissionInterceptor).addPathPatterns("/api/**");
        registry.addInterceptor(tenantFeatureGuardInterceptor).addPathPatterns("/api/**");
        registry.addInterceptor(permissionGuardInterceptor).addPathPatterns("/api/**");
    }
}
