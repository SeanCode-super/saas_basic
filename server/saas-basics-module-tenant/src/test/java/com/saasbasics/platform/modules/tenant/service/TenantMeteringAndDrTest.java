package com.saasbasics.platform.modules.tenant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.modules.tenant.dto.TenantSnapshotResponse;
import com.saasbasics.platform.modules.tenant.dto.TenantSnapshotRestoreResponse;
import com.saasbasics.platform.modules.tenant.entity.PlatTenantBillEntity;
import com.saasbasics.platform.modules.tenant.entity.TenantEntity;
import com.saasbasics.platform.modules.tenant.mapper.PlatTenantBillMapper;
import com.saasbasics.platform.modules.tenant.mapper.PlatTenantMeteringMapper;
import com.saasbasics.platform.modules.tenant.mapper.TenantMapper;
import com.saasbasics.platform.modules.tenant.mapper.TenantQuotaMapper;
import com.saasbasics.platform.modules.tenant.mapper.TenantSnapshotRecordMapper;
import com.saasbasics.platform.outbox.OutboxEventPayload;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

class TenantMeteringAndDrTest {

    @Test
    void exportsGenuineZipSnapshotLoadsArchiveAndVerifiesSha256OnRestore() {
        PlatTenantMeteringMapper meteringMapper = mock(PlatTenantMeteringMapper.class);
        PlatTenantBillMapper billMapper = mock(PlatTenantBillMapper.class);
        TenantMapper tenantMapper = mock(TenantMapper.class);

        TenantEntity tenant = new TenantEntity();
        tenant.setId(1L);
        tenant.setTenantCode("platform");
        tenant.setTenantName("Platform");
        when(tenantMapper.selectById(1L)).thenReturn(tenant);

        StaticListableBeanFactory bf = new StaticListableBeanFactory();
        TenantMeteringService service = new TenantMeteringService(
                meteringMapper,
                billMapper,
                tenantMapper,
                bf.getBeanProvider(TenantQuotaMapper.class),
                bf.getBeanProvider(TenantSnapshotRecordMapper.class),
                bf.getBeanProvider(JdbcTemplate.class),
                new ObjectMapper()
        );

        TenantSnapshotResponse exported = service.generateTenantSnapshot(1L);
        assertThat(exported.snapshotId()).startsWith("SNAP_");
        assertThat(exported.checksumSha256()).hasSize(64);
        assertThat(exported.archiveSizeBytes()).isGreaterThan(100L);
        assertThat(exported.downloadUrl()).endsWith("/" + exported.snapshotId());

        byte[] zipBytes = service.loadSnapshotArchive(exported.snapshotId());
        assertThat(zipBytes.length).isEqualTo(exported.archiveSizeBytes().intValue());

        MockMultipartFile upload = new MockMultipartFile(
                "file", exported.snapshotId() + ".zip", "application/zip", zipBytes);
        TenantSnapshotRestoreResponse restored = service.restoreTenantSnapshot(upload, 1L);
        assertThat(restored.status()).isEqualTo("SUCCESS");
        assertThat(restored.restoredCounts()).containsKey("plat_tenant");
        assertThat(restored.snapshotId()).isEqualTo(exported.snapshotId());
    }

    @Test
    void paySuccessOutboxHandlerExtendsTenantAndCreatesPaidBill() throws Exception {
        TenantMapper tenantMapper = mock(TenantMapper.class);
        PlatTenantBillMapper billMapper = mock(PlatTenantBillMapper.class);
        TenantEntity tenant = new TenantEntity();
        tenant.setId(1L);
        tenant.setTenantCode("platform");
        tenant.setStatus("ACTIVE");
        when(tenantMapper.selectById(1L)).thenReturn(tenant);

        StaticListableBeanFactory bf = new StaticListableBeanFactory();
        bf.addBean("tenantMapper", tenantMapper);
        bf.addBean("billMapper", billMapper);

        PaySuccessOutboxHandler handler = new PaySuccessOutboxHandler(
                bf.getBeanProvider(TenantMapper.class),
                bf.getBeanProvider(PlatTenantBillMapper.class),
                new ObjectMapper()
        );

        OutboxEventPayload evt = new OutboxEventPayload(
                "EVT_1",
                "PAY_ORDER_SUCCEEDED",
                "PAY_ORDER",
                "PO_888",
                1L,
                "{\"tenantId\":1,\"orderNo\":\"PO_888\",\"payTradeNo\":\"PT_888\",\"amount\":\"299.00\",\"channel\":\"WECHAT\",\"durationDays\":30}"
        );
        handler.handle(evt);

        assertThat(tenant.getExpireAt()).isNotNull();
        verify(tenantMapper).updateById(tenant);
        verify(billMapper).insert(any(PlatTenantBillEntity.class));
    }
}
