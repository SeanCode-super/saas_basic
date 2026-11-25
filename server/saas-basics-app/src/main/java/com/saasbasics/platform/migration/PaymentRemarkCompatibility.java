package com.saasbasics.platform.migration;

import java.sql.SQLException;
import java.util.List;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.callback.BaseCallback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.springframework.stereotype.Component;

/** V59 modifies two columns that some V58 schemas, including a fresh install, do not yet have. */
@Component
public final class PaymentRemarkCompatibility extends BaseCallback {
    @Override
    public boolean supports(Event event, Context context) {
        return event == Event.BEFORE_EACH_MIGRATE && context.getMigrationInfo() != null
                && "V59__pay_order_remark.sql".equals(context.getMigrationInfo().getScript());
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return false;
    }

    @Override
    public void handle(Event event, Context context) {
        var connection = context.getConnection();
        try (var columns = connection.prepareStatement("""
                SELECT 1 FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = ? AND column_name = 'remark'
                """)) {
            for (String table : List.of("pay_order", "pay_refund")) {
                columns.setString(1, table);
                boolean missing;
                try (var result = columns.executeQuery()) {
                    missing = !result.next();
                }
                if (missing) {
                    try (var ddl = connection.createStatement()) {
                        ddl.execute("ALTER TABLE " + table + " ADD COLUMN remark VARCHAR(500) NULL");
                    }
                }
            }
        } catch (SQLException e) {
            throw new FlywayException("Failed to prepare the payment columns required by V59", e);
        }
    }
}
