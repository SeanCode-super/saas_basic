package com.saasbasics.platform.modules.tenant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.tenant.dto.TenantSnapshotResponse;
import com.saasbasics.platform.modules.tenant.dto.TenantSnapshotRestoreResponse;
import com.saasbasics.platform.modules.tenant.entity.PlatTenantBillEntity;
import com.saasbasics.platform.modules.tenant.entity.TenantEntity;
import com.saasbasics.platform.modules.tenant.entity.TenantQuotaEntity;
import com.saasbasics.platform.modules.tenant.entity.TenantSnapshotRecordEntity;
import com.saasbasics.platform.modules.tenant.mapper.PlatTenantBillMapper;
import com.saasbasics.platform.modules.tenant.mapper.PlatTenantMeteringMapper;
import com.saasbasics.platform.modules.tenant.mapper.TenantMapper;
import com.saasbasics.platform.modules.tenant.mapper.TenantQuotaMapper;
import com.saasbasics.platform.modules.tenant.mapper.TenantSnapshotRecordMapper;
import com.saasbasics.platform.outbox.OutboxEventPayload;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import com.saasbasics.platform.modules.tenant.mapper.TenantSnapshotSchemaMapper;
import org.springframework.mock.web.MockMultipartFile;

class TenantMeteringAndDrTest {

    @AfterEach
    void clearAuth() {
        AuthContext.clear();
    }

