package com.saasbasics.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.saasbasics.platform.modules.audit.mapper.AuditQueryMapper;
import com.saasbasics.platform.modules.organization.internal.persistence.mapper.PersonMapper;
import java.lang.reflect.Modifier;
import java.util.Set;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class DbMapperScanConfigTest {

    @Test
    void dbProfileBindsEveryCustomMapperStatementAndRequiresSqlInXml() throws Exception {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(new DriverManagerDataSource());
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:/mapper/**/*.xml"));
        SqlSessionFactory sqlSessionFactory = factory.getObject();

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("db");
            context.registerBean(SqlSessionFactory.class, () -> sqlSessionFactory);
            context.register(DbMapperScanConfig.class);
            context.refresh();

            assertThat(context.getBean(AuditQueryMapper.class)).isNotNull();
            assertThat(context.getBean(PersonMapper.class)).isNotNull();
            var configuration = sqlSessionFactory.getConfiguration();
            var sqlAnnotations = Set.of("Select", "Update", "Insert", "Delete", "SelectProvider",
                    "UpdateProvider", "InsertProvider", "DeleteProvider");
            String deferredPayment = "com.saasbasics.platform.modules.integration.mapper.PayOrderMapper.selectByOrderNoOrTradeNo";
            for (var mapper : configuration.getMapperRegistry().getMappers()) {
                for (var method : mapper.getDeclaredMethods()) {
                    if (!Modifier.isAbstract(method.getModifiers())) {
                        continue;
                    }
                    String statement = mapper.getName() + "." + method.getName();
                    assertThat(configuration.hasStatement(statement)).as(statement).isTrue();
                    if (!statement.equals(deferredPayment)) {
                        for (var annotation : method.getDeclaredAnnotations()) {
                            assertThat(sqlAnnotations).as("SQL belongs in Mapper XML: %s", statement)
                                    .doesNotContain(annotation.annotationType().getSimpleName());
                        }
                    }
                }
            }
        }
    }
}
