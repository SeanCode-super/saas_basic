package com.saasbasics.platform.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
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
        assertThat(firstRun.migrationsExecuted).isEqualTo(30);
        assertThat(secondRun.success).isTrue();
        assertThat(secondRun.migrationsExecuted).isZero();
        assertThat(flyway.info().pending()).isEmpty();
        assertOrganizationSchema();
        assertOrganizationMigrationSchema();
        assertIdentityBindingSchema();
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

    private void assertOrganizationMigrationSchema() {
        Set<String> expectedTables = Set.of(
                "org_migration_scope",
                "org_migration_run",
                "org_migration_resource_map",
                "org_migration_issue"
        );

        try (Connection connection = MYSQL.createConnection("")) {
            Set<String> tables = new HashSet<>();
            try (ResultSet resultSet = connection.getMetaData().getTables(
                    connection.getCatalog(), null, "org_migration_%", new String[]{"TABLE"})) {
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
                        .as("migration control fields on %s", table)
                        .contains("tenant_id", "public_id", "status",
                                "created_by", "created_at", "updated_by", "updated_at",
                                "deleted", "deleted_at", "version");
                assertThat(columns)
                        .as("industry-neutral fields on %s", table)
                        .doesNotContain("legal_name", "tax_number", "registration_number", "country_code");
            }

            assertThat(indexNames(connection, "org_migration_run"))
                    .contains("uk_org_migration_run_active_tenant");
            assertThat(indexNames(connection, "org_migration_resource_map"))
                    .contains("uk_org_migration_map_source_target", "uk_org_migration_map_target");
            assertThat(indexNames(connection, "org_migration_issue"))
                    .contains("uk_org_migration_issue_identity");
        } catch (SQLException exception) {
            throw new AssertionError("Unable to inspect the organization migration schema", exception);
        }
    }

    private Set<String> indexNames(Connection connection, String table) throws SQLException {
        Set<String> indexes = new HashSet<>();
        try (ResultSet resultSet = connection.getMetaData().getIndexInfo(
                connection.getCatalog(), null, table, true, false)) {
            while (resultSet.next()) {
                indexes.add(resultSet.getString("INDEX_NAME"));
            }
        }
        return indexes;
    }

    private void assertIdentityBindingSchema() {
        try (Connection connection = MYSQL.createConnection("")) {
            Set<String> bindingColumns = new HashSet<>();
            try (ResultSet resultSet = connection.getMetaData().getColumns(
                    connection.getCatalog(), null, "iam_user_person_binding", null)) {
                while (resultSet.next()) {
                    bindingColumns.add(resultSet.getString("COLUMN_NAME"));
                }
            }
            assertThat(bindingColumns).contains(
                    "tenant_id", "public_id", "user_id", "person_public_id", "status",
                    "valid_from", "valid_to", "active_user_id", "version"
            );
            assertThat(indexNames(connection, "iam_user_person_binding")).contains(
                    "uk_iam_user_person_binding_public_id",
                    "uk_iam_user_person_binding_active_user",
                    "idx_iam_user_person_binding_person"
            );
            assertThat(columnNames(connection, "iam_user")).contains("public_id");
            assertThat(columnNames(connection, "iam_session")).contains(
                    "public_id", "subject_binding_public_id", "selected_assignment_public_id"
            );
            assertNullableColumn(connection, "iam_user", "public_id");
            assertNullableColumn(connection, "iam_session", "public_id");
            assertUuidV7Column(connection, "iam_user");
            assertUuidV7Column(connection, "iam_session");
            assertLegacyWriterCompatibility(connection);
        } catch (SQLException exception) {
            throw new AssertionError("Unable to inspect the IAM subject binding schema", exception);
        }
    }

    private Set<String> columnNames(Connection connection, String table) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (ResultSet resultSet = connection.getMetaData().getColumns(
                connection.getCatalog(), null, table, null)) {
            while (resultSet.next()) {
                columns.add(resultSet.getString("COLUMN_NAME"));
            }
        }
        return columns;
    }

    private void assertUuidV7Column(Connection connection, String table) throws SQLException {
        try (ResultSet resultSet = connection.createStatement().executeQuery(
                "SELECT public_id FROM " + table + " ORDER BY id")) {
            while (resultSet.next()) {
                java.util.UUID publicId = java.util.UUID.fromString(resultSet.getString("public_id"));
                assertThat(publicId.version()).isEqualTo(7);
                assertThat(publicId.variant()).isEqualTo(2);
            }
        }
    }

    private void assertNullableColumn(Connection connection, String table, String column) throws SQLException {
        try (ResultSet resultSet = connection.getMetaData().getColumns(
                connection.getCatalog(), null, table, column)) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString("IS_NULLABLE")).isEqualTo("YES");
        }
    }

    private void assertLegacyWriterCompatibility(Connection connection) throws SQLException {
        try (PreparedStatement userInsert = connection.prepareStatement("""
                INSERT INTO iam_user (tenant_id, user_code, username)
                VALUES (99001, 'ROLLBACK_USER', 'rollback.user')
                """);
             PreparedStatement sessionInsert = connection.prepareStatement("""
                INSERT INTO iam_session (
                  tenant_id, tenant_code, session_no, user_id, username,
                  access_token_hash, expire_at
                )
                SELECT tenant_id, 'rollback', 'ROLLBACK_SESSION', id, username,
                       'rollback-session-token-hash', DATE_ADD(NOW(3), INTERVAL 1 HOUR)
                FROM iam_user
                WHERE tenant_id = 99001 AND user_code = 'ROLLBACK_USER' AND deleted = 0
                """)) {
            assertThat(userInsert.executeUpdate()).isEqualTo(1);
            assertThat(sessionInsert.executeUpdate()).isEqualTo(1);
        }

        assertUuidV7Column(connection, "iam_user");
        assertUuidV7Column(connection, "iam_session");
        assertThat(nullPublicIdentifierCount(connection, "iam_user")).isZero();
        assertThat(nullPublicIdentifierCount(connection, "iam_session")).isZero();
    }

    private long nullPublicIdentifierCount(Connection connection, String table) throws SQLException {
        try (ResultSet resultSet = connection.createStatement().executeQuery(
                "SELECT COUNT(*) FROM " + table + " WHERE public_id IS NULL")) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getLong(1);
        }
    }
}
