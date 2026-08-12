package db.migration;

import com.saasbasics.platform.common.id.UuidV7Generator;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V28__backfill_iam_public_identifiers extends BaseJavaMigration {

    private final UuidV7Generator uuidGenerator = new UuidV7Generator();

    @Override
    public void migrate(Context context) throws Exception {
        backfill(context, "iam_user");
        backfill(context, "iam_session");
    }

    private void backfill(Context context, String table) throws SQLException {
        String selectSql = "SELECT id, public_id FROM " + table + " ORDER BY id FOR UPDATE";
        String updateSql = "UPDATE " + table + " SET public_id = ? WHERE id = ? AND public_id IS NULL";
        try (Statement select = context.getConnection().createStatement();
             ResultSet rows = select.executeQuery(selectSql);
             PreparedStatement update = context.getConnection().prepareStatement(updateSql)) {
            while (rows.next()) {
                String publicId = rows.getString("public_id");
                if (publicId != null) {
                    requireUuidV7(table, rows.getLong("id"), publicId);
                    continue;
                }
                update.setString(1, uuidGenerator.generate().toString());
                update.setLong(2, rows.getLong("id"));
                update.addBatch();
            }
            update.executeBatch();
        }
    }

    private void requireUuidV7(String table, long id, String value) throws SQLException {
        try {
            UUID uuid = UUID.fromString(value);
            if (uuid.version() != 7 || uuid.variant() != 2) {
                throw new SQLException(table + " row " + id + " contains a non-RFC-9562 public identifier");
            }
        } catch (IllegalArgumentException exception) {
            throw new SQLException(table + " row " + id + " contains an invalid public identifier", exception);
        }
    }
}
