package com.saasbasics.platform.modules.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.saasbasics.platform.common.auth.DataPermissionContext;
import com.saasbasics.platform.common.auth.DataPermissionProvider;
import com.saasbasics.platform.common.auth.DataPermissionSqlSpec;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.config.MybatisPlusConfig;
import com.saasbasics.platform.modules.file.mapper.ExportDatasetMapper;
import com.saasbasics.platform.security.EnterpriseDataPermissionHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

class ExportDatasetCatalogTest {
    private ExportDatasetCatalogService catalog;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE gen_dynamic_model(id BIGINT,tenant_id BIGINT,model_code VARCHAR,model_name VARCHAR,table_name VARCHAR,status VARCHAR,deleted INT)");
        jdbc.execute("CREATE TABLE gen_dynamic_field(id BIGINT,tenant_id BIGINT,model_id BIGINT,field_code VARCHAR,field_name VARCHAR,column_type VARCHAR,sort_no INT,deleted INT)");
        jdbc.execute("INSERT INTO gen_dynamic_model VALUES(8,2,'parts','Parts','biz_dyn_t2_parts','PUBLISHED',0)");
        jdbc.execute("INSERT INTO gen_dynamic_field VALUES(1,2,8,'part','Part','VARCHAR',1,0)");
        jdbc.execute("CREATE TABLE biz_dyn_t2_parts(id BIGINT,tenant_id BIGINT,part VARCHAR,created_by BIGINT,dept_id BIGINT,deleted INT)");
        jdbc.execute("INSERT INTO biz_dyn_t2_parts VALUES(1,2,'A',77,1,0),(2,2,'B',66,2,0),(3,2,'A',77,1,1),(4,3,'A',77,1,0)");
        jdbc.execute("CREATE TABLE iam_user(id BIGINT,tenant_id BIGINT,username VARCHAR,deleted INT)");
        jdbc.execute("INSERT INTO iam_user VALUES(77,2,'alpha',0),(66,2,'beta',0),(78,3,'alpha',0)");
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(ds);
        var providers = new StaticListableBeanFactory().getBeanProvider(DataPermissionProvider.class);
        factory.setPlugins(new MybatisPlusConfig().mybatisPlusInterceptor(new EnterpriseDataPermissionHandler(providers)));
        factory.setMapperLocations(new ClassPathResource("mapper/ExportDatasetMapper.xml"));
        catalog = new ExportDatasetCatalogService(new SqlSessionTemplate(factory.getObject()).getMapper(ExportDatasetMapper.class));
    }

    @Test
    void validPublishedColumnsAreBoundAndDataPermissionAppliesToBothCountsAndResults() {
        try (var tenant = TenantAccessContextHolder.openTenant(2L,"target",77L)) {
            DataPermissionSqlSpec own = new DataPermissionSqlSpec();
            own.setTenantId(2L);
            own.addUserId(77L);
            assertThat(catalog.countRows(2L,"dynamic:parts",Map.of("part","A"),own)).isEqualTo(1);
            assertThat(catalog.countRows(2L,"dynamic:parts",Map.of("part","unmatched'value"),own)).isZero();
            var rows = new ArrayList<Map<String,Object>>();
            catalog.streamRows(2L,"dynamic:parts",List.of("part"),Map.of(),10,own,rows::add);
            assertThat(rows).containsExactly(Map.of("part","A"));
            rows.clear();
            catalog.streamRows(2L,"iam_user",List.of("username"),Map.of(),10,own,rows::add);
            assertThat(rows).containsExactly(Map.of("username","alpha"));
            assertThat(DataPermissionContext.getDataScope()).isNull();
            assertThat(DataPermissionContext.getSqlSpec()).isNull();
        }
    }

    @Test
    void unpublishedForeignTablesAndUnlistedColumnsAreRejected() {
        try (var tenant = TenantAccessContextHolder.openTenant(2L,"target",77L)) {
            assertThatExceptionOfType(BizException.class).isThrownBy(() ->
                    catalog.streamRows(2L,"iam_user",List.of("password_hash"),Map.of(),10,row -> {}));
            assertThatExceptionOfType(BizException.class).isThrownBy(() ->
                    catalog.countRows(2L,"iam_user",Map.of("internal_note","x")));
            for (String table : List.of("iam_user","biz_dyn_t3_parts","biz_dyn_bad-name")) {
                jdbc.update("UPDATE gen_dynamic_model SET table_name=?",table);
                assertThatExceptionOfType(BizException.class)
                        .isThrownBy(() -> catalog.countRows(2L,"dynamic:parts",Map.of()))
                        .extracting(BizException::getCode).isEqualTo("INVALID_EXPORT_IDENTIFIER");
            }
            jdbc.update("UPDATE gen_dynamic_model SET status='DRAFT'");
            assertThatExceptionOfType(BizException.class).isThrownBy(() -> catalog.countRows(2L,"dynamic:parts",Map.of()))
                    .extracting(BizException::getCode).isEqualTo("EXPORT_DATASET_NOT_FOUND");
        }
    }
}
