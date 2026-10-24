package com.saasbasics.platform.modules.tenant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saasbasics.platform.common.tenant.TenantFeatureProvider;
import com.saasbasics.platform.modules.audit.service.AuditTrailService;
import com.saasbasics.platform.modules.tenant.dto.TenantAppCatalogItemResponse;
import com.saasbasics.platform.modules.tenant.dto.TenantAppResponse;
import com.saasbasics.platform.modules.tenant.dto.TenantAppSubscribeRequest;
import com.saasbasics.platform.modules.tenant.entity.TenantAppEntity;
import com.saasbasics.platform.modules.tenant.mapper.TenantAppMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class TenantAppServiceTest {

    private TenantAppMapper appMapper;
    private TenantFeatureProvider featureProvider;
    private AuditTrailService auditTrailService;
    private TenantAppService appService;

    @BeforeEach
    void setUp() {
        appMapper = mock(TenantAppMapper.class);
        featureProvider = mock(TenantFeatureProvider.class);
        auditTrailService = mock(AuditTrailService.class);

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("appMapper", appMapper);

        appService = new TenantAppService(
                beanFactory.getBeanProvider(TenantAppMapper.class),
                featureProvider,
                auditTrailService
        );
    }

    @Test
    void listCatalogReturnsAllAppsWithCorrectStatus() {
        when(featureProvider.getEffectiveFeatures(2L)).thenReturn(Set.of("saas.iam.basic", "saas.tenant.basic"));
        when(featureProvider.hasFeature(2L, "iam")).thenReturn(true);
        when(featureProvider.hasFeature(2L, "tenant")).thenReturn(true);
        when(featureProvider.hasFeature(2L, "workflow")).thenReturn(false);
        when(featureProvider.hasFeature(2L, "crm")).thenReturn(false);
        when(appMapper.selectList(any())).thenReturn(List.of());

        List<TenantAppCatalogItemResponse> catalog = appService.listAppCatalog(2L);
        assertThat(catalog).isNotEmpty();

        TenantAppCatalogItemResponse iamApp = catalog.stream().filter(a -> a.appCode().equals("iam")).findFirst().orElseThrow();
        assertThat(iamApp.status()).isEqualTo("ENABLED");
        assertThat(iamApp.isIncludedInPlan()).isTrue();

        TenantAppCatalogItemResponse wfApp = catalog.stream().filter(a -> a.appCode().equals("workflow")).findFirst().orElseThrow();
        assertThat(wfApp.status()).isEqualTo("DISABLED");
        assertThat(wfApp.isIncludedInPlan()).isFalse();
    }

    @Test
    void subscribeAppCreatesSubscriptionAndInvalidatesCache() {
        when(appMapper.selectOne(any())).thenReturn(null);

        TenantAppSubscribeRequest req = new TenantAppSubscribeRequest("workflow", 14, "Trial test");
        TenantAppResponse created = appService.subscribeApp(5L, req);

        assertThat(created).isNotNull();
        assertThat(created.appCode()).isEqualTo("workflow");
        assertThat(created.expiredAt()).isNotNull();
        verify(appMapper).insert(any(TenantAppEntity.class));
        verify(featureProvider).invalidateCache(5L);
    }
}
