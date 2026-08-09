package com.saasbasics.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
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
import org.springframework.core.ResolvableType;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;

class MapperTenantContractTest {

    private static final Pattern CREATE_TABLE = Pattern.compile(
            "CREATE TABLE IF NOT EXISTS `([^`]+)` \\((.*?)\\) ENGINE=",
            Pattern.DOTALL
    );

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

        for (Resource migration : migrations) {
            String sql = migration.getContentAsString(StandardCharsets.UTF_8);
            Matcher matcher = CREATE_TABLE.matcher(sql);
            while (matcher.find()) {
                tableOwnership.put(matcher.group(1), matcher.group(2).contains("`tenant_id`"));
            }
        }

        Set<String> tablesWithoutTenantId = new HashSet<>();
        tableOwnership.forEach((table, tenantOwned) -> {
            if (!tenantOwned) {
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
