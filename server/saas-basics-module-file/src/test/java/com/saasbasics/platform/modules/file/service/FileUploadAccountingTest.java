package com.saasbasics.platform.modules.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.common.tenant.TenantQuotaWriteGuard;
import com.saasbasics.platform.config.MybatisPlusConfig;
import com.saasbasics.platform.modules.file.dto.FileUploadSessionCompleteRequest;
import com.saasbasics.platform.modules.file.dto.FileUploadSessionCreateRequest;
import com.saasbasics.platform.modules.file.entity.FileStorageEntity;
import com.saasbasics.platform.modules.file.mapper.FileObjectMapper;
import com.saasbasics.platform.modules.file.mapper.FileUploadSessionMapper;
import com.saasbasics.platform.modules.file.storage.FileClientFactory;
import com.saasbasics.platform.modules.file.storage.LocalFileClient;
import com.saasbasics.platform.security.EnterpriseDataPermissionHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

class FileUploadAccountingTest {
    @TempDir Path files;
    private JdbcTemplate jdbc;
    private FileUploadAccounting accounting;
    private org.springframework.transaction.support.TransactionTemplate transactions;
    private LocalFileClient client;
    private PlatformFileStorageGatewayImpl gateway;
    private FileUploadSessionService sessions;

    @BeforeEach
    void setup() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE plat_tenant(id BIGINT,tenant_code VARCHAR)");
        jdbc.execute("INSERT INTO plat_tenant VALUES(2,'target')");
        jdbc.execute("CREATE TABLE quota(tenant_id BIGINT PRIMARY KEY,mb BIGINT)");
        jdbc.execute("INSERT INTO quota VALUES(2,1)");
        String common = """
                id BIGINT AUTO_INCREMENT PRIMARY KEY,tenant_id BIGINT,bucket_id BIGINT,storage_id BIGINT,
                file_size BIGINT,object_key VARCHAR,file_name VARCHAR,status VARCHAR,upload_mode VARCHAR,
                version INT DEFAULT 0,deleted INT DEFAULT 0,deleted_at TIMESTAMP,created_by BIGINT,updated_by BIGINT,
                created_at TIMESTAMP,updated_at TIMESTAMP,remark VARCHAR
                """;
        jdbc.execute("CREATE TABLE file_upload_session(" + common + """
                ,expire_at TIMESTAMP,session_code VARCHAR,completed_at TIMESTAMP,part_count INT,owner_user_id BIGINT,
                request_key VARCHAR, request_hash VARCHAR,file_object_id BIGINT,quota_managed INT,cleanup_after TIMESTAMP,
                UNIQUE(tenant_id,request_key))
                """);
        jdbc.execute("CREATE TABLE file_object(" + common + """
                ,file_ext VARCHAR,content_type VARCHAR,visibility VARCHAR,biz_type VARCHAR,version_no INT,
                storage_path VARCHAR,target_dir VARCHAR)
                """);
        jdbc.execute("CREATE TABLE sys_export_task(tenant_id BIGINT,object_key VARCHAR,file_size BIGINT,deleted INT,status VARCHAR)");
        jdbc.execute("CREATE VIEW plat_tenant_storage_sources AS SELECT tenant_id,file_size FROM file_object WHERE deleted=0");
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.addMapper(FileObjectMapper.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setConfiguration(configuration);
        factory.setDataSource(ds);
        factory.setPlugins(new MybatisPlusConfig().mybatisPlusInterceptor(new EnterpriseDataPermissionHandler(
                new StaticListableBeanFactory().getBeanProvider(com.saasbasics.platform.common.auth.DataPermissionProvider.class))));
        factory.setMapperLocations(new ClassPathResource("mapper/FileUploadSessionMapper.xml"));
        var sql = new SqlSessionTemplate(factory.getObject());
        var sessionMapper = sql.getMapper(FileUploadSessionMapper.class);
        var objectMapper = sql.getMapper(FileObjectMapper.class);
        TenantQuotaWriteGuard guard = (tenant, code) ->
                jdbc.queryForObject("SELECT mb FROM quota WHERE tenant_id=? FOR UPDATE", Long.class, tenant);
        var proxy = new ProxyFactory(new FileUploadAccounting(guard, sessionMapper, objectMapper));
        proxy.setProxyTargetClass(true);
        var tx = new TransactionInterceptor();
        var manager = new DataSourceTransactionManager(ds);
        transactions = new org.springframework.transaction.support.TransactionTemplate(manager);
        tx.setTransactionManager(manager);
        tx.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        proxy.addAdvice(tx);
        accounting = (FileUploadAccounting) proxy.getProxy();

        FileStorageEntity storage = new FileStorageEntity();
        storage.setId(9L);
        storage.setTenantId(2L);
        storage.setStorageType("LOCAL");
        storage.setDefaultFolder("objects");
        storage.setPathPattern("{folder}/{filename}");
        storage.setUploadMode("DIRECT_SERVER");
        storage.setBasePath(files.toString());
        FileStorageService storages = mock(FileStorageService.class);
        when(storages.findActiveDefaultStorage(2L)).thenReturn(storage);
        when(storages.findActiveStorageById(2L,9L)).thenReturn(storage);
        FileUploadRouteService routes = mock(FileUploadRouteService.class);
        FileClientFactory clients = mock(FileClientFactory.class);
        client = spy(new LocalFileClient(files.toString(), "/raw"));
        when(clients.getClient(storage)).thenReturn(client);
        var beans = new StaticListableBeanFactory();
        beans.addBean("objects", objectMapper);
        beans.addBean("sessions", sessionMapper);
        beans.addBean("clients", clients);
        var log = mock(FileAccessLogService.class);
        var objectService = new FileObjectService(beans.getBeanProvider(FileObjectMapper.class));
        gateway = new PlatformFileStorageGatewayImpl(storages, routes, clients, objectService, log, accounting);
        sessions = new FileUploadSessionService(beans.getBeanProvider(FileUploadSessionMapper.class),
                beans.getBeanProvider(FileObjectMapper.class), log, storages, routes,
                beans.getBeanProvider(FileClientFactory.class), accounting);
    }

