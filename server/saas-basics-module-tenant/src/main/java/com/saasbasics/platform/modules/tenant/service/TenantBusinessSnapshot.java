package com.saasbasics.platform.modules.tenant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.saasbasics.platform.common.exception.BizException;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.io.IOException;
import java.io.UncheckedIOException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import com.saasbasics.platform.modules.tenant.mapper.TenantSnapshotSchemaMapper;
import org.springframework.stereotype.Component;

/** Rows that can be replaced independently for a tenant in the deployed business schema. */
@Component
final class TenantBusinessSnapshot {
    static final List<String> BASE_TABLES = List.of(
            "plat_tenant",
            "iam_user",
            "iam_role",
            "iam_department",
            "iam_employee",
            "sys_config",
            "sys_dict_type",
            "plat_tenant_quota",
            "plat_tenant_bill",
            "plat_tenant_metering_log"
    );


    record Column(String name, int type, String sqlType, int size, int scale, boolean nullable) {
        boolean binary() {
            return type == Types.BINARY || type == Types.VARBINARY || type == Types.LONGVARBINARY
                    || type == Types.BLOB || (type == Types.BIT && size > 1);
        }
    }

    private final JdbcTemplate jdbc;

    private final TenantSnapshotSchemaMapper schemaMapper;

    TenantBusinessSnapshot(JdbcTemplate jdbc, TenantSnapshotSchemaMapper schemaMapper) {
        this.jdbc = jdbc;
        this.schemaMapper = schemaMapper;
    }

    Map<String, List<Column>> schema(long tenantId) {
        return jdbc.execute((ConnectionCallback<Map<String, List<Column>>>) connection -> {
            DatabaseMetaData md = connection.getMetaData();
            String catalog = connection.getCatalog();
            String schema = connection.getSchema();
            Map<String, List<Column>> result = new TreeMap<>();
            List<String> names = new ArrayList<>();
            try (ResultSet tables = md.getTables(catalog, schema, "%", new String[]{"TABLE", "BASE TABLE"})) {
                while (tables.next()) {
                    String name = tables.getString("TABLE_NAME");
                    if (ownsBusinessTable(name, tenantId)) names.add(name);
                }
            }
            for (String name : names) {
                identifier(name);
                List<Column> columns = new ArrayList<>();
                // JDBC column lookup uses LIKE, so filter the exact table as well.
                try (ResultSet rs = md.getColumns(catalog, schema, name, "%")) {
                    while (rs.next()) {
                        if (!name.equals(rs.getString("TABLE_NAME")) || "YES".equals(rs.getString("IS_GENERATEDCOLUMN"))) continue;
                        String field = rs.getString("COLUMN_NAME");
                        identifier(field);
                        columns.add(new Column(field, rs.getInt("DATA_TYPE"), rs.getString("TYPE_NAME"),
                                rs.getInt("COLUMN_SIZE"), rs.getInt("DECIMAL_DIGITS"), rs.getInt("NULLABLE") != 0));
                    }
                }
                if (columns.stream().anyMatch(c -> c.name().equals("tenant_id"))) result.put(name, columns);
            }
            return result;
        });
    }

