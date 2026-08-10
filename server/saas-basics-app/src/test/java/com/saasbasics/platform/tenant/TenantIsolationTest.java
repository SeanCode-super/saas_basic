package com.saasbasics.platform.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saasbasics.platform.SaasBasicsApplication;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.modules.iam.entity.UserEntity;
import com.saasbasics.platform.modules.iam.mapper.UserMapper;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.MyBatisSystemException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = SaasBasicsApplication.class)
@Testcontainers(disabledWithoutDocker = true)
class TenantIsolationTest {

    private static final long TENANT_ALPHA = 101L;
    private static final long TENANT_BETA = 202L;

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

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearTenantContext() {
        TenantAccessContextHolder.clear();
    }

    @Test
    void mapperReadsAndUpdatesStayInsideTheAuthenticatedTenant() {
        UserEntity user = newUser("alpha");
        user.setTenantId(TENANT_BETA);

        try (TenantAccessContextHolder.Scope ignored = TenantAccessContextHolder.openTenant(
                TENANT_ALPHA, "alpha", 1L)) {
            assertThat(userMapper.insert(user)).isEqualTo(1);
            assertThat(user.getTenantId()).isEqualTo(TENANT_ALPHA);
            assertThat(userMapper.selectById(user.getId())).isNotNull();
        }

        Long storedTenantId = jdbcTemplate.queryForObject(
                "SELECT tenant_id FROM iam_user WHERE id = ?", Long.class, user.getId());
        assertThat(storedTenantId).isEqualTo(TENANT_ALPHA);

        try (TenantAccessContextHolder.Scope ignored = TenantAccessContextHolder.openTenant(
                TENANT_BETA, "beta", 2L)) {
            assertThat(userMapper.selectById(user.getId())).isNull();
            user.setNickname("cross-tenant-update");
            assertThat(userMapper.updateById(user)).isZero();
        }

        String storedNickname = jdbcTemplate.queryForObject(
                "SELECT nickname FROM iam_user WHERE id = ?", String.class, user.getId());
        assertThat(storedNickname).isEqualTo("alpha user");
    }

    @Test
    void tenantMapperAccessFailsClosedWithoutAContext() {
        assertThatThrownBy(() -> userMapper.selectById(1L))
                .isInstanceOf(MyBatisSystemException.class)
                .hasRootCauseInstanceOf(BizException.class)
                .rootCause()
                .extracting("code")
                .isEqualTo("TENANT_CONTEXT_REQUIRED");
    }

    private UserEntity newUser(String prefix) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        UserEntity user = new UserEntity();
        user.setUserCode(prefix + "_" + suffix);
        user.setUsername(prefix + "." + suffix);
        user.setNickname(prefix + " user");
        user.setEmployeeId(0L);
        user.setUserType("STAFF");
        user.setStatus("ENABLED");
        user.setPasswordHash("test-only");
        user.setNeedResetPassword(false);
        return user;
    }
}