    private <T> T inTenant(Supplier<T> action) {
        try (var scope = TenantAccessContextHolder.openTenant(2L, "target", 10L)) {
            return action.get();
        }
    }

    private FileUploadSessionCreateRequest request(String name, long bytes) {
        return new FileUploadSessionCreateRequest(2L,1L,9L,null,name,bytes,"MULTIPART",2,10L,"COMMON");
    }

    @Test
    void directUploadReservesDuringPhysicalTransferAndLeavesNoDuplicateOnRetry() throws Exception {
        var inTransfer = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        byte[] bytes = new byte[800000];
        doAnswer(call -> {
            inTransfer.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out");
            return call.callRealMethod();
        }).when(client).upload(any(),any(),any());
        try (var workers = Executors.newSingleThreadExecutor()) {
            var first = workers.submit(() -> inTenant(() -> gateway.uploadBytes(2L,"COMMON","one.bin",null,bytes,"op1")));
            try {
                assertThat(inTransfer.await(5,TimeUnit.SECONDS)).isTrue();
                assertThatExceptionOfType(BizException.class).isThrownBy(() -> inTenant(() ->
                        sessions.createSession(request("two.bin",400000),"op2")))
                        .extracting(BizException::getCode).isEqualTo("TENANT_STORAGE_QUOTA_EXCEEDED");
            } finally {
                release.countDown();
            }
            var result = first.get(5,TimeUnit.SECONDS);
            var replay = inTenant(() -> gateway.uploadBytes(2L,"COMMON","one.bin",null,bytes,"op1"));
            assertThat(replay.fileId()).isEqualTo(result.fileId());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM file_object",Integer.class)).isEqualTo(1);
            assertThatExceptionOfType(BizException.class).isThrownBy(() -> inTenant(() ->
                    gateway.uploadBytes(2L,"COMMON","one.bin",null,new byte[1],"op1")))
                    .extracting(BizException::getCode).isEqualTo("FILE_REQUEST_CONFLICT");
        }
    }

