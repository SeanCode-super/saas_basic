package com.saasbasics.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.saasbasics.platform.common.auth.DataPermissionProvider;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.security.EnterpriseDataPermissionHandler;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;

class MapperTenantContractTest {

    private static final Pattern CREATE_TABLE = Pattern.compile(
            "\\bCREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?`?(\\w+)`?\\s*\\((.*?)\\)\\s*ENGINE\\s*=",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern TENANT_COLUMN = Pattern.compile(
            "^\\s*`?tenant_id`?\\s+\\w+", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern ADD_TENANT_COLUMN = Pattern.compile(
            "\\bALTER\\s+TABLE\\s+`?(\\w+)`?\\s+ADD\\s+(?:COLUMN\\s+)?`?tenant_id`?\\s+",
            Pattern.CASE_INSENSITIVE);

    @Test
    void tenantInterceptorExemptsIndustryCatalogAndScopesAuditResources() {
        var dataPermissions = new EnterpriseDataPermissionHandler(
                new StaticListableBeanFactory().getBeanProvider(DataPermissionProvider.class));
        var interceptor = new MybatisPlusConfig().mybatisPlusInterceptor(dataPermissions).getInterceptors().stream()
                .filter(TenantLineInnerInterceptor.class::isInstance)
                .map(TenantLineInnerInterceptor.class::cast)
                .findFirst().orElseThrow();
        try (var scope = TenantAccessContextHolder.openTenant(17L, "test", 21L)) {
            var handler = interceptor.getTenantLineHandler();
            assertThat(handler.ignoreTable("plat_industry_service")).isTrue();
            assertThat(handler.ignoreTable("iam_api_resource")).isFalse();
            assertThat(handler.getTenantId().toString()).isEqualTo("17");
        }
    }

    @Test
    void everyTenantMapperEntityExposesTenantId() throws Exception {
        Resource[] mapperResources = new PathMatchingResourcePatternResolver().getResources(
                "classpath*:com/saasbasics/platform/modules/**/*Mapper.class"
        );
        CachingMetadataReaderFactory metadata = new CachingMetadataReaderFactory();
        Set<String> checkedTables = new HashSet<>();

        for (Resource resource : mapperResources) {
            String className = metadata.getMetadataReader(resource).getClassMetadata().getClassName();
            Class<?> mapperType = Class.forName(className);
            if (!BaseMapper.class.isAssignableFrom(mapperType)) {
                continue;
            }
            Class<?> entityType = ResolvableType.forClass(mapperType)
                    .as(BaseMapper.class)
                    .getGeneric(0)
                    .resolve();
            assertThat(entityType).as("entity type for %s", className).isNotNull();

            TableName tableName = entityType.getAnnotation(TableName.class);
            assertThat(tableName).as("@TableName on %s", entityType.getName()).isNotNull();
            String table = tableName.value();
            checkedTables.add(table);
            if (MybatisPlusConfig.PLATFORM_TABLES.contains(table)) {
                continue;
            }

            assertThat(hasMethod(entityType, "getTenantId") && hasMethod(entityType, "setTenantId", Long.class))
                    .as("tenant accessors on %s for table %s", entityType.getName(), table)
                    .isTrue();
        }

        assertThat(checkedTables).hasSizeGreaterThan(30);
    }

    @Test
    void migrationSchemaHasOnlyReviewedPlatformTablesWithoutTenantId() throws IOException {
        Resource[] migrations = new PathMatchingResourcePatternResolver().getResources(
                "classpath*:db/migration/V*.sql"
        );
        Map<String, Boolean> tableOwnership = new HashMap<>();
        Set<String> tenantColumnsAddedByMigration = new HashSet<>();

        for (Resource migration : migrations) {
            String sql = migration.getContentAsString(StandardCharsets.UTF_8);
            Matcher matcher = CREATE_TABLE.matcher(sql);
            while (matcher.find()) {
                tableOwnership.merge(matcher.group(1), TENANT_COLUMN.matcher(matcher.group(2)).find(),
                        Boolean::logicalOr);
            }
            ADD_TENANT_COLUMN.matcher(sql).results()
                    .forEach(match -> tenantColumnsAddedByMigration.add(match.group(1)));
        }

        Set<String> tablesWithoutTenantId = new HashSet<>();
        tableOwnership.forEach((table, tenantOwned) -> {
            if (!tenantOwned && !tenantColumnsAddedByMigration.contains(table)) {
                tablesWithoutTenantId.add(table);
            }
        });
        assertThat(tableOwnership).hasSizeGreaterThan(60);
        assertThat(tablesWithoutTenantId).containsExactlyInAnyOrderElementsOf(MybatisPlusConfig.PLATFORM_TABLES);
    }

    private boolean hasMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            Method ignored = type.getMethod(name, parameterTypes);
            return true;
        } catch (NoSuchMethodException exception) {
            return false;
        }
    }
}
