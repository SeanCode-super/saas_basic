package com.saasbasics.platform.common.tenant;

import com.saasbasics.platform.common.auth.AuthContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TenantContextFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Tenant-Code";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String tenantCode = request.getHeader(HEADER_NAME);
            if (tenantCode == null || tenantCode.isBlank()) {
                tenantCode = AuthContext.getTenantCode();
            }
            TenantContext.setTenantCode(tenantCode == null || tenantCode.isBlank() ? "platform" : tenantCode);
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
