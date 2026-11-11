package com.saasbasics.platform.modules.iam.filter;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.modules.iam.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuthContextFilter extends OncePerRequestFilter {

    private static final Set<String> PUBLIC_PREFIXES = Set.of(
            "/api/auth/login",
            "/api/auth/captcha",
            "/api/auth/refresh-token",
            "/api/auth/sso",
            "/api/portal/entry",
            "/api/security/transport/policy",
            "/api/health/ready",
            "/api/ws",
            "/actuator",
            "/api-docs",
            "/swagger-ui",
            "/swagger-ui.html",
            "/doc.html",
            "/webjars",
            "/error"
    );

    private final AuthService authService;

    public AuthContextFilter(AuthService authService) {
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            if (isPublicRequest(request)) {
                filterChain.doFilter(request, response);
                return;
            }

            AuthPrincipal principal = authService.resolvePrincipal(extractAccessToken(request));
            if (principal == null) {
                writeUnauthorized(response);
                return;
            }
            AuthContext.set(principal);
            request.setAttribute("saas.telemetry.tenantId", principal.tenantId());
            request.setAttribute("saas.telemetry.userId", principal.userId());
            org.slf4j.MDC.put("tenantId", String.valueOf(principal.tenantId()));
            org.slf4j.MDC.put("userId", String.valueOf(principal.userId()));
            try (TenantAccessContextHolder.Scope ignored = TenantAccessContextHolder.openAuthenticated(principal)) {
                filterChain.doFilter(request, response);
            }
        } finally {
            AuthContext.clear();
            TenantAccessContextHolder.clear();
        }
    }

    private boolean isPublicRequest(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        for (String prefix : PUBLIC_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private String extractAccessToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && !authorization.isBlank()) {
            if (authorization.startsWith("Bearer ")) {
                return authorization.substring(7);
            }
            return authorization;
        }
        // Support token in query parameter for EventSource (SSE) & WebSocket
        String paramToken = request.getParameter("token");
        if (paramToken == null || paramToken.isBlank()) {
            paramToken = request.getParameter("accessToken");
        }
        if (paramToken != null && !paramToken.isBlank()) {
            return paramToken.trim();
        }
        return null;
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        ApiResponse<Void> payload = ApiResponse.failure("AUTH_UNAUTHORIZED", "Authentication is required");
        response.getWriter().write(
                """
                {"success":false,"code":"%s","message":"%s","data":null}
                """.formatted(payload.code(), payload.message()).trim()
        );
    }
}
