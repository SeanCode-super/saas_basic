package com.saasbasics.platform.trash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.auth.DataPermissionProvider;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.file.TenantFileRestoreCapacity;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.common.tenant.TenantMembershipCapacity;
import com.saasbasics.platform.config.MybatisPlusConfig;
import com.saasbasics.platform.modules.system.mapper.TrashMapper;
import com.saasbasics.platform.modules.system.trash.TrashActionRequest;
import com.saasbasics.platform.modules.system.trash.TrashItemResponse;
import com.saasbasics.platform.modules.system.trash.TrashService;
import com.saasbasics.platform.security.EnterpriseDataPermissionHandler;
import java.time.LocalDateTime;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class TrashServiceMapperTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Test
    void restoresOnlyRegisteredTenantRowsAndGuardsQuotaAndGrantHistory() throws Exception {
        verifyRecovery(new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
    }

    static void verifyRecovery(DataSource source) throws Exception {
        JdbcTemplate db = new JdbcTemplate(source);
        db.execute("CREATE TABLE gen_dynamic_model(tenant_id BIGINT, table_name VARCHAR(64), model_name VARCHAR(64), model_code VARCHAR(64), deleted INT)");
        db.update("INSERT INTO gen_dynamic_model VALUES (7,'biz_dyn_t7_items','A','items',0),(8,'biz_dyn_t8_items','B','items',0),"
                + "(7,'biz_dyn_tasks','Legacy','tasks',0),(7,'biz_dyn_t8_items','invalid','t8_items',0)");
        for (String name : List.of("biz_dyn_t7_items", "biz_dyn_t8_items", "biz_dyn_tasks", "biz_dyn_orphan", "iam_user", "iam_department", "org_unit",
                "file_object", "iam_role_menu", "iam_menu_permission_v83_archive", "erp_legacy")) {
            db.execute("CREATE TABLE " + name + "(id BIGINT PRIMARY KEY, tenant_id BIGINT, title VARCHAR(20), deleted INT)");
            db.update("INSERT INTO " + name + " VALUES (1,7,'one',1),(2,8,'two',1)");
        }
        db.execute("CREATE TABLE crm_customer(id BIGINT PRIMARY KEY, tenant_id BIGINT, name VARCHAR(20), deleted INT, deleted_at DATETIME(3))");
        db.execute("CREATE TABLE crm_business(id BIGINT PRIMARY KEY, tenant_id BIGINT, name VARCHAR(20), customer_id BIGINT, deleted INT, deleted_at DATETIME(3))");
        db.update("INSERT INTO crm_customer VALUES (1,7,'A',1,'2026-01-02')");
        db.update("INSERT INTO crm_business VALUES (1,7,'same',1,1,'2026-01-02'),(2,7,'older',1,1,'2026-01-01'),(3,8,'other',1,1,'2026-01-02')");

        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setMapperLocations(new ClassPathResource("mapper/TrashMapper.xml"));
        factory.setPlugins(new MybatisPlusConfig().mybatisPlusInterceptor(new EnterpriseDataPermissionHandler(
                new StaticListableBeanFactory().getBeanProvider(DataPermissionProvider.class))));
        var members = mock(TenantMembershipCapacity.class);
        var files = mock(TenantFileRestoreCapacity.class);
        var service = new TrashService(new SqlSessionTemplate(factory.getObject()).getMapper(TrashMapper.class), members, files);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        var actor = new AuthPrincipal(1L, 7L, "seven", 10L, "admin", "Admin", "TENANT", "ACTIVE",
                LocalDateTime.now().plusHours(1), List.of(), List.of(), List.of(), List.of());
        AuthContext.set(actor);
        try (var scope = TenantAccessContextHolder.openAuthenticated(actor)) {
            assertThat(service.listTrash("ALL", null, null)).extracting(TrashItemResponse::tableName)
                    .contains("biz_dyn_t7_items", "biz_dyn_tasks", "erp_legacy")
                    .doesNotContain("biz_dyn_t8_items", "biz_dyn_orphan", "iam_role_menu", "iam_menu_permission_v83_archive");
            assertThat(service.listTrash(null, "erp_legacy", null).getFirst().deletedAt()).isNull();
            for (String table : List.of("biz_dyn_t8_items", "iam_role_menu", "iam_menu_permission_v83_archive")) {
                assertThatThrownBy(() -> service.restore(new TrashActionRequest(table, 1L, false))).isInstanceOf(BizException.class);
            }
            for (String table : List.of("iam_user", "iam_department", "org_unit", "file_object", "biz_dyn_t7_items", "biz_dyn_tasks")) {
                transaction.executeWithoutResult(status -> service.restore(new TrashActionRequest(table, 1L, false)));
                assertThat(db.queryForObject("SELECT deleted FROM " + table + " WHERE id=2", Integer.class)).isOne();
            }
            verify(members).requireSeat(7L);
            verify(members, times(2)).requireDepartment(7L);
            verify(files).requireCapacity(7L, 1L);
            doThrow(new BizException("LIMIT", "limit")).when(members).requireSeat(7L);
            db.update("UPDATE iam_user SET deleted=1 WHERE id=1");
            assertThatThrownBy(() -> transaction.executeWithoutResult(status -> service.restore(
                    new TrashActionRequest("iam_user", 1L, false)))).isInstanceOf(BizException.class);
            assertThat(db.queryForObject("SELECT deleted FROM iam_user WHERE id=1", Integer.class)).isOne();
            transaction.executeWithoutResult(status -> service.restore(new TrashActionRequest("crm_customer", 1L, true)));
            assertThat(db.queryForList("SELECT deleted FROM crm_business ORDER BY id", Integer.class)).containsExactly(0, 1, 1);
            assertThatThrownBy(() -> service.purge(new TrashActionRequest("crm_customer", 1L, true))).isInstanceOf(BizException.class);
            transaction.executeWithoutResult(status -> service.purge(new TrashActionRequest("erp_legacy", 1L, false)));
            assertThat(db.queryForList("SELECT id FROM erp_legacy ORDER BY id", Long.class)).containsExactly(2L);
        } finally {
            AuthContext.clear();
        }
    }
}
