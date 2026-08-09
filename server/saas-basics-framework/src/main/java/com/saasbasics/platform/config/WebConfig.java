package com.saasbasics.platform.config;

import com.saasbasics.platform.security.PermissionGuardInterceptor;
import com.saasbasics.platform.security.DataPermissionInterceptor;
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
    private final PermissionGuardInterceptor permissionGuardInterceptor;

    public WebConfig(SecurityProperties securityProperties,
                     DataPermissionInterceptor dataPermissionInterceptor,
                     PermissionGuardInterceptor permissionGuardInterceptor) {
        this.securityProperties = securityProperties;
        this.dataPermissionInterceptor = dataPermissionInterceptor;
        this.permissionGuardInterceptor = permissionGuardInterceptor;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(securityProperties.getCorsAllowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(dataPermissionInterceptor).addPathPatterns("/api/**");
        registry.addInterceptor(permissionGuardInterceptor).addPathPatterns("/api/**");
    }
}
