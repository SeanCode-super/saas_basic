package com.saasbasics.platform.modules.system.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.tenant.TenantAccessContext;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.modules.audit.service.AuditTrailService;
import com.saasbasics.platform.modules.system.dto.DictOptionResponse;
import com.saasbasics.platform.modules.system.dto.SequenceNextResponse;
import com.saasbasics.platform.modules.system.entity.DictItemEntity;
import com.saasbasics.platform.modules.system.entity.DictTypeEntity;
import com.saasbasics.platform.modules.system.entity.SequenceRuleEntity;
import com.saasbasics.platform.modules.system.entity.SystemConfigEntity;
import com.saasbasics.platform.modules.system.mapper.DictItemMapper;
import com.saasbasics.platform.modules.system.mapper.DictTypeMapper;
import com.saasbasics.platform.modules.system.mapper.SequenceRuleMapper;
import com.saasbasics.platform.modules.system.mapper.SystemConfigMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class DictServiceAndConfigIsolationTest {

    @AfterEach
    void tearDown() {
        TenantAccessContextHolder.clear();
        AuthContext.clear();
    }

    @Test
    void dictOptionsFallBackToPlatformViaSystemBypassAndPreferTenantOverride() {
        DictTypeMapper typeMapper = mock(DictTypeMapper.class);
        DictItemMapper itemMapper = mock(DictItemMapper.class);
        DictService service = new DictService(provider(typeMapper), provider(itemMapper), mock(AuditTrailService.class));

        DictTypeEntity platformType = new DictTypeEntity();
        platformType.setId(10L);
        platformType.setTenantId(1L);
        platformType.setDictCode("order_status");
        platformType.setDictName("订单状态");
        platformType.setDictScope("GLOBAL");
        platformType.setStatus("ENABLED");

        DictItemEntity platformItem = new DictItemEntity();
        platformItem.setId(101L);
        platformItem.setTenantId(1L);
        platformItem.setDictTypeId(10L);
        platformItem.setItemLabel("待审核");
        platformItem.setItemValue("PENDING");
        platformItem.setItemColor("blue");
        platformItem.setItemTag("pending");
        platformItem.setIsDefault(true);
        platformItem.setStatus("ENABLED");

        DictTypeEntity tenantType = new DictTypeEntity();
        tenantType.setId(20L);
        tenantType.setTenantId(2L);
        tenantType.setDictCode("order_status");
        tenantType.setDictName("租户订单状态");
        tenantType.setDictScope("TENANT");
        tenantType.setStatus("ENABLED");

        DictItemEntity tenantItem = new DictItemEntity();
        tenantItem.setId(201L);
        tenantItem.setTenantId(2L);
        tenantItem.setDictTypeId(20L);
        tenantItem.setItemLabel("租户自定义待批");
        tenantItem.setItemValue("CUSTOM_PENDING");
        tenantItem.setIsDefault(false);
        tenantItem.setStatus("ENABLED");

        AtomicBoolean tenantHasOwnDict = new AtomicBoolean(false);
        when(typeMapper.selectOne(any())).thenAnswer(invocation -> {
            TenantAccessContext ctx = TenantAccessContextHolder.current();
            if (ctx != null && ctx.bypassesTenantIsolation()) {
                return platformType;
            }
            return tenantHasOwnDict.get() ? tenantType : null;
        });
        when(itemMapper.selectList(any())).thenAnswer(invocation -> {
            TenantAccessContext ctx = TenantAccessContextHolder.current();
            if (ctx != null && ctx.bypassesTenantIsolation()) {
                return List.of(platformItem);
            }
            return List.of(tenantItem);
        });

        setTenantPrincipal(2L, "acme");
        try (TenantAccessContextHolder.Scope ignored = TenantAccessContextHolder.openTenant(2L, "acme", 20L)) {
            List<DictOptionResponse> fallbackOptions = service.listOptions("order_status");
            assertThat(fallbackOptions).hasSize(1);
            assertThat(fallbackOptions.get(0).label()).isEqualTo("待审核");
            assertThat(fallbackOptions.get(0).value()).isEqualTo("PENDING");
            assertThat(fallbackOptions.get(0).defaultItem()).isTrue();

            tenantHasOwnDict.set(true);
            List<DictOptionResponse> overrideOptions = service.listOptions("order_status");
            assertThat(overrideOptions).hasSize(1);
            assertThat(overrideOptions.get(0).label()).isEqualTo("租户自定义待批");
            assertThat(overrideOptions.get(0).value()).isEqualTo("CUSTOM_PENDING");
        }
    }

    @Test
    void systemConfigFallsBackReadOnlyToPlatformWhenTenantMissing() {
        SystemConfigMapper configMapper = mock(SystemConfigMapper.class);
        SystemConfigService service = new SystemConfigService(provider(configMapper), mock(AuditTrailService.class));

        SystemConfigEntity platformConfig = new SystemConfigEntity();
        platformConfig.setId(1L);
        platformConfig.setTenantId(1L);
        platformConfig.setConfigKey("sys.upload.max_mb");
        platformConfig.setConfigValue("50");
        platformConfig.setStatus("ENABLED");

        SystemConfigEntity tenantConfig = new SystemConfigEntity();
        tenantConfig.setId(2L);
        tenantConfig.setTenantId(2L);
        tenantConfig.setConfigKey("sys.upload.max_mb");
        tenantConfig.setConfigValue("200");
        tenantConfig.setStatus("ENABLED");

        AtomicBoolean tenantHasOverride = new AtomicBoolean(false);
        AtomicBoolean platformHasConfig = new AtomicBoolean(true);
        when(configMapper.selectOne(any())).thenAnswer(invocation -> {
            TenantAccessContext ctx = TenantAccessContextHolder.current();
            if (ctx != null && ctx.bypassesTenantIsolation()) {
                return platformHasConfig.get() ? platformConfig : null;
            }
            return tenantHasOverride.get() ? tenantConfig : null;
        });

        setTenantPrincipal(2L, "acme");
        try (TenantAccessContextHolder.Scope ignored = TenantAccessContextHolder.openTenant(2L, "acme", 20L)) {
            assertThat(service.getConfigValueWithFallback("sys.upload.max_mb", "10")).isEqualTo("50");

            tenantHasOverride.set(true);
            assertThat(service.getConfigValueWithFallback("sys.upload.max_mb", "10")).isEqualTo("200");

            tenantHasOverride.set(false);
            platformHasConfig.set(false);
            assertThat(service.getConfigValueWithFallback("sys.upload.max_mb", "10")).isEqualTo("10");
        }
    }

    @Test
    void sequenceNextValueOnTenantClonesPlatformRuleWithZeroCounter() {
        SequenceRuleMapper mapper = mock(SequenceRuleMapper.class);
        SequenceRuleService service = new SequenceRuleService(provider(mapper), provider(mock(AuditTrailService.class)));

        SequenceRuleEntity platformRule = new SequenceRuleEntity();
        platformRule.setId(1L);
        platformRule.setTenantId(1L);
        platformRule.setRuleCode("SEQ_ORDER");
        platformRule.setRuleName("订单流水号");
        platformRule.setPrefix("ORD");
        platformRule.setSeqLength(6);
        platformRule.setStep(1);
        platformRule.setCurrentValue(99L);
        platformRule.setResetCycle("NONE");
        platformRule.setStatus("ENABLED");

        when(mapper.selectOne(any())).thenAnswer(invocation -> {
            TenantAccessContext ctx = TenantAccessContextHolder.current();
            if (ctx != null && ctx.bypassesTenantIsolation()) {
                return platformRule;
            }
            return null;
        });
        when(mapper.insert(any(SequenceRuleEntity.class))).thenAnswer(invocation -> {
            TenantAccessContext ctx = TenantAccessContextHolder.current();
            assertThat(ctx == null || !ctx.bypassesTenantIsolation()).isTrue();
            SequenceRuleEntity inserted = invocation.getArgument(0);
            assertThat(inserted.getCurrentValue()).isZero();
            inserted.setId(200L);
            return 1;
        });
        when(mapper.updateById(any(SequenceRuleEntity.class))).thenReturn(1);

        setTenantPrincipal(2L, "acme");
        try (TenantAccessContextHolder.Scope ignored = TenantAccessContextHolder.openTenant(2L, "acme", 20L)) {
            SequenceNextResponse response = service.nextValue("SEQ_ORDER");
            assertThat(response.code()).isEqualTo("SEQ_ORDER");
            assertThat(response.currentSeq()).isEqualTo(1L);
            assertThat(response.generatedValue()).startsWith("ORD").endsWith("000001");

            ArgumentCaptor<SequenceRuleEntity> insertCaptor = ArgumentCaptor.forClass(SequenceRuleEntity.class);
            verify(mapper).insert(insertCaptor.capture());
            assertThat(insertCaptor.getValue().getTenantId()).isEqualTo(2L);
            assertThat(insertCaptor.getValue().getCurrentValue()).isEqualTo(1L);
        }
    }

    private static void setTenantPrincipal(Long tenantId, String tenantCode) {
        AuthContext.set(new AuthPrincipal(
                2L, tenantId, tenantCode, 20L, "admin", "Tenant Admin", "TENANT_ADMIN",
                "ACTIVE", LocalDateTime.now().plusHours(1), List.of("*:*:*"), List.of(), List.of(2L), List.of()
        ));
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