    @Test
    void meteringReportsConfiguredLimitsEvenWhenUsageIsHigherAndPropagatesUnavailableMeasurements() {
        AuthContext.set(new AuthPrincipal(1L, 7L, "seven", 1L, "tester", "Tester", "TENANT", "ACTIVE",
                LocalDateTime.now().plusHours(1), List.of(), List.of(), List.of(), List.of()));
        var counts = mock(PlatTenantMeteringMapper.class);
        var quotas = mock(TenantQuotaMapper.class);
        var seats = new TenantQuotaEntity();
        seats.setQuotaCode("USER_COUNT");
        seats.setQuotaValue(1L);
        var storage = new TenantQuotaEntity();
        storage.setQuotaCode("STORAGE_MB");
        storage.setQuotaValue(0L);
        when(quotas.selectList(any())).thenReturn(List.of(seats, storage));
        when(counts.countActiveSeats(7L)).thenReturn(2);
        when(counts.sumStorageUsed(7L)).thenReturn(1024L);
        var service = new TenantMeteringService(counts, mock(PlatTenantBillMapper.class), mock(TenantMapper.class),
                quotas, mock(TenantSnapshotRecordMapper.class), mock(TenantBusinessSnapshot.class), new ObjectMapper());
        var summary = service.getSummary();
        assertThat(summary.activeSeats()).isEqualTo(2);
        assertThat(summary.seatQuota()).isOne();
        assertThat(summary.storageUsedBytes()).isEqualTo(1024L);
        assertThat(summary.storageQuotaBytes()).isZero();
        when(quotas.selectList(any())).thenReturn(List.of(seats));
        assertThatThrownBy(service::getSummary).isInstanceOf(BizException.class);
        when(counts.countActiveSeats(7L)).thenThrow(new IllegalStateException("measurement unavailable"));
        assertThatThrownBy(service::getSummary).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validatesWithoutWritingAndRestoresOnlyArchivedBusinessRowsForTheSelectedTenant() {
        PlatTenantMeteringMapper meteringMapper = mock(PlatTenantMeteringMapper.class);
        PlatTenantBillMapper billMapper = mock(PlatTenantBillMapper.class);
        TenantMapper tenantMapper = mock(TenantMapper.class);

        TenantEntity tenant = new TenantEntity();
        tenant.setId(1L);
        tenant.setTenantCode("platform");
        tenant.setTenantName("Platform");
        when(tenantMapper.selectById(1L)).thenReturn(tenant);

        var datasource = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                "jdbc:h2:mem:dr_" + java.util.UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(datasource);
        for (String table : List.of("plat_tenant", "iam_user", "iam_role", "iam_department", "iam_employee", "sys_config",
                "sys_dict_type", "plat_tenant_quota", "plat_tenant_bill", "plat_tenant_metering_log")) {
            jdbc.execute("CREATE TABLE " + table + " (id BIGINT PRIMARY KEY, tenant_id BIGINT)");
        }
        jdbc.update("INSERT INTO plat_tenant VALUES (1,1)");
        jdbc.execute("CREATE TABLE erp_inventory (id BIGINT PRIMARY KEY, tenant_id BIGINT, quantity INT)");
        jdbc.update("INSERT INTO erp_inventory VALUES (1,1,10),(2,2,15)");
        jdbc.execute("CREATE TABLE biz_inspection (id BIGINT PRIMARY KEY, tenant_id BIGINT, note VARCHAR(80))");
        jdbc.update("INSERT INTO biz_inspection VALUES (4,1,'before'),(5,1,NULL)");
        jdbc.execute("CREATE TABLE biz_checklist (id BIGINT PRIMARY KEY, tenant_id BIGINT, payload VARBINARY(16))");
        jdbc.batchUpdate("INSERT INTO biz_checklist VALUES (?,1,?)",
                java.util.stream.IntStream.rangeClosed(1, 5001).boxed().toList(), 200, (ps, id) -> {
                    ps.setInt(1, id);
                    ps.setBytes(2, new byte[]{0, -1});
                });
        var registry = mock(TenantSnapshotRecordMapper.class);
        var saved = new java.util.concurrent.atomic.AtomicReference<TenantSnapshotRecordEntity>();
        org.mockito.Mockito.doAnswer(i -> { saved.set(i.getArgument(0)); return 1; }).when(registry).insert(any(TenantSnapshotRecordEntity.class));
        when(registry.selectOne(any())).thenAnswer(i -> saved.get());
        TenantMeteringService service = new TenantMeteringService(
                meteringMapper,
                billMapper,
                tenantMapper,
                mock(TenantQuotaMapper.class),
                registry,
                new TenantBusinessSnapshot(jdbc, mock(TenantSnapshotSchemaMapper.class)),
                new ObjectMapper().setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        );

        AuthContext.set(new AuthPrincipal(1L, 1L, "platform", 1L, "tester", "Tester", "TENANT", "ACTIVE",
                LocalDateTime.now().plusHours(1), List.of(), List.of(), List.of(), List.of()));
        TenantSnapshotResponse exported = service.generateTenantSnapshot(1L);
        assertThat(exported.snapshotId()).startsWith("SNAP_");
        assertThat(exported.entityCounts().get("biz_checklist")).isEqualTo(5001L);
        assertThat(exported.checksumSha256()).hasSize(64);
        assertThat(exported.archiveSizeBytes()).isGreaterThan(100L);
        assertThat(exported.downloadUrl()).endsWith("/" + exported.snapshotId());

        byte[] zipBytes = service.loadSnapshotArchive(exported.snapshotId());
        assertThat(zipBytes.length).isEqualTo(exported.archiveSizeBytes().intValue());

        MockMultipartFile upload = new MockMultipartFile(
                "file", exported.snapshotId() + ".zip", "application/zip", zipBytes);
        TenantSnapshotRestoreResponse restored = service.restoreTenantSnapshot(upload, 1L);
        assertThat(restored.status()).isEqualTo("VALIDATED");
        assertThat(restored.restoredCounts()).containsKey("plat_tenant");
        assertThat(restored.snapshotId()).isEqualTo(exported.snapshotId());
        var legacy = new MockMultipartFile("file", "legacy.json", "application/json",
                "{\"snapshotId\":\"legacy\",\"entityCounts\":{\"inventory\":7}}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(service.restoreTenantSnapshot(legacy, 1L).status()).isEqualTo("VALIDATED");
        assertThatThrownBy(() -> service.restoreTenantSnapshot(legacy, 1L, true)).isInstanceOf(BizException.class);
        jdbc.update("DELETE FROM biz_checklist");
        jdbc.update("UPDATE erp_inventory SET quantity=42");
        jdbc.update("INSERT INTO erp_inventory VALUES (3,1,4)");
        jdbc.update("UPDATE biz_inspection SET note='after'");
        service.restoreTenantSnapshot(upload, 1L);
        assertThat(jdbc.queryForObject("SELECT quantity FROM erp_inventory WHERE id=1", Integer.class)).isEqualTo(42);
        var tx = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(datasource));
        // Force the second table to fail after the first has been rewritten.
        jdbc.execute("ALTER TABLE erp_inventory ADD CONSTRAINT stock_min CHECK (quantity >= 4 AND quantity <> 10)");
        assertThatThrownBy(() -> tx.execute(s -> service.restoreTenantSnapshot(upload, 1L, true))).isInstanceOf(BizException.class);
        assertThat(jdbc.queryForObject("SELECT note FROM biz_inspection WHERE id=4", String.class)).isEqualTo("after");
        jdbc.execute("ALTER TABLE erp_inventory DROP CONSTRAINT stock_min");
        assertThat(tx.execute(s -> service.restoreTenantSnapshot(upload, 1L, true)).status()).isEqualTo("RESTORED");
        assertThat(jdbc.queryForList("SELECT quantity FROM erp_inventory ORDER BY id", Integer.class)).containsExactly(10,42);
        assertThat(jdbc.queryForObject("SELECT note FROM biz_inspection WHERE id=4", String.class)).isEqualTo("before");
        assertThat(jdbc.queryForObject("SELECT note FROM biz_inspection WHERE id=5", String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM biz_checklist", Integer.class)).isEqualTo(5001);
        assertThat(jdbc.queryForObject("SELECT payload FROM biz_checklist WHERE id=4999", byte[].class)).containsExactly(0, -1);
        tx.execute(s -> service.restoreTenantSnapshot(upload, 1L, true));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM erp_inventory", Integer.class)).isEqualTo(2);
    }

    @Test
    void rejectsCrossTenantSnapshotExportRestoreAndDownloadForNonSuperAdmin() {
        PlatTenantMeteringMapper meteringMapper = mock(PlatTenantMeteringMapper.class);
        PlatTenantBillMapper billMapper = mock(PlatTenantBillMapper.class);
        TenantMapper tenantMapper = mock(TenantMapper.class);
        TenantSnapshotRecordMapper recordMapper = mock(TenantSnapshotRecordMapper.class);


        TenantMeteringService service = new TenantMeteringService(
                meteringMapper,
                billMapper,
                tenantMapper,
                mock(TenantQuotaMapper.class),
                recordMapper,
                mock(TenantBusinessSnapshot.class),
                new ObjectMapper().setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        );

        // Mock normal tenant 1 user (not superadmin)
        AuthPrincipal regularTenantUser = new AuthPrincipal(
                100L, 1L, "tenant_alpha", 200L, "alpha_user", "Alpha",
                "TENANT", "ACTIVE", LocalDateTime.now().plusHours(2),
                List.of("tenant:snapshot:export", "tenant:snapshot:restore"),
                List.of(), List.of(2L), List.of()
        );
        AuthContext.set(regularTenantUser);

        // 1. Cross-tenant export should fail
        assertThatThrownBy(() -> service.generateTenantSnapshot(2L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无权跨租户导出灾备快照");

        // 2. Cross-tenant restore should fail
        MockMultipartFile dummyZip = new MockMultipartFile("file", "test.zip", "application/zip", new byte[]{1, 2, 3});
        assertThatThrownBy(() -> service.restoreTenantSnapshot(dummyZip, 2L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无权跨租户恢复灾备快照");

        // 3. Cross-tenant download should fail
        TenantSnapshotRecordEntity tenantTwoRecord = new TenantSnapshotRecordEntity();
        tenantTwoRecord.setSnapshotId("SNAP_TENANT_2");
        tenantTwoRecord.setTenantId(2L);
        when(recordMapper.selectOne(any())).thenReturn(tenantTwoRecord);

        assertThatThrownBy(() -> service.loadSnapshotArchive("SNAP_TENANT_2"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无权下载其他租户的灾备快照");
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
                new ObjectMapper().setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
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