    long exportRows(String table, List<Column> columns, long tenantId, JsonGenerator output) {
        if (columns == null ? !BASE_TABLES.contains(table) : !ownsBusinessTable(table, tenantId)) {
            throw invalid("不支持的导出表：" + table);
        }
        String filter = table.equals("plat_tenant") ? "id" : "tenant_id";
        String sql = "SELECT " + (columns == null ? "*" : fields(columns)) + " FROM " + identifier(table) + " WHERE " + filter + " = ?";
        return jdbc.execute((ConnectionCallback<Long>) con -> {
            try (PreparedStatement ps = con.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                if ("MySQL".equalsIgnoreCase(con.getMetaData().getDatabaseProductName())) ps.setFetchSize(Integer.MIN_VALUE);
                ps.setLong(1, tenantId);
                long count = 0;
                ColumnMapRowMapper baseRows = new ColumnMapRowMapper();
                try (ResultSet rs = ps.executeQuery()) {
                    output.writeStartArray();
                    while (rs.next()) {
                        if (columns == null) output.writeObject(baseRows.mapRow(rs, 0));
                        else {
                            output.writeStartObject();
                            for (Column c : columns) {
                                output.writeFieldName(c.name());
                                if (c.binary()) output.writeObject(rs.getBytes(c.name()));
                                else output.writeString(rs.getString(c.name()));
                            }
                            output.writeEndObject();
                        }
                        count++;
                    }
                    output.writeEndArray();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                return count;
            }
        });
    }

    void checkRestoreTables(Map<String, List<Column>> tables) {
        jdbc.execute((ConnectionCallback<Void>) con -> {
            DatabaseMetaData md = con.getMetaData();
            for (String table : tables.keySet()) {
                if ("MySQL".equalsIgnoreCase(md.getDatabaseProductName())) {
                    String engine = schemaMapper.storageEngine(table);
                    if (!"InnoDB".equalsIgnoreCase(engine)) throw invalid("数据表不支持事务恢复：" + table);
                }
                try (ResultSet fk = md.getExportedKeys(con.getCatalog(), con.getSchema(), table)) {
                    if (fk.next()) throw invalid("存在外键引用，需按业务依赖执行专用恢复：" + table);
                }
            }
            return null;
        });
    }

    static long readRows(JsonParser input, ObjectMapper mapper, java.util.function.Consumer<JsonNode> consumer) throws IOException {
        if (input.nextToken() != JsonToken.START_ARRAY) throw invalid("归档缺少行数组");
        long count = 0;
        while (input.nextToken() == JsonToken.START_OBJECT) {
            if (consumer == null) input.skipChildren();
            else consumer.accept(mapper.readTree(input));
            count++;
        }
        if (input.currentToken() != JsonToken.END_ARRAY || input.nextToken() != null) throw invalid("归档行格式无效");
        return count;
    }

    long replace(String table, List<Column> columns, JsonParser input, ObjectMapper mapper, long tenantId) throws IOException {
        if (!ownsBusinessTable(table, tenantId) || columns.stream().noneMatch(c -> c.name().equals("tenant_id"))) {
            throw invalid("不支持的业务恢复表：" + table);
        }
        jdbc.update("DELETE FROM " + identifier(table) + " WHERE tenant_id = ?", tenantId);
        String sql = "INSERT INTO " + identifier(table) + " (" + fields(columns) + ") VALUES ("
                + String.join(",", Collections.nCopies(columns.size(), "?")) + ")";
        List<JsonNode> batch = new ArrayList<>();
        long count = readRows(input, mapper, row -> {
            if (row.size() != columns.size() || !Long.toString(tenantId).equals(row.path("tenant_id").asText())) {
                throw invalid("归档行与租户或表结构不一致：" + table);
            }
            batch.add(row);
            if (batch.size() == 200) {
                insertBatch(sql, columns, batch);
                batch.clear();
            }
        });
        if (!batch.isEmpty()) insertBatch(sql, columns, batch);
        return count;
    }

    private void insertBatch(String sql, List<Column> columns, List<JsonNode> batch) {
        jdbc.batchUpdate(sql, batch, batch.size(), (ps, row) -> {
            for (int i = 0; i < columns.size(); i++) {
                Column c = columns.get(i);
                JsonNode v = row.get(c.name());
                if (v == null) throw invalid("缺少业务字段：" + c.name());
                if (v.isNull()) ps.setNull(i + 1, c.type());
                else if (c.binary()) ps.setBytes(i + 1, Base64.getDecoder().decode(v.asText()));
                else ps.setString(i + 1, v.asText());
            }
        });
    }

    private static boolean ownsBusinessTable(String table, long tenantId) {
        if (table.startsWith("biz_dyn_")) return table.startsWith("biz_dyn_t" + tenantId + "_");
        return table.startsWith("erp_") || table.startsWith("crm_") || table.startsWith("biz_");
    }

    private static String fields(List<Column> columns) {
        return String.join(",", columns.stream().map(c -> identifier(c.name())).toList());
    }

    private static String identifier(String value) {
        if (!value.matches("[A-Za-z_][A-Za-z0-9_]{0,63}")) throw invalid("不支持的数据库标识");
        return "`" + value + "`";
    }

    static BizException invalid(String message) {
        return new BizException("INVALID_SNAPSHOT", message);
    }
}
