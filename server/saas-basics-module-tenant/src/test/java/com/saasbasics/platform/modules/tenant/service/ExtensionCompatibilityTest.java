package com.saasbasics.platform.modules.tenant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.tenant.TenantApplication;
import com.saasbasics.platform.modules.tenant.entity.TenantAppEntity;
import com.saasbasics.platform.modules.tenant.mapper.TenantAppMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExtensionCompatibilityTest {

    @Test
    void enforcesHostSemVerRangeTopologicalDependenciesAndReverseDependencyGuard() {
        ExtensionCompatibilityResolver resolver = new ExtensionCompatibilityResolver("0.2.0");

        TenantApplication coreApp = stubApp("core_ledger", 2, "0.1.0", "0.5.0", Map.of());
        TenantApplication billingApp = stubApp("smart_billing", 1, "0.1.0", "0.3.0", Map.of("core_ledger", 2));
        TenantApplication incompatibleApp = stubApp("future_ai", 1, "1.0.0", null, Map.of());

        Map<String, TenantApplication> catalog = Map.of(
                coreApp.code(), coreApp,
                billingApp.code(), billingApp,
                incompatibleApp.code(), incompatibleApp
        );

        // 1. Host version compatibility check
        resolver.verifyHostCompatibility(coreApp);
        assertThatThrownBy(() -> resolver.verifyHostCompatibility(incompatibleApp))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo("EXTENSION_HOST_INCOMPATIBLE");

        // 2. Topological install order puts core_ledger before smart_billing
        List<String> order = resolver.resolveInstallOrder(List.of(billingApp, coreApp));
        assertThat(order.indexOf("core_ledger")).isLessThan(order.indexOf("smart_billing"));

        // 3. Cycle detection
        TenantApplication cyclicA = stubApp("cycle_a", 1, null, null, Map.of("cycle_b", 1));
        TenantApplication cyclicB = stubApp("cycle_b", 1, null, null, Map.of("cycle_a", 1));
        assertThatThrownBy(() -> resolver.resolveInstallOrder(List.of(cyclicA, cyclicB)))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo("EXTENSION_DEPENDENCY_CYCLE");

        // 4. Dependency satisfaction & reverse dependency disable guard
        TenantAppMapper mapper = mock(TenantAppMapper.class);
        TenantAppEntity installedCore = new TenantAppEntity();
        installedCore.setAppCode("core_ledger");
        installedCore.setInstalledRevision(2);
        installedCore.setStatus("ENABLED");
        when(mapper.selectOne(any())).thenReturn(installedCore);

        resolver.verifyCompatibleAndDependencies(1L, billingApp, catalog, mapper);

        TenantAppEntity installedBilling = new TenantAppEntity();
        installedBilling.setAppCode("smart_billing");
        installedBilling.setInstalledRevision(1);
        installedBilling.setStatus("ENABLED");
        when(mapper.selectList(any())).thenReturn(List.of(installedCore, installedBilling));

        assertThatThrownBy(() -> resolver.assertNoActiveDependents(1L, "core_ledger", catalog.values(), mapper))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo("EXTENSION_HAS_ACTIVE_DEPENDENTS");
    }

    private static TenantApplication stubApp(
            String code, int latestRevision, String minHost, String maxHost, Map<String, Integer> deps) {
        return new TenantApplication() {
            @Override public List<Endpoint> endpoints() { return List.of(); }
            @Override public String code() { return code; }
            @Override public String name() { return code; }
            @Override public String description() { return code; }
            @Override public String componentPath() { return "@/views/industry/IndustryApplication.vue"; }
            @Override public int latestRevision() { return latestRevision; }
            @Override public void migrate(long tenantId, int revision) {}
            @Override public String minHostVersion() { return minHost; }
            @Override public String maxHostVersion() { return maxHost; }
            @Override public Map<String, Integer> dependencies() { return deps; }
        };
    }
}
