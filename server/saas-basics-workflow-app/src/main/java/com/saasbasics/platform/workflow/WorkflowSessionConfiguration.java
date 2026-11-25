package com.saasbasics.platform.workflow;

import com.saasbasics.platform.modules.audit.service.AuditService;
import com.saasbasics.platform.modules.audit.service.AuditTrailService;
import com.saasbasics.platform.modules.iam.filter.AuthContextFilter;
import com.saasbasics.platform.modules.iam.service.AccessSubjectProfileService;
import com.saasbasics.platform.modules.iam.service.AuthService;
import com.saasbasics.platform.modules.iam.service.DataPermissionRuleService;
import com.saasbasics.platform.modules.iam.service.MenuPermissionService;
import com.saasbasics.platform.modules.iam.service.PasswordSecurityService;
import com.saasbasics.platform.modules.iam.service.PortalEntryService;
import com.saasbasics.platform.modules.tenant.service.DatabasePortalTenantDirectory;
import com.saasbasics.platform.modules.tenant.service.TenantFeatureService;
import com.saasbasics.platform.modules.tenant.service.TenantMembershipCapacityService;
import com.saasbasics.platform.modules.tenant.service.TenantQuotaWriteGuardImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@ComponentScan("com.saasbasics.platform.modules.organization.internal.application")
@MapperScan({
        "com.saasbasics.platform.modules.audit.mapper",
        "com.saasbasics.platform.modules.iam.mapper",
        "com.saasbasics.platform.modules.organization.internal.persistence.mapper",
        "com.saasbasics.platform.modules.tenant.mapper"
})
@Import({
        AuthService.class, PasswordSecurityService.class, PortalEntryService.class,
        MenuPermissionService.class, AccessSubjectProfileService.class, DataPermissionRuleService.class,
        DatabasePortalTenantDirectory.class, TenantMembershipCapacityService.class, TenantQuotaWriteGuardImpl.class,
        TenantFeatureService.class, AuditService.class, AuditTrailService.class
})
class WorkflowSessionConfiguration {

    @Bean
    AuthContextFilter workflowSessionFilter(AuthService authService, Environment environment) {
        return new AuthContextFilter(authService, environment) {
            @Override
            protected boolean shouldNotFilter(HttpServletRequest request) {
                return "GET".equals(request.getMethod())
                        && (request.getContextPath() + "/api/workflow/health").equals(request.getRequestURI());
            }
        };
    }
}
