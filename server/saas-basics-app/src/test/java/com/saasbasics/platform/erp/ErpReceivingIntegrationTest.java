package com.saasbasics.platform.erp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saasbasics.platform.SaasBasicsApplication;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.modules.erp.dto.ErpMasterDataRequest;
import com.saasbasics.platform.modules.erp.dto.ErpProductSaveRequest;
import com.saasbasics.platform.modules.erp.dto.ErpPurchaseItemRequest;
import com.saasbasics.platform.modules.erp.dto.ErpPurchaseOrderSaveRequest;
import com.saasbasics.platform.modules.erp.dto.ErpStockAdjustmentRequest;
import com.saasbasics.platform.modules.erp.service.ErpService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = SaasBasicsApplication.class, properties = "flowable.async-executor-activate=false")
@Testcontainers(disabledWithoutDocker = true)
class ErpReceivingIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.3.0")
            .withDatabaseName("saas_basics")
            .withUsername("root")
            .withPassword("root");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired ErpService erp;
    @Autowired JdbcTemplate jdbc;
    private TenantAccessContextHolder.Scope context;
    private static final AuthPrincipal BUYER = new AuthPrincipal(1L, 1L, "platform", 1L, "admin", "测试采购员",
            "PLATFORM", "ONLINE", LocalDateTime.now().plusHours(4), List.of("*"), List.of(), List.of(1L), List.of());

    @BeforeEach
    void openContext() {
        AuthContext.set(BUYER);
        context = TenantAccessContextHolder.openTenant(1L, "platform", 1L);
    }

    @AfterEach
    void closeContext() {
        context.close();
        AuthContext.clear();
    }

    @Test
    void concurrentReceiptsAndAdjustmentsPreserveEveryMovement() throws Exception {
        var supplier = erp.createSupplier(new ErpMasterDataRequest("RCPT-S", "验收供应商"));
        var warehouse = erp.createWarehouse(new ErpMasterDataRequest("RCPT-W", "验收仓库"));
        var product = erp.saveProduct(new ErpProductSaveRequest("RCPT-P", "验收物料", null, null, "件",
                BigDecimal.ONE, null, 0, "ENABLED", null));
        var line = new ErpPurchaseItemRequest(product.id(), 3, new BigDecimal("4.15"));
        var body = new ErpPurchaseOrderSaveRequest(supplier.id(), warehouse.id(), null, List.of(line), null);
        var first = erp.createPurchaseOrder(body);
        var second = erp.createPurchaseOrder(body);
        assertThat(first.totalAmount()).isEqualByComparingTo("12.45");
        assertThatThrownBy(() -> erp.createPurchaseOrder(new ErpPurchaseOrderSaveRequest(
                supplier.id(), warehouse.id(), null, List.of(line), BigDecimal.ONE))).isInstanceOf(BizException.class);

        var start = new CyclicBarrier(4);
        try (var threads = Executors.newFixedThreadPool(4)) {
            var operations = List.<Callable<Boolean>>of(
                    () -> run(start, () -> erp.stockInPurchaseOrder(first.id(), null)),
                    () -> run(start, () -> erp.stockInPurchaseOrder(first.id(), null)),
                    () -> run(start, () -> erp.stockInPurchaseOrder(second.id(), null)),
                    () -> run(start, () -> erp.adjustStock(new ErpStockAdjustmentRequest(
                            product.id(), warehouse.id(), "IN", 4, "手工入库"))));
            var results = threads.invokeAll(operations, 30, TimeUnit.SECONDS);
            assertThat(results.stream().filter(f -> f.isCancelled()).count()).isZero();
            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(true, false);
            assertThat(results.get(2).get()).isTrue();
            assertThat(results.get(3).get()).isTrue();
        }
        var ledger = erp.listInventoryRecords(product.id(), warehouse.id()).reversed();
        assertThat(ledger).hasSize(3);
        assertThat(ledger).filteredOn(row -> "PURCHASE_IN".equals(row.recordType()))
                .extracting(row -> row.purchaseItemId()).doesNotContainNull().doesNotHaveDuplicates();
        int balance = 0;
        for (var entry : ledger) {
            assertThat(entry.beforeQuantity()).isEqualTo(balance);
            balance += entry.changeQuantity();
            assertThat(entry.afterQuantity()).isEqualTo(balance);
        }
        assertThat(balance).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT stock_quantity FROM erp_inventory WHERE tenant_id=1 "
                + "AND product_id=? AND warehouse_id=?", Integer.class, product.id(), warehouse.id())).isEqualTo(10);
    }

    private boolean run(CyclicBarrier barrier, Runnable action) throws Exception {
        AuthContext.set(BUYER);
        try (var scope = TenantAccessContextHolder.openTenant(1L, "platform", 1L)) {
            barrier.await(10, TimeUnit.SECONDS);
            action.run();
            return true;
        } catch (BizException e) {
            if (!"ORDER_NOT_PENDING".equals(e.getCode())) throw e;
            return false;
        } finally {
            AuthContext.clear();
        }
    }
}
