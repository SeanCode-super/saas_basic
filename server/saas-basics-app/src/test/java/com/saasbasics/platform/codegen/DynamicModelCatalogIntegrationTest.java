package com.saasbasics.platform.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import com.saasbasics.platform.SaasBasicsApplication;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.modules.ai.api.PublishedModelCatalog;
import com.saasbasics.platform.modules.codegen.dto.DynamicFieldDto;
import com.saasbasics.platform.modules.codegen.dto.DynamicModelPublishRequest;
import com.saasbasics.platform.modules.codegen.service.AiAppBuilderService;
import java.time.LocalDateTime;
import java.util.List;
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
class DynamicModelCatalogIntegrationTest {
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

    @Autowired AiAppBuilderService builder;
    @Autowired PublishedModelCatalog catalog;
    @Autowired com.saasbasics.platform.modules.codegen.service.DynamicDataService data;
    @Autowired JdbcTemplate jdbc;

    @Test
    void onlyPublishedTenantFieldsAppearInTheCatalogAndArchivedMenusDisappear() {
        var principal = new AuthPrincipal(1L, 1L, "platform", 1L, "admin", "Admin", "PLATFORM", "ONLINE",
                LocalDateTime.now().plusHours(1), List.of("*"), List.of(), List.of(1L), List.of());
        AuthContext.set(principal);
        try (var scope = TenantAccessContextHolder.openAuthenticated(principal)) {
            var field = new DynamicFieldDto(null, "part_note", "物料备注", "VARCHAR(64)", "String", "input",
                    false, true, "LIKE", null, null, 1);
            var request = new DynamicModelPublishRequest("qa_catalog", "目录验收模型", null, null, null,
                    List.of(field), false, List.of());
            var model = builder.publishApp(request);
            jdbc.update("INSERT INTO gen_dynamic_field (tenant_id,model_id,field_code,field_name,column_type) "
                    + "VALUES (?,?,?,?,?)", 481, model.id(), "foreign_note", "其他租户字段", "VARCHAR(64)");
            assertThat(catalog.columns(1L)).filteredOn(column -> column.tableName().equals(model.tableName()))
                    .extracting(PublishedModelCatalog.Column::fieldCode).containsExactly("part_note");

            long rowId = data.createRecord("qa_catalog", java.util.Map.of("part_note", "原始记录"));
            builder.appendField(model.id(), "checked_on", "点检日期", AiAppBuilderService.FieldKind.DATE);
            builder.appendField(model.id(), "checked_on", "点检日期", AiAppBuilderService.FieldKind.DATE);
            assertThat(data.getById("qa_catalog", rowId)).containsEntry("part_note", "原始记录");
            assertThat(data.getModelMeta("qa_catalog").fields()).filteredOn(f -> "checked_on".equals(f.fieldCode())).hasSize(1);
            data.updateRecord("qa_catalog", rowId, java.util.Map.of("checked_on", "2026-11-20"));
            assertThat(String.valueOf(data.getById("qa_catalog", rowId).get("checked_on"))).startsWith("2026-11-20");
            builder.deleteModel(model.id());
            assertThat(catalog.columns(1L)).noneMatch(column -> column.tableName().equals(model.tableName()));
            assertThat(jdbc.queryForObject("SELECT deleted FROM iam_menu WHERE tenant_id=? AND id=?",
                    Integer.class, 1L, model.menuId())).isEqualTo(1);
        } finally {
            AuthContext.clear();
        }
    }
}