    @Test
    void sessionCompletesAtExactSizeOnceAndExpiredReservationsFreeSpace() throws Exception {
        var ticket = inTenant(() -> sessions.createSession(request("parts.bin",900000),"p"));
        var replay = inTenant(() -> sessions.createSession(request("parts.bin",900000),"p"));
        assertThat(ticket.id()).isEqualTo(replay.id());
        var finish = new FileUploadSessionCompleteRequest(2L,"application/octet-stream","PRIVATE","COMMON",null);
        client.upload(new byte[1],ticket.objectKey(), "application/octet-stream");
        assertThatExceptionOfType(BizException.class).isThrownBy(() -> inTenant(() -> sessions.completeSession(ticket.id(),finish)))
                .extracting(BizException::getCode).isEqualTo("FILE_UPLOAD_SIZE_MISMATCH");
        client.upload(new byte[900000],ticket.objectKey(),"application/octet-stream");
        var registered = inTenant(() -> sessions.completeSession(ticket.id(),finish));
        assertThat(inTenant(() -> sessions.completeSession(ticket.id(),finish)).id()).isEqualTo(registered.id());
        var expired = inTenant(() -> sessions.createSession(request("expired.bin",100000),"expired"));
        client.upload(new byte[5],expired.objectKey(),"application/octet-stream");
        jdbc.update("UPDATE file_upload_session SET expire_at=TIMESTAMP '2020-01-01 00:00:00' WHERE id=?",expired.id());
        inTenant(() -> sessions.createSession(request("replacement.bin",120000),"repl"));
        sessions.cleanExpiredReservations();
        assertThat(Files.exists(files.resolve(expired.objectKey()))).isFalse();
        assertThat(jdbc.queryForObject("SELECT status FROM file_upload_session WHERE id=?",String.class,expired.id()))
                .isEqualTo("FAILED");
    }

    @Test
    void restoringAFileUsesTheSameQuotaAsPendingUploads() {
        jdbc.update("INSERT INTO file_object (id,tenant_id,storage_id,object_key,file_size,deleted) VALUES (70,2,9,'old',800000,1)");
        var reservation = inTenant(() -> sessions.createSession(request("pending.bin",400000),"pending"));
        assertThatExceptionOfType(BizException.class).isThrownBy(() -> inTenant(() -> transactions.execute(status -> {
            accounting.requireCapacity(2L,70L);
            return null;
        }))).extracting(BizException::getCode).isEqualTo("TENANT_STORAGE_QUOTA_EXCEEDED");
        jdbc.update("UPDATE file_upload_session SET expire_at=TIMESTAMP '2020-01-01 00:00:00' WHERE id=?",reservation.id());
        inTenant(() -> transactions.execute(status -> {
            accounting.requireCapacity(2L,70L);
            return jdbc.update("UPDATE file_object SET deleted=0 WHERE id=70");
        }));
        assertThatExceptionOfType(BizException.class).isThrownBy(() -> inTenant(() ->
                sessions.createSession(request("too-big.bin",400000),"another")))
                .extracting(BizException::getCode).isEqualTo("TENANT_STORAGE_QUOTA_EXCEEDED");
    }

    @Test
    void unfinishedCleanupRemainsBilledUntilTheObjectIsActuallyRemoved() {
        doAnswer(call -> {
            call.callRealMethod();
            throw new BizException("FILE_STORAGE_UPLOAD_FAILED","simulated timeout");
        }).when(client).upload(any(),any(),any());
        doThrow(new BizException("FILE_DELETE_FAILED","temporarily unavailable")).doCallRealMethod().when(client).delete(any());
        assertThatExceptionOfType(BizException.class).isThrownBy(() -> inTenant(() ->
                gateway.uploadBytes(2L,"COMMON","cleanup.bin",null,new byte[900000],"failed")));
        assertThatExceptionOfType(BizException.class).isThrownBy(() -> inTenant(() ->
                sessions.createSession(request("next.bin",400000),"next")))
                .extracting(BizException::getCode).isEqualTo("TENANT_STORAGE_QUOTA_EXCEEDED");
        jdbc.update("UPDATE file_upload_session SET cleanup_after=TIMESTAMP '2020-01-01 00:00:00' WHERE status='CLEANUP'");
        sessions.cleanExpiredReservations();
        assertThat(Files.exists(files.resolve("objects/cleanup.bin"))).isFalse();
        assertThat(inTenant(() -> sessions.createSession(request("next.bin",400000),"next")).id()).isNotNull();
    }

    @Test
    void failedPhysicalTransferIsCleanedAndItsReservationCanBeRetried() {
        byte[] bytes = new byte[900000];
        doAnswer(call -> {
            call.callRealMethod();
            throw new BizException("FILE_STORAGE_UPLOAD_FAILED","simulated write failure");
        }).doCallRealMethod().when(client).upload(any(),any(),any());
        assertThatExceptionOfType(BizException.class).isThrownBy(() -> inTenant(() ->
                gateway.uploadBytes(2L,"COMMON","retry.bin",null,bytes,"retry")));
        assertThat(Files.exists(files.resolve("objects/retry.bin"))).isFalse();
        var result = inTenant(() -> gateway.uploadBytes(2L,"COMMON","retry.bin",null,bytes,"retry"));
        assertThat(result.fileSize()).isEqualTo(bytes.length);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM file_upload_session",Integer.class)).isEqualTo(1);
    }
}
