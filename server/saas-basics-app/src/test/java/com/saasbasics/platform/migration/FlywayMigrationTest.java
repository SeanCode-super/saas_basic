package com.saasbasics.platform.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class FlywayMigrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.3.0")
            .withDatabaseName("saas_basics")
            .withUsername("root")
            .withPassword("root");

    @Test
    void migratesAnEmptyDatabaseAndIsRepeatable() {
        Flyway flyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load();

        MigrateResult firstRun = flyway.migrate();
        MigrateResult secondRun = flyway.migrate();

        assertThat(firstRun.success).isTrue();
        assertThat(firstRun.migrationsExecuted).isEqualTo(25);
        assertThat(secondRun.success).isTrue();
        assertThat(secondRun.migrationsExecuted).isZero();
        assertThat(flyway.info().pending()).isEmpty();
        assertOrganizationSchema();
    }

    private void assertOrganizationSchema() {
        Set<String> expectedTables = Set.of(
                "org_organization",
                "org_organization_classification",
                "org_organization_capability",
                "org_organization_relation",
                "org_unit",
                "org_unit_relation",
                "org_person",
                "org_engagement",
                "org_position",
                "org_assignment"
        );

        try (Connection connection = MYSQL.createConnection("")) {
            Set<String> tables = new HashSet<>();
            try (ResultSet resultSet = connection.getMetaData().getTables(
                    connection.getCatalog(), null, "org_%", new String[]{"TABLE"})) {
                while (resultSet.next()) {
                    tables.add(resultSet.getString("TABLE_NAME"));
                }
            }
            assertThat(tables).containsAll(expectedTables);

            for (String table : expectedTables) {
                Set<String> columns = new HashSet<>();
                try (ResultSet resultSet = connection.getMetaData().getColumns(
                        connection.getCatalog(), null, table, null)) {
                    while (resultSet.next()) {
                        columns.add(resultSet.getString("COLUMN_NAME"));
                    }
                }
                assertThat(columns)
                        .as("standard fields on %s", table)
                        .contains("tenant_id", "public_id", "status", "valid_from", "valid_to",
                                "created_by", "created_at", "updated_by", "updated_at",
                                "deleted", "deleted_at", "version");
                assertThat(columns)
                        .as("industry-neutral fields on %s", table)
                        .doesNotContain("legal_name", "tax_number", "registration_number", "country_code");
            }
        } catch (SQLException exception) {
            throw new AssertionError("Unable to inspect the migrated organization schema", exception);
        }
    }
}
