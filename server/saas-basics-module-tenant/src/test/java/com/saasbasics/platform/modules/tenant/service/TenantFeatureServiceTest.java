package com.saasbasics.platform.modules.tenant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.modules.tenant.entity.TenantAppEntity;
import com.saasbasics.platform.modules.tenant.entity.TenantEntity;
import com.saasbasics.platform.modules.tenant.entity.TenantPackageEntity;
import com.saasbasics.platform.modules.tenant.mapper.TenantAppMapper;
import com.saasbasics.platform.modules.tenant.mapper.TenantMapper;
import com.saasbasics.platform.modules.tenant.mapper.TenantPackageMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class TenantFeatureServiceTest {

    private TenantMapper tenantMapper;
    private TenantPackageMapper packageMapper;
    private TenantAppMapper appMapper;
    private TenantFeatureService featureService;

    @BeforeEach
    void setUp() {
        tenantMapper = mock(TenantMapper.class);
        packageMapper = mock(TenantPackageMapper.class);
        appMapper = mock(TenantAppMapper.class);

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("tenantMapper", tenantMapper);
        beanFactory.addBean("packageMapper", packageMapper);
        beanFactory.addBean("appMapper", appMapper);

        featureService = new TenantFeatureService(
                beanFactory.getBeanProvider(TenantMapper.class),
                beanFactory.getBeanProvider(TenantPackageMapper.class),
                beanFactory.getBeanProvider(TenantAppMapper.class),
                beanFactory.getBeanProvider(org.springframework.data.redis.core.StringRedisTemplate.class),
                new ObjectMapper()
        );
    }

    @Test
    void platformTenantAlwaysHasAllFeatures() {
        assertThat(featureService.hasFeature(1L, "workflow")).isTrue();
        assertThat(featureService.hasFeature(1L, "crm")).isTrue();
        assertThat(featureService.hasFeature(0L, "any_random_feature")).isTrue();
        assertThat(featureService.getEffectiveFeatures(1L)).contains("*");
    }

    @Test
    void basicPlanTenantHasOnlyBasicFeatures() {
        TenantEntity tenant = new TenantEntity();
        tenant.setId(2L);
        tenant.setStatus("ENABLED");
        tenant.setPackageId(10L);

        TenantPackageEntity pkg = new TenantPackageEntity();
        pkg.setId(10L);
        pkg.setFeatureFlagsJson("[\"saas.dashboard.basic\", \"saas.iam.basic\", \"saas.tenant.basic\"]");

        when(tenantMapper.selectById(2L)).thenReturn(tenant);
        when(packageMapper.selectById(10L)).thenReturn(pkg);
        when(appMapper.selectList(any())).thenReturn(List.of());

        assertThat(featureService.hasFeature(2L, "dashboard")).isTrue();
        assertThat(featureService.hasFeature(2L, "iam")).isTrue();
        assertThat(featureService.hasFeature(2L, "tenant")).isTrue();
        assertThat(featureService.hasFeature(2L, "workflow")).isFalse();
        assertThat(featureService.hasFeature(2L, "crm")).isFalse();
        assertThat(featureService.hasFeature(2L, "erp")).isFalse();
    }

    @Test
    void extraFeatureOverrideGrantsWorkflowToBasicTenant() {
        TenantEntity tenant = new TenantEntity();
        tenant.setId(3L);
        tenant.setStatus("ENABLED");
        tenant.setPackageId(10L);
        tenant.setExtraFeaturesJson("[\"workflow\"]");

        TenantPackageEntity pkg = new TenantPackageEntity();
        pkg.setId(10L);
        pkg.setFeatureFlagsJson("[\"saas.iam.basic\"]");

        when(tenantMapper.selectById(3L)).thenReturn(tenant);
        when(packageMapper.selectById(10L)).thenReturn(pkg);
        when(appMapper.selectList(any())).thenReturn(List.of());

        assertThat(featureService.hasFeature(3L, "iam")).isTrue();
        assertThat(featureService.hasFeature(3L, "workflow")).isTrue();
        assertThat(featureService.hasFeature(3L, "crm")).isFalse();
    }

    @Test
    void appSubscriptionTrialAndExpirationEnforcement() {
        TenantEntity tenant = new TenantEntity();
        tenant.setId(4L);
        tenant.setStatus("ENABLED");
        tenant.setPackageId(10L);

        TenantPackageEntity pkg = new TenantPackageEntity();
        pkg.setId(10L);
        pkg.setFeatureFlagsJson("[\"saas.iam.basic\"]");

        // Active trial app
        TenantAppEntity activeTrial = new TenantAppEntity();
        activeTrial.setTenantId(4L);
        activeTrial.setAppCode("crm");
        activeTrial.setStatus("ENABLED");
        activeTrial.setExpiredAt(LocalDateTime.now().plusDays(7));

        // Expired trial app
        TenantAppEntity expiredTrial = new TenantAppEntity();
        expiredTrial.setTenantId(4L);
        expiredTrial.setAppCode("erp");
        expiredTrial.setStatus("ENABLED");
        expiredTrial.setExpiredAt(LocalDateTime.now().minusDays(1));

        when(tenantMapper.selectById(4L)).thenReturn(tenant);
        when(packageMapper.selectById(10L)).thenReturn(pkg);
        when(appMapper.selectList(any())).thenReturn(List.of(activeTrial, expiredTrial));

        assertThat(featureService.hasFeature(4L, "crm")).isTrue();
        assertThat(featureService.hasFeature(4L, "erp")).isFalse();
    }
}
